package dev.jecaex.gui;

import dev.jecaex.JecaExMod;
import me.towdium.jecalculation.data.Controller;
import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.data.structure.Recipe;
import me.towdium.jecalculation.data.structure.RecordCraft;
import me.towdium.jecalculation.gui.JecaGui;
import me.towdium.jecalculation.gui.guis.GuiRecipe;
import me.towdium.jecalculation.jei.JecaPlugin;
import mezz.jei.api.recipe.IFocus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-screen crafting-tree view (Bill of Materials style).
 *
 * <p>The target is the root at the top. Every recipe is a horizontal card containing its
 * catalysts (purple, left) and its inputs (right). Craftable inputs are green, raw materials are
 * red; the output of a recipe is shown only implicitly as the parent's input (or the root
 * target). The view can be panned and zoomed, and a total raw-material summary is shown at the
 * bottom.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiCraftTree extends GuiScreen {

    private static final int ICON = 16;
    private static final int STEP = 17;
    private static final int FLUID_STEP = 24;
    private static final int ITEM_STEP = 17;
    private static final int PAD = 2;
    private static final int GAP = 4;
    private static final int V_SPACING = 28;
    private static final int H_SPACING = 6;
    private static final float AMOUNT_SCALE = 2f / 3f;
    private static final int BTN_CATALYSTS = 0;
    private static final int BTN_STACKS = 1;

    private static final int OUTPUT_COLOR = 0x663B82F6; // blue
    private static final int LEAF_COLOR = 0x66C0392B; // red
    private static final int CATALYST_COLOR = 0x66A855C7; // purple
    private static final int INPUT_COLOR = 0x6622C55E; // green
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int LINE_COLOR = 0xFFA0A0A0;

    private final GuiScreen old;
    private final boolean empty;

    private final List<Card> cards = new ArrayList<>();
    private final List<Segment> lines = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> screenHits = new ArrayList<>();

    private ILabel rootTarget;
    private boolean rootLeaf;
    private int rootTargetX, rootTargetY;
    private final List<ILabel> rawMaterials = new ArrayList<>();
    private final Map<ILabel, Object> repCache = new HashMap<>();
    private String summaryTitle;
    private int summaryTitleX, summaryTitleY;

    private double offX, offY;
    private float scale = 1.0f;
    private boolean dragging;
    private int lastMouseX, lastMouseY;

    private boolean showCatalysts = true;
    private boolean showStackAmounts = false;
    private RecipeTree.TreeNode root;
    private GuiButton catalystButton;
    private GuiButton stackButton;

    public GuiCraftTree(GuiScreen old) {
        this.old = old;
        ILabel target = getTarget();
        this.empty = target == ILabel.EMPTY;
        if (!empty) {
            RecipeTree tree = RecipeTree.build(target);
            this.root = tree.root;
            this.rootTarget = tree.root.output;
            this.rootLeaf = tree.root.leaf;
            collectRawMaterials(tree.root);
            rawMaterials.sort(Comparator.comparing(ILabel::getDisplayName));
            rebuildLayout();
        }
    }

    private void rebuildLayout() {
        cards.clear();
        lines.clear();
        hits.clear();
        TreeVolume volume = layout(root, 0);
        if (volume != null) {
            for (Card c : volume.nodes) {
                registerCard(c);
            }
            int center = (volume.minLeft() + volume.maxRight()) / 2;
            for (Card c : cards) {
                c.x -= center;
            }
            rootTargetX -= center;
            for (Segment s : lines) {
                s.x1 -= center;
                s.x2 -= center;
            }
            for (Hit h : hits) {
                h.x -= center;
            }
        } else {
            rootTargetX = 0;
        }
        // Shift cards down one level so the root target sits above them.
        for (Card c : cards) {
            c.y += V_SPACING;
        }
        for (Hit h : hits) {
            h.y += V_SPACING;
        }
        for (Segment s : lines) {
            s.y1 += V_SPACING;
            s.y2 += V_SPACING;
        }
        rootTargetY = 0;
    }

    @Override
    public void initGui() {
        offY = -height / 3.0;
        layoutRawMaterials();
        addTopButtons();
    }

    private void addTopButtons() {
        if (empty) {
            return;
        }
        String stackText = I18n.format(showStackAmounts
                ? "jecaex.gui.tree.show_as_counts"
                : "jecaex.gui.tree.show_as_stacks");
        int stackW = fontRenderer.getStringWidth(stackText) + 12;
        stackButton = new GuiButton(BTN_STACKS, width - stackW - 4, 4, stackW, 20, stackText);
        buttonList.add(stackButton);

        String catalystText = I18n.format(showCatalysts
                ? "jecaex.gui.tree.hide_catalysts"
                : "jecaex.gui.tree.show_catalysts");
        int catalystW = fontRenderer.getStringWidth(catalystText) + 12;
        catalystButton = new GuiButton(BTN_CATALYSTS, width - catalystW - 4, 28, catalystW, 20, catalystText);
        buttonList.add(catalystButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_CATALYSTS) {
            showCatalysts = !showCatalysts;
            rebuildLayout();
            offX = 0;
            offY = -height / 3.0;
            buttonList.clear();
            addTopButtons();
            return;
        }
        if (button.id == BTN_STACKS) {
            showStackAmounts = !showStackAmounts;
            buttonList.clear();
            addTopButtons();
            return;
        }
        super.actionPerformed(button);
    }

    private boolean isOverButton(int mouseX, int mouseY) {
        GuiButton b = catalystButton;
        if (b != null && mouseX >= b.x && mouseX < b.x + b.width
                && mouseY >= b.y && mouseY < b.y + b.height) {
            return true;
        }
        b = stackButton;
        return b != null && mouseX >= b.x && mouseX < b.x + b.width
                && mouseY >= b.y && mouseY < b.y + b.height;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private static class Card {
        final RecipeTree.TreeNode data;
        int x, y;
        Card parent;
        int halfW, totalW;
        int catLeft, catW, inLeft, inW;
        int[] inputX;

        Card(RecipeTree.TreeNode data, int x, int y, boolean showCatalysts) {
            this.data = data;
            this.x = x;
            this.y = y;
            int c = showCatalysts ? data.catalysts.size() : 0;
            int i = data.inputs.size();
            catW = c > 0 ? c * STEP + PAD * 2 : 0;
            inW = i > 0 ? i * STEP + PAD * 2 : 0;
            totalW = catW + (catW > 0 ? GAP : 0) + inW;
            halfW = (totalW + 1) / 2;
            catLeft = -halfW;
            inLeft = catLeft + catW + (catW > 0 ? GAP : 0);
            inputX = new int[i];
            for (int j = 0; j < i; j++) {
                inputX[j] = inLeft + PAD + j * STEP + ICON / 2;
            }
        }
    }

    private static class Segment {
        int x1, y1, x2, y2;

        Segment(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    private static class Hit {
        final ILabel label;
        final Recipe recipe;
        int x, y;

        Hit(ILabel label, int x, int y) {
            this(label, null, x, y);
        }

        Hit(ILabel label, Recipe recipe, int x, int y) {
            this.label = label;
            this.recipe = recipe;
            this.x = x;
            this.y = y;
        }
    }

    private static class TreeVolume {
        final List<Card> nodes = new ArrayList<>();
        final List<int[]> widths = new ArrayList<>();

        TreeVolume(Card head) {
            nodes.add(head);
            widths.add(new int[] { head.x - head.halfW, head.x + head.halfW });
        }

        int getLeft(int d) {
            return widths.get(d)[0];
        }

        int getRight(int d) {
            return widths.get(d)[1];
        }

        int depth() {
            return widths.size();
        }

        void addToRight(TreeVolume other) {
            int rOff = getRight(0) - other.getLeft(0) + H_SPACING;
            for (int i = 1; i < depth() && i < other.depth(); i++) {
                rOff = Math.max(rOff, getRight(i) - other.getLeft(i) + H_SPACING);
            }
            for (int i = 0; i < other.depth(); i++) {
                if (i < depth()) {
                    widths.get(i)[1] = other.getRight(i) + rOff;
                } else {
                    widths.add(new int[] { other.getLeft(i) + rOff, other.getRight(i) + rOff });
                }
            }
            for (Card c : other.nodes) {
                c.x += rOff;
                nodes.add(c);
            }
        }

        void addHead(Card head) {
            head.x = (getLeft(0) + getRight(0)) / 2;
            for (Card c : nodes) {
                if (c.parent == null) {
                    c.parent = head;
                }
            }
            widths.add(0, new int[] { head.x - head.halfW, head.x + head.halfW });
            nodes.add(0, head);
        }

        int minLeft() {
            int m = getLeft(0);
            for (int i = 1; i < depth(); i++) {
                m = Math.min(m, getLeft(i));
            }
            return m;
        }

        int maxRight() {
            int m = getRight(0);
            for (int i = 1; i < depth(); i++) {
                m = Math.max(m, getRight(i));
            }
            return m;
        }
    }

    private TreeVolume layout(RecipeTree.TreeNode node, int depth) {
        if (node.leaf) {
            return null; // raw material: rendered as a red input in its parent's card.
        }
        TreeVolume result = null;
        for (RecipeTree.TreeNode child : node.children) {
            TreeVolume vol = layout(child, depth + 1);
            if (vol == null) {
                continue;
            }
            if (result == null) {
                result = vol;
            } else {
                result.addToRight(vol);
            }
        }
        Card card = new Card(node, 0, depth * V_SPACING, showCatalysts);
        if (result == null) {
            result = new TreeVolume(card);
        } else {
            result.addHead(card);
        }
        if (depth == 0) {
            rootTargetX = card.x;
        }
        return result;
    }

    private void registerCard(Card c) {
        cards.add(c);
        // Catalysts.
        if (c.catW > 0) {
            for (int i = 0; i < c.data.catalysts.size(); i++) {
                hits.add(new Hit(c.data.catalysts.get(i), c.data.recipe, c.x + c.catLeft + PAD + i * STEP,
                        c.y - ICON / 2));
            }
        }
        // Inputs.
        if (c.inW > 0) {
            for (int i = 0; i < c.data.inputs.size(); i++) {
                hits.add(new Hit(c.data.inputs.get(i), c.data.recipe, c.x + c.inLeft + PAD + i * STEP,
                        c.y - ICON / 2));
            }
        }
        // Connector from parent input to this card.
        if (c.parent != null) {
            int idx = c.parent.data.children.indexOf(c.data);
            if (idx >= 0) {
                int px = c.parent.x + c.parent.inputX[idx];
                int py = c.parent.y;
                int cx = c.x;
                int cy = c.y;
                int connectorY = py + V_SPACING / 2;
                lines.add(new Segment(px, py + ICON / 2, px, connectorY));
                lines.add(new Segment(px, connectorY, cx, connectorY));
                lines.add(new Segment(cx, connectorY, cx, cy - 9));
            }
        }
    }

    // ------------------------------------------------------------------
    // Raw material cost
    // ------------------------------------------------------------------

    private void collectRawMaterials(RecipeTree.TreeNode node) {
        if (node.leaf) {
            ILabel out = node.output;
            for (ILabel m : rawMaterials) {
                if (m.matches(out)) {
                    m.setAmount(m.getAmount() + out.getAmount());
                    return;
                }
            }
            rawMaterials.add(out.copy());
        } else {
            for (RecipeTree.TreeNode child : node.children) {
                collectRawMaterials(child);
            }
        }
    }

    private void layoutRawMaterials() {
        screenHits.clear();
        if (rawMaterials.isEmpty()) {
            return;
        }
        List<ILabel> fluids = new ArrayList<>();
        List<ILabel> items = new ArrayList<>();
        for (ILabel m : rawMaterials) {
            if ("fluidStack".equals(m.getIdentifier())) {
                fluids.add(m);
            } else {
                items.add(m);
            }
        }
        summaryTitle = I18n.format("jecaex.gui.tree.total");
        int titleW = fontRenderer.getStringWidth(summaryTitle);
        int totalW = titleW + 8 + fluids.size() * FLUID_STEP + 12 + items.size() * ITEM_STEP;
        int sx = Math.max(4, (width - totalW) / 2);
        summaryTitleX = sx;
        summaryTitleY = height - 80;
        int x = sx + titleW + 8;
        for (ILabel f : fluids) {
            screenHits.add(new Hit(f, x, summaryTitleY));
            x += FLUID_STEP;
        }
        if (!fluids.isEmpty() && !items.isEmpty()) {
            x += 12;
        }
        for (ILabel it : items) {
            screenHits.add(new Hit(it, x, summaryTitleY));
            x += ITEM_STEP;
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (empty) {
            drawCenteredString(fontRenderer, I18n.format("jecaex.gui.tree.empty"), width / 2, height / 2 - 10, 0xFFFFFF);
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(width / 2, height / 2, 0);
        GlStateManager.scale(scale, scale, 1);
        GlStateManager.translate(offX, offY, 0);

        for (Segment s : lines) {
            drawLine(s.x1, s.y1, s.x2, s.y2);
        }

        // Root target.
        if (rootTarget != null) {
            int targetColor = rootLeaf ? LEAF_COLOR : OUTPUT_COLOR;
            drawRect(rootTargetX - 10, rootTargetY - 9, rootTargetX + 10, rootTargetY + 9, targetColor);
            renderLabel(rootTarget, rootTargetX - ICON / 2, rootTargetY - ICON / 2, true);
            // connector from target to the root card (if any).
            if (!cards.isEmpty()) {
                Card rootCard = cards.get(0);
                drawLine(rootTargetX, rootTargetY + 9, rootTargetX, rootCard.y - 9);
            }
        }

        int viewTop = (int) (-height / 2.0 / scale - offY) - 40;
        int viewBottom = (int) (height / 2.0 / scale - offY) + 40;
        for (Card c : cards) {
            if (c.y < viewTop || c.y > viewBottom) {
                continue;
            }
            // Catalysts (purple).
            if (c.catW > 0) {
                for (int i = 0; i < c.data.catalysts.size(); i++) {
                    int cx = c.x + c.catLeft + PAD + i * STEP;
                    drawRect(cx, c.y - 9, cx + ICON, c.y + 9, CATALYST_COLOR);
                    renderLabel(c.data.catalysts.get(i), cx, c.y - ICON / 2, false);
                }
            }
            // Inputs (green craftable / red raw).
            if (c.inW > 0) {
                for (int i = 0; i < c.data.inputs.size(); i++) {
                    int ix = c.x + c.inLeft + PAD + i * STEP;
                    int color = c.data.children.get(i).leaf ? LEAF_COLOR : INPUT_COLOR;
                    drawRect(ix, c.y - 9, ix + ICON, c.y + 9, color);
                    renderLabel(c.data.inputs.get(i), ix, c.y - ICON / 2, true);
                }
            }
            // White border around the whole card.
            int left = c.x - c.halfW;
            int right = left + c.totalW;
            drawRect(left, c.y - 10, right, c.y - 9, BORDER_COLOR);
            drawRect(left, c.y + 9, right, c.y + 10, BORDER_COLOR);
            drawRect(left, c.y - 10, left + 1, c.y + 10, BORDER_COLOR);
            drawRect(right - 1, c.y - 10, right, c.y + 10, BORDER_COLOR);
        }

        GlStateManager.popMatrix();

        // Raw material summary (fixed at the bottom, screen space).
        if (!screenHits.isEmpty()) {
            drawString(fontRenderer, summaryTitle, summaryTitleX, summaryTitleY + 4, 0xFFCCCCCC);
            for (Hit h : screenHits) {
                renderLabel(h.label, h.x, h.y, true);
            }
        }

        // Buttons (e.g. the collapse-catalysts toggle in the top-right corner).
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Tooltip.
        for (Hit h : screenHits) {
            if (mouseX >= h.x && mouseX < h.x + ICON && mouseY >= h.y && mouseY < h.y + ICON) {
                List<String> tip = new ArrayList<>();
                tip.add(h.label.getDisplayName());
                h.label.getToolTip(tip, true);
                drawHoveringText(tip, mouseX, mouseY);
                return;
            }
        }
        int wx = worldX(mouseX);
        int wy = worldY(mouseY);
        for (Hit h : hits) {
            if (wx >= h.x && wx < h.x + ICON && wy >= h.y && wy < h.y + ICON) {
                List<String> tip = new ArrayList<>();
                tip.add(h.label.getDisplayName());
                h.label.getToolTip(tip, true);
                drawHoveringText(tip, mouseX, mouseY);
                break;
            }
        }
    }

    private void renderLabel(ILabel label, int x, int y, boolean showAmount) {
        Object rep = cachedRep(label);
        if (rep instanceof ItemStack) {
            ItemStack stack = (ItemStack) rep;
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemIntoGUI(stack, x, y);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            if (showAmount) {
                String s = formatAmount(label);
                if (!s.isEmpty()) {
                    drawAmount(s, x, y);
                }
            }
        } else if (rep instanceof FluidStack) {
            renderFluid((FluidStack) rep, x, y);
            if (showAmount) {
                String s = formatAmount(label);
                if (!s.isEmpty()) {
                    drawAmount(s, x, y);
                }
            }
        }
    }

    /**
     * Formats a label's amount for display. Items are shown as "stackSize*n+m" (with the actual
     * item stack size) when the stack-mode toggle is on; fluids and other cases keep the default
     * JEC formatting.
     */
    private String formatAmount(ILabel label) {
        if (showStackAmounts && !"fluidStack".equals(label.getIdentifier()) && !label.isPercent()) {
            Object rep = cachedRep(label);
            if (rep instanceof ItemStack) {
                int stackSize = ((ItemStack) rep).getMaxStackSize();
                long amount = label.getAmount();
                if (amount > 0 && stackSize > 1) {
                    long n = amount / stackSize;
                    long m = amount % stackSize;
                    if (n == 0) {
                        return Long.toString(m);
                    } else if (m == 0) {
                        return n == 1 ? Long.toString(amount) : stackSize + "*" + n;
                    } else {
                        return stackSize + "*" + n + "+" + m;
                    }
                }
            }
        }
        return label.getAmountString(true);
    }

    private void drawAmount(String s, int x, int y) {
        // Stacked from the bottom-right corner of the 16x16 icon. Wraps into multiple lines
        // when too wide, and shrinks the scale if even the wrapped block is too tall, so the
        // number always stays inside the icon without ever truncating digits.
        List<String> lines = wrapAmount(s);
        float lineH = fontRenderer.FONT_HEIGHT;
        float totalH = (lines.size() - 1) * lineH + 8;
        float scl = Math.min(AMOUNT_SCALE, (ICON - 2) / totalH);
        GlStateManager.pushMatrix();
        GlStateManager.scale(scl, scl, 1.0f);
        int wx = Math.round((x + ICON - 1) / scl);
        int wy = Math.round((y + ICON - 1) / scl - 8);
        for (int i = 0; i < lines.size(); i++) {
            int lw = fontRenderer.getStringWidth(lines.get(i));
            fontRenderer.drawStringWithShadow(lines.get(i), wx - lw,
                    wy - Math.round((lines.size() - 1 - i) * lineH), 0xFFFFFF);
        }
        GlStateManager.popMatrix();
    }

    private List<String> wrapAmount(String s) {
        List<String> lines = new ArrayList<>();
        int maxW = Math.round((ICON - 2) / AMOUNT_SCALE);
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int w = fontRenderer.getCharWidth(c);
            if (cur.length() > 0 && fontRenderer.getStringWidth(cur.toString()) + w > maxW) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }

    private Object cachedRep(ILabel label) {
        Object rep = repCache.get(label);
        if (rep == null && !repCache.containsKey(label)) {
            rep = label.getRepresentation();
            repCache.put(label, rep);
        }
        return rep;
    }

    private void renderFluid(FluidStack fluid, int x, int y) {
        TextureAtlasSprite tex = mc.getTextureMapBlocks().getTextureExtry(fluid.getFluid().getStill().toString());
        if (tex == null) {
            return;
        }
        mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        int c = fluid.getFluid().getColor();
        GlStateManager.color((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 1f);
        drawTexturedModalRect(x, y, tex, ICON, ICON);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawLine(int x1, int y1, int x2, int y2) {
        if (x1 == x2) {
            drawRect(x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2), LINE_COLOR);
        } else {
            drawRect(Math.min(x1, x2), y1, Math.max(x1, x2), y1 + 1, LINE_COLOR);
        }
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    private int worldX(int screenX) {
        return (int) ((screenX - width / 2) / scale - offX);
    }

    private int worldY(int screenY) {
        return (int) ((screenY - height / 2) / scale - offY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && !isOverButton(mouseX, mouseY)) {
            boolean shift = isShiftKeyDown();
            for (Hit h : screenHits) {
                if (mouseX >= h.x && mouseX < h.x + ICON && mouseY >= h.y && mouseY < h.y + ICON) {
                    showRecipe(h.label);
                    return;
                }
            }
            int wx = worldX(mouseX);
            int wy = worldY(mouseY);
            for (Hit h : hits) {
                if (wx >= h.x && wx < h.x + ICON && wy >= h.y && wy < h.y + ICON) {
                    if (shift && h.recipe != null) {
                        openRecipeEditor(h.recipe);
                    } else {
                        showRecipe(h.label);
                    }
                    return;
                }
            }
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void openRecipeEditor(Recipe recipe) {
        try {
            GuiRecipe gui = new GuiRecipe();
            Method fromRecipe = GuiRecipe.class.getDeclaredMethod("fromRecipe", Recipe.class);
            fromRecipe.setAccessible(true);
            fromRecipe.invoke(gui, recipe);
            Method refresh = GuiRecipe.class.getDeclaredMethod("refresh");
            refresh.setAccessible(true);
            refresh.invoke(gui);
            JecaGui.displayGui(true, true, gui);
        } catch (ReflectiveOperationException | RuntimeException e) {
            JecaExMod.LOGGER.error("Failed to open the JEC recipe editor", e);
        }
    }

    private void showRecipe(ILabel label) {
        Object rep = cachedRep(label);
        if (rep != null) {
            JecaPlugin.runtime.getRecipesGui()
                    .show(JecaPlugin.runtime.getRecipeRegistry().createFocus(IFocus.Mode.OUTPUT, rep));
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging) {
            offX += (mouseX - lastMouseX) / scale;
            offY += (mouseY - lastMouseY) / scale;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scale = Math.max(0.4f, Math.min(3.0f, scale * (wheel > 0 ? 1.1f : 0.9f)));
            return;
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(old);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------------
    // Target
    // ------------------------------------------------------------------

    private static ILabel getTarget() {
        RecordCraft record = Controller.getRCraft();
        ILabel latest = record.getLatest();
        if (latest == ILabel.EMPTY) {
            return ILabel.EMPTY;
        }
        long amount = 1;
        try {
            String s = record.amount;
            if (s != null && !s.isEmpty()) {
                amount = Long.parseLong(s);
            }
        } catch (NumberFormatException ignored) {
            amount = 1;
        }
        return latest.copy().setAmount(amount);
    }
}

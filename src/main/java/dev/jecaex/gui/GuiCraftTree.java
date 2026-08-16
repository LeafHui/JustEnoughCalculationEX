package dev.jecaex.gui;

import dev.architectury.fluid.FluidStack;
import dev.architectury.hooks.fluid.FluidStackHooks;
import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.data.structure.CostList;
import me.towdium.jecalculation.data.structure.Recipe;
import me.towdium.jecalculation.compat.ModCompat;
import me.towdium.jecalculation.gui.JecaGui;
import me.towdium.jecalculation.gui.guis.GuiRecipe;
import me.towdium.jecalculation.utils.wrappers.Trio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Full-screen crafting-tree view (Bill of Materials style) for JEC 1.20.1.
 *
 * <p>The target is the root at the top. Every recipe is a horizontal card containing its
 * catalysts (purple, left) and its inputs (right). Craftable inputs are green, raw materials are
 * red; the output of a recipe is shown only implicitly as the parent's input (or the root
 * target). The view can be panned and zoomed, and a total raw-material summary is shown at the
 * bottom.</p>
 */
public class GuiCraftTree extends Screen {

    private static final int ICON = 16;
    private static final int STEP = 17;
    private static final int FLUID_STEP = 24;
    private static final int ITEM_STEP = 17;
    private static final int PAD = 2;
    private static final int GAP = 4;
    private static final int V_SPACING = 28;
    private static final int H_SPACING = 6;
    private static final float AMOUNT_SCALE = 2f / 3f;

    private static final int OUTPUT_COLOR = 0x663B82F6; // blue
    private static final int LEAF_COLOR = 0x66C0392B; // red
    private static final int CATALYST_COLOR = 0x66A855C7; // purple
    private static final int INPUT_COLOR = 0x6622C55E; // green
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int LINE_COLOR = 0xFFA0A0A0;

    private final JecaGui parent;
    private final boolean empty;

    private final List<Card> cards = new ArrayList<>();
    private final List<Segment> lines = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> screenHits = new ArrayList<>();

    private ILabel rootTarget;
    private boolean rootLeaf;
    private int rootTargetX;
    private int rootTargetY;
    private final List<ILabel> rawMaterials = new ArrayList<>();
    private final Map<ILabel, Object> repCache = new HashMap<>();
    private String summaryTitle;
    private int summaryTitleX;
    private int summaryTitleY;

    private double offX;
    private double offY;
    private float scale = 1.0f;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    private boolean showCatalysts = true;
    private Button catalystButton;
    private RecipeTree.TreeNode root;

    public GuiCraftTree(JecaGui parent, ILabel target) {
        super(Component.translatable("jecalculation.gui.craft.tree"));
        this.parent = parent;
        this.empty = target == null || target == ILabel.EMPTY;
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

    @Override
    protected void init() {
        clearWidgets();
        offY = -height / 3.0;
        layoutRawMaterials();
        addCatalystButton();
    }

    private void addCatalystButton() {
        if (empty) {
            return;
        }
        String key = showCatalysts
                ? "jecaex.gui.tree.hide_catalysts"
                : "jecaex.gui.tree.show_catalysts";
        Component text = Component.translatable(key);
        int w = font.width(text) + 12;
        catalystButton = Button.builder(text, b -> toggleCatalysts())
                .bounds(width - w - 4, 4, w, 20)
                .build();
        addRenderableWidget(catalystButton);
    }

    private void toggleCatalysts() {
        showCatalysts = !showCatalysts;
        rebuildLayout();
        offX = 0;
        offY = -height / 3.0;
        clearWidgets();
        addCatalystButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        if (empty) {
            graphics.drawCenteredString(font, Component.translatable("jecaex.gui.tree.empty"),
                    width / 2, height / 2 - 10, 0xFFFFFF);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2.0, height / 2.0, 0);
        pose.scale(scale, scale, 1);
        pose.translate(offX, offY, 0);

        for (Segment s : lines) {
            drawLine(graphics, s.x1, s.y1, s.x2, s.y2);
        }

        if (rootTarget != null) {
            int targetColor = rootLeaf ? LEAF_COLOR : OUTPUT_COLOR;
            graphics.fill(rootTargetX - 10, rootTargetY - 9, rootTargetX + 10, rootTargetY + 9, targetColor);
            renderLabel(graphics, rootTarget, rootTargetX - ICON / 2, rootTargetY - ICON / 2, true);
            if (!cards.isEmpty()) {
                Card rootCard = cards.get(0);
                drawLine(graphics, rootTargetX, rootTargetY + 9, rootTargetX, rootCard.y - 9);
            }
        }

        int viewTop = (int) (-height / 2.0 / scale - offY) - 40;
        int viewBottom = (int) (height / 2.0 / scale - offY) + 40;
        for (Card c : cards) {
            if (c.y < viewTop || c.y > viewBottom) {
                continue;
            }
            if (c.catW > 0) {
                for (int i = 0; i < c.data.catalysts.size(); i++) {
                    int cx = c.x + c.catLeft + PAD + i * STEP;
                    graphics.fill(cx, c.y - 9, cx + ICON, c.y + 9, CATALYST_COLOR);
                    renderLabel(graphics, c.data.catalysts.get(i), cx, c.y - ICON / 2, false);
                }
            }
            if (c.inW > 0) {
                for (int i = 0; i < c.data.inputs.size(); i++) {
                    int ix = c.x + c.inLeft + PAD + i * STEP;
                    int color = c.data.children.get(i).leaf ? LEAF_COLOR : INPUT_COLOR;
                    graphics.fill(ix, c.y - 9, ix + ICON, c.y + 9, color);
                    renderLabel(graphics, c.data.inputs.get(i), ix, c.y - ICON / 2, true);
                }
            }

            int left = c.x - c.halfW;
            int right = left + c.totalW;
            graphics.fill(left, c.y - 10, right, c.y - 9, BORDER_COLOR);
            graphics.fill(left, c.y + 9, right, c.y + 10, BORDER_COLOR);
            graphics.fill(left, c.y - 10, left + 1, c.y + 10, BORDER_COLOR);
            graphics.fill(right - 1, c.y - 10, right, c.y + 10, BORDER_COLOR);
        }
        pose.popPose();

        // Raw material summary (screen space).
        if (!screenHits.isEmpty()) {
            graphics.drawString(font, summaryTitle, summaryTitleX, summaryTitleY + 4, 0xFFCCCCCC);
            for (Hit h : screenHits) {
                renderLabel(graphics, h.label, h.x, h.y, true);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // Tooltips.
        for (Hit h : screenHits) {
            if (mouseX >= h.x && mouseX < h.x + ICON && mouseY >= h.y && mouseY < h.y + ICON) {
                renderHitTooltip(graphics, h, mouseX, mouseY);
                return;
            }
 
        }
        int wx = worldX(mouseX);
        int wy = worldY(mouseY);
        for (Hit h : hits) {
            if (wx >= h.x && wx < h.x + ICON && wy >= h.y && wy < h.y + ICON) {
                renderHitTooltip(graphics, h, mouseX, mouseY);
                break;
            }
        }
    }

    private void renderHitTooltip(GuiGraphics graphics, Hit h, int mouseX, int mouseY) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(h.label.getDisplayName()));
        List<String> raw = new ArrayList<>();
        h.label.getToolTip(raw, true);
        raw.forEach(s -> tip.add(Component.literal(s)));
        graphics.renderTooltip(font, tip, Optional.empty(), mouseX, mouseY);
    }

    private void renderLabel(GuiGraphics graphics, ILabel label, int x, int y, boolean showAmount) {
        Object rep = cachedRep(label);
        if (rep instanceof ItemStack stack) {
            RenderSystem.enableDepthTest();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 100);
            graphics.renderItem(stack, x, y);
            graphics.pose().popPose();
            RenderSystem.disableDepthTest();
            if (showAmount) {
                String s = label.getAmountString(true);
                if (!s.isEmpty()) {
                    drawAmount(graphics, s, x, y);
                }
            }
        } else if (rep instanceof FluidStack fluid) {
            renderFluid(graphics, fluid, x, y);
            if (showAmount) {
                String s = label.getAmountString(true);
                if (!s.isEmpty()) {
                    drawAmount(graphics, s, x, y);
                }
            }
        }
    }

    private void drawAmount(GuiGraphics graphics, String s, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().scale(AMOUNT_SCALE, AMOUNT_SCALE, 1);
        int tw = font.width(s);
        int wx = Math.round((x + ICON - 2) / AMOUNT_SCALE - tw);
        int wy = Math.round((y + ICON - 3) / AMOUNT_SCALE - 8);
        graphics.drawString(font, s, wx, wy, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    private Object cachedRep(ILabel label) {
        Object rep = repCache.get(label);
        if (rep == null && !repCache.containsKey(label)) {
            rep = label.getRepresentation();
            repCache.put(label, rep);
        }
        return rep;
    }

    private void renderFluid(GuiGraphics graphics, FluidStack fluid, int x, int y) {
        TextureAtlasSprite tex = FluidStackHooks.getStillTexture(fluid.getFluid());
        if (tex == null) {
            return;
        }
        int c = FluidStackHooks.getColor(fluid.getFluid());
        float r = (c >> 16 & 255) / 255f;
        float g = (c >> 8 & 255) / 255f;
        float b = (c & 255) / 255f;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        graphics.setColor(r, g, b, 1f);
        graphics.blit(x, y, 0, ICON, ICON, tex);
        graphics.setColor(1f, 1f, 1f, 1f);
        graphics.pose().popPose();
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        if (x1 == x2) {
            graphics.fill(x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2), LINE_COLOR);
        } else {
            graphics.fill(Math.min(x1, x2), y1, Math.max(x1, x2), y1 + 1, LINE_COLOR);
        }
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    private int worldX(double screenX) {
        return (int) ((screenX - width / 2.0) / scale - offX);
    }

    private int worldY(double screenY) {
        return (int) ((screenY - height / 2.0) / scale - offY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && (catalystButton == null || !catalystButton.isMouseOver(mouseX, mouseY))) {
            boolean shift = hasShiftDown();
            for (Hit h : screenHits) {
                if (mouseX >= h.x && mouseX < h.x + ICON && mouseY >= h.y && mouseY < h.y + ICON) {
                    showRecipe(h.label);
                    return true;
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
                    return true;
                }
            }
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            offX += (mouseX - lastMouseX) / scale;
            offY += (mouseY - lastMouseY) / scale;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            scale = Math.max(0.4f, Math.min(3.0f, (float) (scale * (delta > 0 ? 1.1 : 0.9))));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (parent != null) {
                Minecraft.getInstance().setScreen(parent);
            } else {
                onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------
    // JEC integration
    // ------------------------------------------------------------------

    private void openRecipeEditor(Recipe recipe) {
        GuiRecipe gui = new GuiRecipe();
        gui.transfer(convertRecipe(recipe), GuiRecipe.class);
        JecaGui.displayGui(gui, parent);
    }

    private EnumMap<Recipe.IO, List<Trio<ILabel, CostList, CostList>>> convertRecipe(Recipe recipe) {
        EnumMap<Recipe.IO, List<Trio<ILabel, CostList, CostList>>> ret = new EnumMap<>(Recipe.IO.class);
        putLabels(ret, Recipe.IO.INPUT, recipe.getLabel(Recipe.IO.INPUT));
        putLabels(ret, Recipe.IO.CATALYST, recipe.getLabel(Recipe.IO.CATALYST));
        putLabels(ret, Recipe.IO.OUTPUT, recipe.getLabel(Recipe.IO.OUTPUT));
        return ret;
    }

    private void putLabels(EnumMap<Recipe.IO, List<Trio<ILabel, CostList, CostList>>> map,
                           Recipe.IO io, List<ILabel> labels) {
        List<Trio<ILabel, CostList, CostList>> list = new ArrayList<>();
        for (ILabel label : labels) {
            if (label == ILabel.EMPTY) {
                continue;
            }
            ILabel copy = label.copy();
            CostList cost = new CostList(List.of(copy));
            list.add(new Trio<>(copy, cost, cost));
        }
        if (!list.isEmpty()) {
            map.put(io, list);
        }
    }

    private void showRecipe(ILabel label) {
        ModCompat.showRecipe(label);
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

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

    private static class Card {
        final RecipeTree.TreeNode data;
        int x;
        int y;
        Card parent;
        int halfW;
        int totalW;
        int catLeft;
        int catW;
        int inLeft;
        int inW;
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
        int x1;
        int y1;
        int x2;
        int y2;

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
        int x;
        int y;

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
            return null;
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
        if (c.catW > 0) {
            for (int i = 0; i < c.data.catalysts.size(); i++) {
                hits.add(new Hit(c.data.catalysts.get(i), c.data.recipe,
                        c.x + c.catLeft + PAD + i * STEP, c.y - ICON / 2));
            }
        }
        if (c.inW > 0) {
            for (int i = 0; i < c.data.inputs.size(); i++) {
                hits.add(new Hit(c.data.inputs.get(i), c.data.recipe,
                        c.x + c.inLeft + PAD + i * STEP, c.y - ICON / 2));
            }
        }
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
        summaryTitle = Component.translatable("jecaex.gui.tree.total").getString();
        int titleW = font.width(summaryTitle);
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
}

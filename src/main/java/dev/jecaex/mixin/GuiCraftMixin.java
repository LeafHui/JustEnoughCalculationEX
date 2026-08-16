package dev.jecaex.mixin;

import dev.jecaex.gui.GuiCraftTree;
import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.data.structure.RecordCraft;
import me.towdium.jecalculation.gui.JecaGui;
import me.towdium.jecalculation.gui.Resource;
import me.towdium.jecalculation.gui.guis.GuiCraft;
import me.towdium.jecalculation.gui.widgets.WButton;
import me.towdium.jecalculation.gui.widgets.WButtonIcon;
import me.towdium.jecalculation.gui.widgets.WLabel;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Crafting Tree button to JEC's crafting calculator.
 */
@Mixin(value = GuiCraft.class, remap = false)
public abstract class GuiCraftMixin {

    @Shadow
    private WLabel label;

    @Shadow
    private RecordCraft record;

    @Inject(method = "<init>(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("TAIL"), remap = false)
    private void jecaex$addCraftingTreeButton(CallbackInfo ci) {
        GuiCraft self = (GuiCraft) (Object) this;
        Resource.ResourceGroup treeIcon = new Resource.ResourceGroup(
                Resource.ICN_STACK_N, Resource.ICN_STACK_F);
        WButton tree = new WButtonIcon(83, 62, 20, 20, treeIcon,
                "jecalculation.gui.craft.tree");
        tree.setListener(i -> {
            ILabel target = label.getLabel();
            if (target == ILabel.EMPTY) {
                return;
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
            JecaGui parent = JecaGui.getCurrent();
            Minecraft.getInstance().setScreen(new GuiCraftTree(parent, target.copy().setAmount(amount)));
        });
        self.add(tree);
    }
}

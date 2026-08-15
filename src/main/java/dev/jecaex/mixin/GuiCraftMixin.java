package dev.jecaex.mixin;

import dev.jecaex.gui.GuiCraftTree;
import me.towdium.jecalculation.gui.Resource;
import me.towdium.jecalculation.gui.guis.GuiCraft;
import me.towdium.jecalculation.gui.widgets.WButtonIcon;
import me.towdium.jecalculation.gui.widgets.WContainer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "crafting tree" button to the crafting calculator, next to the "steps" button.
 */
@Mixin(value = GuiCraft.class, remap = false)
public abstract class GuiCraftMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void jecaex$addTreeButton(CallbackInfo ci) {
        Resource.ResourceGroup treeIcon = new Resource.ResourceGroup(Resource.ICN_STACK_N, Resource.ICN_STACK_F);
        ((WContainer) (Object) this).add(new WButtonIcon(83, 62, 20, 20, treeIcon, "craft.tree")
                .setListener(i -> Minecraft.getMinecraft()
                        .displayGuiScreen(new GuiCraftTree(Minecraft.getMinecraft().currentScreen))));
    }
}

package dev.jecaex.mixin;

import dev.jecaex.gregtech.GtRecipeTransferHelper;
import me.towdium.jecalculation.gui.guis.GuiRecipe;
import mezz.jei.api.gui.IRecipeLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Patches {@link GuiRecipe#transfer(IRecipeLayout)} for GregTech machine recipes.
 *
 * <p>All actual work is delegated to {@link GtRecipeTransferHelper} which uses reflection to avoid
 * hard class dependencies. If the transfer is not a GregTech machine recipe (or anything goes
 * wrong), the original JEC transfer logic runs untouched.</p>
 */
@Mixin(value = GuiRecipe.class, remap = false)
public abstract class GuiRecipeMixin {

    @Inject(method = "transfer", at = @At("HEAD"), cancellable = true)
    private void jecaex$transfer(IRecipeLayout recipe, CallbackInfo ci) {
        if (GtRecipeTransferHelper.transfer(this, recipe)) {
            ci.cancel();
        }
    }
}

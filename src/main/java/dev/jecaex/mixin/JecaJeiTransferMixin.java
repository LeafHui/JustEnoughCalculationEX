package dev.jecaex.mixin;

import dev.jecaex.JecaExMod;
import me.towdium.jecalculation.compat.ModCompat;
import me.towdium.jecalculation.compat.jei.JecaJEIPlugin;
import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.data.structure.CostList;
import me.towdium.jecalculation.data.structure.Recipe;
import me.towdium.jecalculation.utils.wrappers.Trio;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * Restores JEC's 1.12-era behavior of adding JEI recipe catalysts (workstations/machines)
 * to the catalyst group when a recipe is transferred into the JEC recipe editor.
 */
@Mixin(value = JecaJEIPlugin.JEITransferHandler.class, remap = false)
public abstract class JecaJeiTransferMixin {

    @Inject(
            method = "convertRecipe",
            at = @At("RETURN"),
            remap = false
    )
    private void jecaex$addRecipeCatalysts(
            IRecipeSlotsView recipeSlots,
            Class<?> context,
            CallbackInfoReturnable<EnumMap<Recipe.IO, List<Trio<ILabel, CostList, CostList>>>> cir) {
        addJeiCatalysts(cir.getReturnValue(), context);
    }

    private void addJeiCatalysts(EnumMap<Recipe.IO, List<Trio<ILabel, CostList, CostList>>> merged,
                                 Class<?> context) {
        try {
            if (JecaJEIPlugin.runtime == null) {
                return;
            }
            IRecipeCategory<?> category = JecaJEIPlugin.runtime.getRecipeManager()
                    .createRecipeCategoryLookup().get()
                    .filter(c -> c.getClass() == context)
                    .findFirst()
                    .orElse(null);
            if (category == null) {
                return;
            }
            JecaJEIPlugin.runtime.getRecipeManager()
                    .createRecipeCatalystLookup(category.getRecipeType()).get()
                    .map(ITypedIngredient::getIngredient)
                    .filter(Objects::nonNull)
                    .forEach(ingredient -> ModCompat.merge(merged, List.of(ingredient),
                            context, Recipe.IO.CATALYST));
        } catch (Throwable t) {
            JecaExMod.LOGGER.warn("Failed to add JEI recipe catalysts to JEC transfer", t);
        }
    }
}

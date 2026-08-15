package dev.jecaex.gregtech;

import dev.jecaex.JecaExMod;
import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.data.structure.CostList;
import me.towdium.jecalculation.gui.widgets.WLabelGroup;
import me.towdium.jecalculation.jei.JecaPlugin;
import me.towdium.jecalculation.utils.wrappers.Trio;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enhanced recipe-transfer logic for Just Enough Calculation.
 *
 * <p>All third-party access (GregTech, HEI internals) is done reflectively so the addon does not
 * hard-depend on any particular version of those mods, and GregTech is strictly optional.</p>
 *
 * <p>Improvements over JEC's default transfer:</p>
 * <ol>
 *   <li>The workstation/machine catalyst is shown as the first real stack (no fuzzy-meta collapse
 *       that zeroes the metadata and renders as "Unnamed"). All candidates stay in the
 *       disambiguation list.</li>
 *   <li>For GregTech machine recipes the machine with the minimum required voltage tier is shown
 *       (e.g. an MV recipe shows the MV machine instead of the LV one).</li>
 *   <li>Non-consumable item inputs are routed to the catalyst slot: GregTech circuits/molds/lenses
 *       via {@code GTRecipeWrapper#isNotConsumedItem}, and damageable tools in crafting-table
 *       recipes via the generic {@code ItemStack#isItemStackDamageable} check.</li>
 * </ol>
 *
 * <p>If the recipe is not recognised (or anything fails), {@code false} is returned and JEC's
 * original transfer logic runs unchanged.</p>
 */
public final class GtRecipeTransferHelper {

    private GtRecipeTransferHelper() {
    }

    private static final String RECIPE_LAYOUT_CLASS = "mezz.jei.gui.recipes.RecipeLayout";
    private static final String RECIPE_WRAPPER_FIELD = "recipeWrapper";

    private static final String GT_WRAPPER_CLASS = "gregtech.integration.jei.recipe.GTRecipeWrapper";
    private static final String GT_NOT_CONSUMED_METHOD = "isNotConsumedItem";
    private static final String GT_CIRCUIT_CLASS = "gregtech.api.recipes.ingredients.IntCircuitIngredient";
    private static final String GT_CIRCUIT_METHOD = "isIntegratedCircuit";
    private static final String GT_UTILITY_CLASS = "gregtech.api.util.GTUtility";
    private static final String GT_TIER_BY_VOLTAGE_METHOD = "getTierByVoltage";
    private static final String GT_GET_EUT_METHOD = "getEUt";
    private static final String GT_GET_MTE_METHOD = "getMetaTileEntity";
    private static final String GT_TIERED_CLASS = "gregtech.api.metatileentity.ITieredMetaTileEntity";
    private static final String GT_MTE_GET_TIER_METHOD = "getTier";

    private static final String CRAFTING_CATEGORY_UID = "minecraft.crafting";

    private static Field recipeWrapperField;
    private static Method isNotConsumedItem;
    private static Method isIntegratedCircuit;
    private static Method getTierByVoltage;
    private static Method getEUt;
    private static Method getMetaTileEntity;
    private static Class<?> tieredMteClass;
    private static Method mteGetTier;

    /**
     * Runs the enhanced transfer into {@code gui} (a JEC {@code GuiRecipe}).
     *
     * @return {@code true} if the transfer was handled (caller should cancel JEC's original
     *         transfer); {@code false} to fall back to JEC's original logic.
     */
    public static boolean transfer(Object gui, IRecipeLayout recipe) {
        Object wrapper = getRecipeWrapper(recipe);
        boolean isGtMachine = wrapper != null && GT_WRAPPER_CLASS.equals(wrapper.getClass().getName());
        boolean isCrafting = CRAFTING_CATEGORY_UID.equals(recipe.getRecipeCategory().getUid());

        try {
            WLabelGroup catalyst = getField(gui, "catalyst");
            WLabelGroup input = getField(gui, "input");
            WLabelGroup output = getField(gui, "output");
            HashMap<Integer, List<ILabel>> disambCache = getField(gui, "disambCache");

            ArrayList<Trio<ILabel, CostList, CostList>> inputList = new ArrayList<>();
            ArrayList<Trio<ILabel, CostList, CostList>> catalystItems = new ArrayList<>();
            ArrayList<Trio<ILabel, CostList, CostList>> outputList = new ArrayList<>();
            disambCache.clear();

            // 1) Fluids first (JEC's original ordering): inputs/outputs only.
            for (IGuiIngredient<?> gi : recipe.getFluidStacks().getGuiIngredients().values()) {
                merge(gi.isInput() ? inputList : outputList, gi, recipe);
            }

            // 2) Item ingredients, routed to input / catalyst / output.
            for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry
                    : recipe.getItemStacks().getGuiIngredients().entrySet()) {
                IGuiIngredient<?> gi = entry.getValue();
                boolean isInput = gi.isInput();
                boolean isCatalyst = false;
                if (isInput) {
                    if (isGtMachine) {
                        isCatalyst = isNotConsumedItem(wrapper, entry.getKey()) || isIntegratedCircuit(gi);
                    } else if (isCrafting) {
                        isCatalyst = isDamageableTool(gi);
                    }
                }
                if (isCatalyst) {
                    merge(catalystItems, gi, recipe);
                } else {
                    merge(isInput ? inputList : outputList, gi, recipe);
                }
            }

            // 3) Workstation/machine catalysts. For GregTech pick the minimum required voltage tier
            //    (steam machines are skipped, so the lowest electric tier is LV).
            List<Object> rawCatalysts = JecaPlugin.runtime.getRecipeRegistry()
                    .getRecipeCatalysts(recipe.getRecipeCategory());
            List<ILabel> machines = rawCatalysts.stream()
                    .map(ILabel.Converter::from)
                    .filter(i -> i != ILabel.EMPTY)
                    .collect(Collectors.toList());
            int catalystStart = 0;
            if (!machines.isEmpty()) {
                ILabel shown = machines.get(0);
                if (isGtMachine && machines.size() > 1) {
                    int index = selectMachineIndex(rawCatalysts, wrapper);
                    if (index >= 0 && index < machines.size()) {
                        shown = machines.get(index);
                    }
                }
                catalyst.setLabel(shown, 0);
                if (machines.size() > 1) {
                    disambCache.put(14, machines);
                }
                catalystStart = 1;
            }

            // 4) Non-consumable item catalysts after the machine (disamb key = 14 + slot).
            List<ILabel> catItemLabels = sort(catalystItems, 14 + catalystStart, disambCache);
            for (int i = 0; i < catItemLabels.size() && catalystStart + i < 7; i++) {
                catalyst.setLabel(catItemLabels.get(i), catalystStart + i);
            }

            // 5) Consumed inputs and outputs.
            input.setLabel(sort(inputList, 0, disambCache), 0);
            output.setLabel(sort(outputList, 21, disambCache), 0);

            Method refresh = gui.getClass().getDeclaredMethod("refresh");
            refresh.setAccessible(true);
            refresh.invoke(gui);

            return true;
        } catch (Throwable t) {
            JecaExMod.LOGGER.error("Enhanced recipe transfer failed, falling back to default transfer", t);
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Detection / queries (reflection, no hard compile dependency)
    // ---------------------------------------------------------------------

    private static Object getRecipeWrapper(IRecipeLayout layout) {
        try {
            Field field = getRecipeWrapperField(layout);
            return field == null ? null : field.get(layout);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Field getRecipeWrapperField(IRecipeLayout layout) throws ReflectiveOperationException {
        if (recipeWrapperField == null) {
            Class<?> current = layout.getClass();
            while (current != null && !RECIPE_LAYOUT_CLASS.equals(current.getName())) {
                current = current.getSuperclass();
            }
            Class<?> target = current != null ? current : layout.getClass();
            Field field = target.getDeclaredField(RECIPE_WRAPPER_FIELD);
            field.setAccessible(true);
            recipeWrapperField = field;
        }
        return recipeWrapperField;
    }

    private static boolean isNotConsumedItem(Object wrapper, int slot) {
        try {
            if (isNotConsumedItem == null) {
                isNotConsumedItem = wrapper.getClass().getMethod(GT_NOT_CONSUMED_METHOD, int.class);
            }
            return (Boolean) isNotConsumedItem.invoke(wrapper, slot);
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private static boolean isIntegratedCircuit(IGuiIngredient<?> gi) {
        try {
            for (Object ingredient : gi.getAllIngredients()) {
                if (!(ingredient instanceof ItemStack)) {
                    return false;
                }
                if (isIntegratedCircuit == null) {
                    Class<?> circuitClass = Class.forName(GT_CIRCUIT_CLASS);
                    isIntegratedCircuit = circuitClass.getMethod(GT_CIRCUIT_METHOD, ItemStack.class);
                }
                if (!(Boolean) isIntegratedCircuit.invoke(null, ingredient)) {
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    /** Generic "tool" detection for crafting recipes: damageable items are not consumed. */
    private static boolean isDamageableTool(IGuiIngredient<?> gi) {
        for (Object ingredient : gi.getAllIngredients()) {
            if (!(ingredient instanceof ItemStack) || !((ItemStack) ingredient).isItemStackDamageable()) {
                return false;
            }
        }
        return true;
    }

    /** Voltage tier of a GregTech recipe (1 = LV, 2 = MV, 3 = HV, ...). ULV recipes map to LV. */
    private static int getVoltageTier(Object wrapper) {
        try {
            Object recipe = wrapper.getClass().getMethod("getRecipe").invoke(wrapper);
            if (getEUt == null) {
                getEUt = recipe.getClass().getMethod(GT_GET_EUT_METHOD);
            }
            int eut = (Integer) getEUt.invoke(recipe);
            if (getTierByVoltage == null) {
                Class<?> gtUtility = Class.forName(GT_UTILITY_CLASS);
                getTierByVoltage = gtUtility.getMethod(GT_TIER_BY_VOLTAGE_METHOD, long.class);
            }
            // getTierByVoltage returns 0 = ULV, 1 = LV, 2 = MV, ...; there is no ULV electric
            // machine tier to show, so treat ULV recipes as LV.
            byte tier = (Byte) getTierByVoltage.invoke(null, Math.abs((long) eut));
            return Math.max(1, tier);
        } catch (ReflectiveOperationException | LinkageError e) {
            return 1;
        }
    }

    /** Tier of a machine catalyst stack (1 = LV, 2 = MV, ...), or -1 if it has no tier (steam). */
    private static int getMachineTier(Object rawCatalyst) {
        if (!(rawCatalyst instanceof ItemStack)) {
            return -1;
        }
        try {
            if (getMetaTileEntity == null) {
                Class<?> gtUtility = Class.forName(GT_UTILITY_CLASS);
                getMetaTileEntity = gtUtility.getMethod(GT_GET_MTE_METHOD, ItemStack.class);
            }
            Object mte = getMetaTileEntity.invoke(null, rawCatalyst);
            if (mte == null) {
                return -1;
            }
            if (tieredMteClass == null) {
                tieredMteClass = Class.forName(GT_TIERED_CLASS);
            }
            if (!tieredMteClass.isInstance(mte)) {
                return -1;
            }
            if (mteGetTier == null) {
                mteGetTier = tieredMteClass.getMethod(GT_MTE_GET_TIER_METHOD);
            }
            return (Integer) mteGetTier.invoke(mte);
        } catch (ReflectiveOperationException | LinkageError e) {
            return -1;
        }
    }

    /** Picks the catalyst index whose electric tier matches the recipe's minimum tier. */
    private static int selectMachineIndex(List<Object> rawCatalysts, Object wrapper) {
        int desiredTier = getVoltageTier(wrapper);
        int bestIndex = -1;
        int bestHigherTier = Integer.MAX_VALUE;
        int highestTier = 0;
        int highestIndex = -1;
        for (int i = 0; i < rawCatalysts.size(); i++) {
            int tier = getMachineTier(rawCatalysts.get(i));
            if (tier < 1) {
                continue; // steam machine or non-tiered workstation
            }
            if (tier > highestTier) {
                highestTier = tier;
                highestIndex = i;
            }
            if (tier == desiredTier) {
                return i;
            }
            if (tier > desiredTier && tier < bestHigherTier) {
                bestHigherTier = tier;
                bestIndex = i;
            }
        }
        if (bestIndex >= 0) {
            return bestIndex; // lowest tier above the desired one
        }
        return highestIndex >= 0 ? highestIndex : 0; // clamp to the highest available tier
    }

    // ---------------------------------------------------------------------
    // JEC merge/sort logic, reimplemented against JEC's public API.
    // ---------------------------------------------------------------------

    private static void merge(ArrayList<Trio<ILabel, CostList, CostList>> dst, IGuiIngredient<?> gi,
                              IRecipeLayout context) {
        List<ILabel> list = gi.getAllIngredients().stream()
                .map(ILabel.Converter::from)
                .filter(i -> i != ILabel.EMPTY)
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return;
        }
        dst.stream().filter(p -> {
            CostList cl = new CostList(list);
            if (p.three.equals(cl)) {
                ILabel.MERGER.merge(p.one, ILabel.CONVERTER.first(list, context)).ifPresent(i -> p.one = i);
                p.two = p.two.merge(cl, true, false);
                return true;
            }
            return false;
        }).findAny().orElseGet(() -> {
            Trio<ILabel, CostList, CostList> ret = new Trio<>(
                    ILabel.CONVERTER.first(list, context), new CostList(list), new CostList(list));
            dst.add(ret);
            return ret;
        });
    }

    private static ArrayList<ILabel> sort(ArrayList<Trio<ILabel, CostList, CostList>> src, int offset,
                                          HashMap<Integer, List<ILabel>> disambCache) {
        ArrayList<ILabel> ret = new ArrayList<>();
        for (int i = 0; i < src.size(); i++) {
            Trio<ILabel, CostList, CostList> p = src.get(i);
            ret.add(p.one);
            if (p.two.getLabels().size() > 1) {
                disambCache.put(i + offset, p.two.getLabels());
            }
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}

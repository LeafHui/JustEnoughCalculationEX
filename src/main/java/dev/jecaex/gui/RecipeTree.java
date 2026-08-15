package dev.jecaex.gui;

import me.towdium.jecalculation.data.Controller;
import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.data.structure.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a recipe dependency tree for visualisation.
 *
 * <p>Each {@link TreeNode} represents one recipe step: a single tracked output, its catalysts and
 * its (scaled) inputs. Inputs that can themselves be crafted are expanded recursively into child
 * nodes until a raw material (no matching recipe) is reached.</p>
 */
public final class RecipeTree {

    public static final int MAX_DEPTH = 8;
    public static final int MAX_NODES = 200;

    public final TreeNode root;

    private RecipeTree(TreeNode root) {
        this.root = root;
    }

    public static RecipeTree build(ILabel target) {
        Builder builder = new Builder();
        return new RecipeTree(builder.build(target, 0, new ArrayList<>()));
    }

    public static final class TreeNode {
        public final ILabel output;
        public final List<ILabel> catalysts;
        public final List<ILabel> inputs;
        public final List<TreeNode> children;
        public final int depth;
        public final boolean leaf;
        public final Recipe recipe;

        TreeNode(ILabel output, List<ILabel> catalysts, List<ILabel> inputs, int depth, Recipe recipe) {
            this.output = output;
            this.catalysts = catalysts;
            this.inputs = inputs;
            this.depth = depth;
            this.recipe = recipe;
            this.children = new ArrayList<>();
            this.leaf = catalysts.isEmpty() && inputs.isEmpty();
        }
    }

    private static final class Builder {
        int nodes;

        TreeNode build(ILabel target, int depth, List<ILabel> ancestors) {
            // Cycle detection: if the target already appears higher in the chain, stop.
            for (ILabel ancestor : ancestors) {
                if (target.matches(ancestor)) {
                    return leaf(target, depth);
                }
            }
            if (depth >= MAX_DEPTH || nodes >= MAX_NODES) {
                return leaf(target, depth);
            }

            // JEC matches recipes against "required" labels, i.e. labels with a negative amount
            // (a positive amount never matches an ore-dictionary output because LOreDict#mergeFuzzy
            // requires opposite signs). Negate only for matching/amount computation; the tree keeps
            // the positive amount for display.
            ILabel request = target.copy().multiply(-1);
            Recipe recipe = findRecipe(request);
            if (recipe == null) {
                return leaf(target, depth);
            }

            nodes++;
            long multiplier = recipe.multiplier(request);

            List<ILabel> catalysts = nonEmpty(recipe.getLabel(Recipe.IO.CATALYST));
            List<ILabel> inputs = new ArrayList<>();
            for (ILabel in : recipe.getLabel(Recipe.IO.INPUT)) {
                if (in != ILabel.EMPTY) {
                    inputs.add(in.copy().multiply((float) multiplier));
                }
            }

            TreeNode node = new TreeNode(target, catalysts, inputs, depth, recipe);

            List<ILabel> nextAncestors = new ArrayList<>(ancestors);
            nextAncestors.add(target);
            for (ILabel input : inputs) {
                node.children.add(build(input, depth + 1, nextAncestors));
            }
            return node;
        }

        TreeNode leaf(ILabel output, int depth) {
            return new TreeNode(output, new ArrayList<>(), new ArrayList<>(), depth, null);
        }

        static Recipe findRecipe(ILabel target) {
            return Controller.recipeIterator().stream()
                    .filter(r -> r.matches(target).isPresent())
                    .findFirst()
                    .orElse(null);
        }

        static List<ILabel> nonEmpty(ILabel[] labels) {
            List<ILabel> ret = new ArrayList<>();
            for (ILabel l : labels) {
                if (l != ILabel.EMPTY) {
                    ret.add(l.copy());
                }
            }
            return ret;
        }
    }
}

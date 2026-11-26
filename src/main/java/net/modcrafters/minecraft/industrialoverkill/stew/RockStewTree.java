package net.modcrafters.minecraft.industrialoverkill.stew;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.lib.machine.recipe.RecipeGraph;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;

/** A depth-first view of the graph's canonical routes; parents precede all their descendants. */
public final class RockStewTree {
    public static final int ROW_HEIGHT = 24;
    private RockStewTree() {}

    public record FluidUse(long retained, long consumed) {
        public FluidUse {
            if (retained < 0 || consumed < 0 || retained > Integer.MAX_VALUE || consumed > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Invalid source fluid amount");
        }
        public long total() { return Math.max(RockStewRecipes.SOURCE_FLUID_MINIMUM, consumed); }
    }
    public record Step(ItemStack item, String operation, long lavaConsumed) {
        public Step {
            if (lavaConsumed < 0 || lavaConsumed > Integer.MAX_VALUE || item.isEmpty() || !List.of("generate", "grind", "smelt", "craft").contains(operation))
                throw new IllegalArgumentException("Invalid recipe display step");
        }
    }
    public record Row(List<Step> steps, FluidUse water, FluidUse lava) {
        public Row {
            steps = List.copyOf(steps);
            if (steps.isEmpty() || steps.size() > RockStewProcessor.SLOTS) throw new IllegalArgumentException("Invalid display route");
        }
        public ItemStack destination() { return steps.getLast().item(); }
    }

    public static Row row(MachineRecipe source, RecipeGraph graph, int node) {
        var steps = new ArrayList<Step>();
        steps.add(new Step(graph.item(0), "generate", fluids(source, "lava").consumed()));
        graph.pathTo(node).forEach(operation -> steps.add(new Step(operation.output(), operation.kind().getPath(), fluids(operation.recipe(), "lava").consumed())));
        return new Row(steps, fluids(source, "water"), fluids(source, "lava"));
    }

    private static FluidUse fluids(MachineRecipe recipe, String group) {
        long retained = 0, consumed = 0;
        for (var part : recipe.inputs().getOrDefault(group, List.of())) {
            if (part instanceof MachineIngredient.FluidPart fluid) {
                if (fluid.consumed()) consumed += fluid.amount(); else retained += fluid.amount();
            }
        }
        return new FluidUse(retained, consumed);
    }

    public static List<Integer> order(RecipeGraph graph) {
        var children = new ArrayList<List<Integer>>();
        for (int i = 0; i < graph.size(); i++) children.add(new ArrayList<>());
        for (int i = 1; i < graph.size(); i++) {
            var path = graph.pathTo(i);
            int parent = path.size() == 1 ? 0 : graph.indexOf(path.get(path.size() - 2).output());
            children.get(parent).add(i);
        }
        var order = new ArrayList<Integer>();
        visit(0, children, order);
        return List.copyOf(order);
    }

    private static void visit(int node, List<List<Integer>> children, List<Integer> order) {
        order.add(node);
        for (int child : children.get(node)) visit(child, children, order);
    }

}

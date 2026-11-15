package net.modcrafters.minecraft.industrialoverkill.stew;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.lib.machine.neoforge.MachineRecipeTypes;
import zone.moddev.mc.lib.machine.neoforge.RecipeOperations;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;
import zone.moddev.mc.lib.machine.recipe.RecipeGraph;

/** Mod-owned admission and balance; catalogs are rebuilt only on reload/selection. */
public final class RockStewRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, "industrial_overkill");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "industrial_overkill");
    public static final MachineRecipeType SOURCES = MachineRecipeTypes.register(TYPES, SERIALIZERS, "rock_stew");
    public static final MachineRecipeType GRINDING = MachineRecipeTypes.register(TYPES, SERIALIZERS, "rock_stew_grinding");
    public static final ResourceLocation DEFAULT = id("rock_stew/cobblestone");
    public static final int MAX_STEPS = 5; // Generation occupies the first processor column.
    public static final int SMELT_LAVA = 100;
    public static final int SOURCE_FLUID_MINIMUM = 1000;
    private static long epoch;

    private RockStewRecipes() {}
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("industrial_overkill", path); }
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) invalidate();
        });
        RockStewScreen.register();
    }
    public static void invalidate() { epoch++; }
    public static long epoch() { return epoch; }

    public static List<RecipeHolder<MachineRecipeEntry>> sources(Level level) {
        return SOURCES.recipes(level.getRecipeManager()).stream()
            .filter(h -> validSource(h.value().recipe()))
            .sorted(Comparator.comparing(RecipeHolder::id)).toList();
    }

    private static boolean validSource(zone.moddev.mc.lib.machine.recipe.MachineRecipe recipe) {
        if (recipe.time() < 1 || recipe.outputs().size() != 1
            || !recipe.inputs().keySet().containsAll(List.of("water", "lava", "energy"))) return false;
        var output = recipe.outputs().getOrDefault("output", List.of());
        if (output.size() != 1 || !(output.getFirst() instanceof zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct item)
            || item.chance() != 1 || !RockStewConfig.allowed(item.stack())
            || item.stack().getCount() > item.stack().getMaxStackSize()) return false;
        return recipe.inputs().entrySet().stream().allMatch(entry -> switch (entry.getKey()) {
            case "water", "lava" -> !entry.getValue().isEmpty() && entry.getValue().stream().allMatch(p ->
                p instanceof zone.moddev.mc.lib.machine.ingredient.MachineIngredient.FluidPart fluid && fluid.amount() > 0
                    && fluid.fluids().contains((entry.getKey().equals("water")
                        ? net.minecraft.world.level.material.Fluids.WATER : net.minecraft.world.level.material.Fluids.LAVA).builtInRegistryHolder()));
            case "energy" -> !entry.getValue().isEmpty() && entry.getValue().stream().allMatch(p -> p instanceof zone.moddev.mc.lib.machine.ingredient.MachineIngredient.EnergyPart && p.amount() > 0);
            default -> false;
        });
    }

    public static RockStewProcessor.Route route(Level level, ResourceLocation source, ItemStack destination) {
        var root = sources(level).stream().filter(h -> h.id().equals(source)).findFirst().orElseThrow();
        var graph = graph(level, root);
        int node = graph.indexOf(destination);
        if (node < 0) throw new IllegalArgumentException("Destination is not reachable from this source");
        return route(root, graph, node);
    }

    public static RockStewProcessor.Route route(RecipeHolder<MachineRecipeEntry> root, RecipeGraph graph, int node) {
        var bindings = new ArrayList<RockStewProcessor.Binding>();
        bindings.add(new RockStewProcessor.Binding(root.id(), "generate", root.value().recipe()));
        graph.pathTo(node).forEach(op -> bindings.add(new RockStewProcessor.Binding(op.id(), op.kind().getPath(), op.recipe())));
        if (bindings.size() > MAX_STEPS || bindings.stream().anyMatch(binding -> !RockStewConfig.allowed(binding.product())))
            throw new IllegalArgumentException("Rock Stew path exceeds five steps or contains a product outside the allowed item tags");
        return new RockStewProcessor.Route(bindings);
    }

    private static RecipeGraph.Operation fuelSmelting(RecipeGraph.Operation operation) {
        var recipe = operation.recipe();
        var inputs = new java.util.LinkedHashMap<>(recipe.inputs());
        var lava = net.minecraft.core.HolderSet.direct(net.minecraft.world.level.material.Fluids.LAVA.builtInRegistryHolder());
        inputs.put("lava", List.of(
            new zone.moddev.mc.lib.machine.ingredient.MachineIngredient.FluidPart(lava, SMELT_LAVA)));
        var fueled = new zone.moddev.mc.lib.machine.recipe.MachineRecipe(inputs, recipe.outputs(), recipe.time());
        return new RecipeGraph.Operation(operation.id(), operation.kind(), operation.inputCount(), operation.output(), fueled);
    }

    public static RecipeGraph graph(Level level, RecipeHolder<MachineRecipeEntry> root) {
        if (!validSource(root.value().recipe())) throw new IllegalArgumentException("Invalid Rock Stew source");
        // One stable catalog per discovery, never rebuilt inside a stage tick.
        var recipes = level.getRecipeManager().getRecipes().stream().sorted(Comparator.comparing(RecipeHolder::id)).toList();
        return RecipeGraph.discover(root.value().getResultItem(level.registryAccess()), source -> {
            var operations = new ArrayList<RecipeGraph.Operation>();
            for (var holder : recipes) {
                if (holder.value() instanceof MachineRecipeEntry entry && entry.type() == GRINDING)
                    RecipeOperations.machine(holder.id(), id("grind"), entry.recipe(), source).ifPresent(operations::add);
                else {
                    RecipeOperations.smelting(holder, source, level, new RecipeOperations.Cost(100, 800)).map(RockStewRecipes::fuelSmelting).ifPresent(operations::add);
                    RecipeOperations.crafting(holder, source, level, new RecipeOperations.Cost(20, 160)).ifPresent(operations::add);
                }
            }
            // Apply the strict allowlist before expansion, including intermediate products.
            return operations.stream().filter(operation -> RockStewConfig.allowed(operation.output())).toList();
        }, new RecipeGraph.Limits(512, 2048, MAX_STEPS - 1));
    }
}

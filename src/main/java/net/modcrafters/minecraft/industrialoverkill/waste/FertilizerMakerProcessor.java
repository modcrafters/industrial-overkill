package net.modcrafters.minecraft.industrialoverkill.waste;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.core.HolderSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import zone.moddev.mc.lib.machine.feature.Processors;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineTx;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;

public final class FertilizerMakerProcessor extends FarmBatchProcessor {
    private enum Recipe {
        SEWAGE(FarmSupplies.SEWAGE, FarmSupplies.FERTILIZER, 4, 200, 50),
        FILTERED(FarmSupplies.FILTERED_SEWAGE, FarmSupplies.FILTERED_FERTILIZER, 2, 200, 50),
        WATER(FarmSupplies.WATER, FarmSupplies.FERTILIZER, 2, 800, 0),
        PELLETS(null, FarmSupplies.SLOW_RELEASE_PELLET, 2, 400, 0);

        final ResourceLocation fluid;
        final Supplier<? extends Item> output;
        final int yield;
        final int ticks;
        final int water;

        Recipe(ResourceLocation fluid, Supplier<? extends Item> output, int yield, int ticks, int water) {
            this.fluid = fluid;
            this.output = output;
            this.yield = yield;
            this.ticks = ticks;
            this.water = water;
        }
    }

    private static final Recipe[] RECIPES = Recipe.values();
    private static final int LIQUID = 100;
    private static final int LIQUID_SEEDS = 4;
    private static final int PELLET_SEEDS = 8;
    // Keep the original tank IDs so saved raw sewage and water stay in the machine.
    private static final String INPUT_TANK = "sewage";
    private static final String WATER_OUTPUT = "water";

    public FertilizerMakerProcessor() { super(RECIPES.length, "maker_liquid", "maker_pellets"); }

    @Override
    protected int dataVersion() { return 1; }

    @Override
    protected CompoundTag migrateState(int version, CompoundTag data) {
        data.putInt("mode", data.getInt("mode") == 2 ? 1 : 0);
        return data;
    }

    public static int progress(BlockEntity host) {
        return Processors.find(host, "maker", FertilizerMakerProcessor.class)
            .map(FertilizerMakerProcessor::batchProgress).orElse(0);
    }

    public static int progressMax(BlockEntity host) {
        return Processors.find(host, "maker", FertilizerMakerProcessor.class)
            .map(FertilizerMakerProcessor::batchDuration).orElse(0);
    }

    public record RecipePreview(ResourceLocation id, MachineRecipe recipe) {}

    /** All the playable ingredient combinations, built from the processing recipes. */
    public static List<RecipePreview> recipePreviews() {
        var previews = new ArrayList<RecipePreview>();
        for (var recipe : RECIPES) {
            if (recipe != Recipe.PELLETS) previews.add(preview(recipe, false));
            previews.add(preview(recipe, true));
        }
        return List.copyOf(previews);
    }

    private static RecipePreview preview(Recipe recipe, boolean cake) {
        var inputs = new LinkedHashMap<String, List<MachineIngredient>>();
        if (recipe.fluid != null) {
            var fluid = FarmSupplies.fluid(recipe.fluid, LIQUID);
            inputs.put(INPUT_TANK, List.of(new MachineIngredient.FluidPart(HolderSet.direct(fluid.fluid()), fluid.amount())));
        }
        if (!cake || recipe == Recipe.PELLETS)
            inputs.put("seeds", List.of(new MachineIngredient.ItemPart(Ingredient.of(FarmSupplies.COMPOSTABLE_SEEDS),
                recipe == Recipe.PELLETS ? PELLET_SEEDS : LIQUID_SEEDS)));
        if (cake)
            inputs.put("cake", List.of(new MachineIngredient.ItemPart(Ingredient.of(FarmSupplies.FILTER_CAKE.get()), 1)));
        inputs.put("energy", List.of(new MachineIngredient.EnergyPart((long) ENERGY_PER_TICK * recipe.ticks)));

        var outputs = new LinkedHashMap<String, List<MachineProduct>>();
        outputs.put("output", List.of(new MachineProduct.ItemProduct(new ItemStack(recipe.output.get(), recipe.yield), 1)));
        if (recipe.water > 0)
            outputs.put(WATER_OUTPUT, List.of(new MachineProduct.FluidProduct(FarmSupplies.fluid(FarmSupplies.WATER, recipe.water), 1)));
        String name = recipe.name().toLowerCase(Locale.ROOT) + (cake && recipe != Recipe.PELLETS ? "_cake" : "");
        return new RecipePreview(FarmSupplies.id("fertilizer_maker/" + name), new MachineRecipe(inputs, outputs, recipe.ticks));
    }

    @Override
    protected int chooseBatch(DefinedMachineEntity host, int mode) {
        if (mode == 1) return hasInputs(host, Recipe.PELLETS.ordinal()) ? Recipe.PELLETS.ordinal() : -1;
        for (var recipe : RECIPES)
            if (recipe.fluid != null && hasInputs(host, recipe.ordinal())) return recipe.ordinal();
        return -1;
    }

    @Override
    protected int duration(int recipe) { return RECIPES[recipe].ticks; }

    @Override
    protected boolean hasInputs(DefinedMachineEntity host, int index) {
        var recipe = RECIPES[index];
        var resources = host.resources();
        if (recipe.fluid != null) {
            var input = resources.fluids(INPUT_TANK);
            if (input.free(0) < LIQUID || !input.peek(0).fluid().is(recipe.fluid)) return false;
        }
        int count = countSeeds(resources.items("seeds"));
        boolean cake = resources.items("cake").free(0) > 0;
        return recipe == Recipe.PELLETS ? cake && count >= PELLET_SEEDS : count >= LIQUID_SEEDS || cake;
    }

    @Override
    protected boolean hasOutputSpace(DefinedMachineEntity host, int index) {
        var recipe = RECIPES[index];
        var resources = host.resources();
        return resources.items("output").fits(List.of(new ItemStack(recipe.output.get(), recipe.yield)))
            && (recipe.water == 0 || resources.fluids(WATER_OUTPUT)
                .fits(List.of(FarmSupplies.fluid(FarmSupplies.WATER, recipe.water))));
    }

    private static int countSeeds(ItemGroup seeds) {
        int count = 0;
        for (int slot = 0; slot < seeds.size(); slot++)
            if (seeds.peek(slot).is(FarmSupplies.COMPOSTABLE_SEEDS)) count += seeds.free(slot);
        return count;
    }

    private static boolean takeSeeds(ItemGroup seeds, int count, MachineTx transaction) {
        for (int slot = 0; slot < seeds.size() && count > 0; slot++)
            if (seeds.peek(slot).is(FarmSupplies.COMPOSTABLE_SEEDS)) count -= seeds.extract(slot, count, transaction);
        return count == 0;
    }

    @Override
    protected boolean finishBatch(DefinedMachineEntity host, int index) {
        var recipe = RECIPES[index];
        var resources = host.resources();
        var seeds = resources.items("seeds");
        var cake = resources.items("cake");
        try (var transaction = resources.begin()) {
            if (recipe == Recipe.PELLETS) {
                if (!takeSeeds(seeds, PELLET_SEEDS, transaction) || cake.extract(0, 1, transaction) != 1) return false;
            } else {
                if (countSeeds(seeds) >= LIQUID_SEEDS) {
                    if (!takeSeeds(seeds, LIQUID_SEEDS, transaction)) return false;
                } else if (cake.extract(0, 1, transaction) != 1) return false;
                if (resources.fluids(INPUT_TANK).extract(0, LIQUID, transaction) != LIQUID) return false;
            }
            if (resources.items("output").insert(new ItemStack(recipe.output.get(), recipe.yield), transaction)
                != recipe.yield) return false;
            if (recipe.water > 0 && resources.fluids(WATER_OUTPUT)
                .insert(FarmSupplies.fluid(FarmSupplies.WATER, recipe.water), transaction) != recipe.water) return false;
            transaction.commit();
            return true;
        }
    }
}

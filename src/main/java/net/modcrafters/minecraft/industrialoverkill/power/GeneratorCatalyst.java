package net.modcrafters.minecraft.industrialoverkill.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.inventory.FluidGroup;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeInput;

/** One dose of fluid modifies one solid-fuel burn. Recipes synchronize through the vanilla recipe manager. */
public record GeneratorCatalyst(Dose input, Optional<FluidAmount> outputFluid, float outputFluidChance,
    Optional<ItemStack> outputItem, float outputItemChance, double energyMultiplier, double burnRateMultiplier,
    float ashChance) implements Recipe<MachineRecipeInput> {
    static final int CAPACITY = 4000;
    static final float BASE_ASH_CHANCE = 0.125F;
    private static final Codec<Float> CHANCE = Codec.floatRange(0, 1);
    private static final Codec<Double> MULTIPLIER = Codec.DOUBLE.validate(value -> Double.isFinite(value) && value > 0
        ? DataResult.success(value) : DataResult.error(() -> "A generator multiplier must be finite and positive"));

    public record Dose(Holder<Fluid> fluid, int amount) {
        static final Codec<Dose> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("id").forGetter(Dose::fluid),
            Codec.intRange(1, CAPACITY).fieldOf("amount").forGetter(Dose::amount)
        ).apply(i, Dose::new));
    }

    public static final MapCodec<GeneratorCatalyst> CODEC = RecordCodecBuilder.<GeneratorCatalyst>mapCodec(i -> i.group(
        Dose.CODEC.fieldOf("input").forGetter(GeneratorCatalyst::input),
        FluidAmount.CODEC.optionalFieldOf("output_fluid").forGetter(GeneratorCatalyst::outputFluid),
        CHANCE.optionalFieldOf("output_fluid_chance", 1F).forGetter(GeneratorCatalyst::outputFluidChance),
        ItemStack.CODEC.optionalFieldOf("output_item").forGetter(GeneratorCatalyst::outputItem),
        CHANCE.optionalFieldOf("output_item_chance", 1F).forGetter(GeneratorCatalyst::outputItemChance),
        MULTIPLIER.optionalFieldOf("energy_multiplier", 1D).forGetter(GeneratorCatalyst::energyMultiplier),
        MULTIPLIER.optionalFieldOf("burn_rate_multiplier", 1D).forGetter(GeneratorCatalyst::burnRateMultiplier),
        CHANCE.optionalFieldOf("ash_chance", BASE_ASH_CHANCE).forGetter(GeneratorCatalyst::ashChance)
    ).apply(i, GeneratorCatalyst::new)).validate(rule -> {
        if (rule.input.fluid.value() == Fluids.EMPTY || rule.outputFluid.filter(f -> f.isEmpty() || f.amount() > CAPACITY).isPresent())
            return DataResult.error(() -> "Generator fluids must be nonempty and fit in the tank");
        if ((rule.outputFluid.isEmpty() && rule.outputFluidChance != 1) || (rule.outputItem.isEmpty() && rule.outputItemChance != 1))
            return DataResult.error(() -> "An output chance requires the corresponding output");
        return DataResult.success(rule);
    });

    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, IndustrialOverkill.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, IndustrialOverkill.MOD_ID);
    private static final DeferredHolder<RecipeType<?>, RecipeType<GeneratorCatalyst>> TYPE = TYPES.register("generator_catalyst", () -> new RecipeType<GeneratorCatalyst>() {});
    private static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GeneratorCatalyst>> SERIALIZER = SERIALIZERS.register("generator_catalyst", () -> new RecipeSerializer<GeneratorCatalyst>() {
        private final StreamCodec<RegistryFriendlyByteBuf, GeneratorCatalyst> stream = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        @Override public MapCodec<GeneratorCatalyst> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, GeneratorCatalyst> streamCodec() { return stream; }
    });

    static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }

    private record Index(Collection<RecipeHolder<?>> source, Map<Fluid, RecipeHolder<GeneratorCatalyst>> rules) {}
    private static final Map<RecipeManager, Index> INDEXES = new WeakHashMap<>();

    public static synchronized Map<Fluid, RecipeHolder<GeneratorCatalyst>> rules(Level level) {
        if (level == null) return Map.of();
        var manager = level.getRecipeManager();
        var source = manager.getRecipes();
        var index = INDEXES.get(manager);
        if (index == null || index.source != source) {
            var rules = new LinkedHashMap<Fluid, RecipeHolder<GeneratorCatalyst>>();
            manager.getAllRecipesFor(TYPE.get()).stream().sorted(Comparator.comparing(r -> r.id().toString()))
                .forEach(r -> rules.putIfAbsent(r.value().input.fluid.value(), r));
            index = new Index(source, Collections.unmodifiableMap(rules));
            INDEXES.put(manager, index);
        }
        return index.rules;
    }

    @Override public boolean matches(MachineRecipeInput inventory, Level level) {
        if (!(inventory.inventory().group("catalyst").orElse(null) instanceof FluidGroup tank) || tank.tanks() == 0) return false;
        var fluid = tank.peek(0);
        return fluid.fluid().equals(input.fluid) && fluid.amount() >= input.amount;
    }
    @Override public ItemStack assemble(MachineRecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeType<?> getType() { return TYPE.get(); }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER.get(); }
}

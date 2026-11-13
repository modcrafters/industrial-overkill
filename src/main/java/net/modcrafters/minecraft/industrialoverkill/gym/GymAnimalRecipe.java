package net.modcrafters.minecraft.industrialoverkill.gym;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;

/** Reloadable animal/food rules, synchronized by vanilla's recipe manager. */
public record GymAnimalRecipe(Selector entity, Ingredient food, boolean enabled,
    int energyPerTick, int foodDurationTicks)
    implements
        Recipe<SingleRecipeInput> {
    public static final int DEFAULT_ENERGY = 8, DEFAULT_DURATION = 200;
    // A full intensity-3 tick must fit the gym's 32,000 FE buffer.
    public static final int MAX_ENERGY = 32000 / 3;
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister
        .create(Registries.RECIPE_TYPE, IndustrialOverkill.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister
        .create(Registries.RECIPE_SERIALIZER, IndustrialOverkill.MOD_ID);
    public static final DeferredHolder<RecipeType<?>, RecipeType<GymAnimalRecipe>> TYPE = TYPES
        .register("gym_animal", () -> new RecipeType<>() {
            @Override
            public String toString() {
                return "industrial_overkill:gym_animal";
            }
        });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GymAnimalRecipe>> SERIALIZER = SERIALIZERS
        .register("gym_animal", Serializer::new);
    // RecipeManager replaces its immutable collection on reload/client synchronization.
    // Cache only the relevant recipes, without scanning all crafting recipes on every gym tick.
    private static final Map<RecipeManager, Index> INDEXES = new WeakHashMap<>();
    private record Index(Collection<RecipeHolder<?>> source, List<GymAnimalRecipe> rules) {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }

    public static synchronized List<GymAnimalRecipe> rules(Level level) {
        if (level == null)
            return List.of();
        var manager = level.getRecipeManager();
        var source = manager.getRecipes();
        var index = INDEXES.get(manager);
        if (index == null || index.source() != source) {
            index = new Index(source, manager.getAllRecipesFor(TYPE.get()).stream()
                .sorted(java.util.Comparator.comparing(holder -> holder.id().toString()))
                .map(RecipeHolder::value).filter(GymAnimalRecipe::enabled).toList());
            INDEXES.put(manager, index);
        }
        return index.rules();
    }

    public static boolean eligible(net.minecraft.world.entity.animal.Animal animal) {
        return animal.isAlive() && !animal.isBaby() && !animal.isPassenger() && !animal.isVehicle()
            && GymAnimalRecipe.rules(animal.level()).stream()
                .anyMatch(rule -> rule.entity().matches(animal.getType()));
    }
    public static boolean food(net.minecraft.world.level.Level level, ItemStack stack) {
        return !stack.isEmpty()
            && GymAnimalRecipe.rules(level).stream().anyMatch(rule -> rule.food().test(stack));
    }
    public static boolean foodFor(net.minecraft.world.entity.animal.Animal animal,
        ItemStack stack) {
        return !stack.isEmpty() && GymAnimalRecipe.rules(animal.level()).stream()
            .anyMatch(rule -> rule.entity().matches(animal.getType()) && rule.food().test(stack));
    }

    public static GymAnimalRecipe find(net.minecraft.world.entity.animal.Animal animal,
        ItemStack food) {
        if (food.isEmpty())
            return null;
        return rules(animal.level()).stream()
            .filter(rule -> rule.entity.matches(animal.getType()) && rule.food.test(food))
            .findFirst().orElse(null);
    }

    public record Selector(ResourceLocation id, boolean tag) {
        public static final Codec<Selector> CODEC = Codec.STRING.comapFlatMap(value -> {
            boolean tag = value.startsWith("#");
            return ResourceLocation.read(tag ? value.substring(1) : value)
                .flatMap(id -> tag || BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                    ? DataResult.success(new Selector(id, tag))
                    : DataResult.error(() -> "Unknown gym entity: " + id));
        }, value -> (value.tag ? "#" : "") + value.id);

        public boolean matches(EntityType<?> type) {
            return tag
                ? type.is(TagKey.create(Registries.ENTITY_TYPE, id))
                : BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(id);
        }
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return enabled && food.test(input.item());
    }
    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }
    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }
    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }
    @Override
    public RecipeType<?> getType() {
        return TYPE.get();
    }
    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, food);
    }
    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<GymAnimalRecipe> {
        public static final MapCodec<GymAnimalRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i
            .group(
                Selector.CODEC.fieldOf("entity").forGetter(GymAnimalRecipe::entity),
                Ingredient.CODEC_NONEMPTY.fieldOf("food").forGetter(GymAnimalRecipe::food),
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(GymAnimalRecipe::enabled),
                Codec.intRange(1, MAX_ENERGY).optionalFieldOf("energy_per_tick", DEFAULT_ENERGY)
                    .forGetter(GymAnimalRecipe::energyPerTick),
                Codec.intRange(1, Integer.MAX_VALUE)
                    .optionalFieldOf("food_duration_ticks", DEFAULT_DURATION)
                    .forGetter(GymAnimalRecipe::foodDurationTicks))
            .apply(i, GymAnimalRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, GymAnimalRecipe> STREAM = ByteBufCodecs
            .fromCodecWithRegistries(CODEC.codec());
        @Override
        public MapCodec<GymAnimalRecipe> codec() {
            return CODEC;
        }
        @Override
        public StreamCodec<RegistryFriendlyByteBuf, GymAnimalRecipe> streamCodec() {
            return STREAM;
        }
    }
}

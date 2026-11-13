package zone.moddev.mc.industrialoverkill.test;

import static zone.moddev.mc.industrialoverkill.test.AnimalGymTests.require;

import java.util.ArrayList;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.industrialoverkill.gym.GymAnimalRecipe;
import zone.moddev.mc.industrialoverkill.trap.MobTrapData;

import io.netty.buffer.Unpooled;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GymAnimalRecipeTests {
    private static GymAnimalRecipe parse(GameTestHelper h, String json) {
        return GymAnimalRecipe.Serializer.CODEC.codec().parse(
            h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
            JsonParser.parseString(json)).getOrThrow();
    }

    @GameTest(template = "gym_empty")
    public static void paidRatesScaleWithIntensityAndSurviveReload(GameTestHelper h) {
        var level = h.getLevel();
        var manager = level.getRecipeManager();
        var original = new ArrayList<>(manager.getRecipes());
        var id = ResourceLocation.parse("industrial_overkill:test_rates");
        try {
            manager.replaceRecipes(java.util.List.of(new RecipeHolder<>(id, parse(h,
                """
                    {"entity":"minecraft:panda","food":{"item":"minecraft:bamboo"},"energy_per_tick":12,"food_duration_ticks":600}
                    """))));
            var gym = AnimalGymTests.place(h, Direction.NORTH);
            gym.food.insertItem(3,
                MobTrapData.stack(MobTrapData.save(EntityType.PANDA.create(level))), false);
            gym.food.insertItem(0, new ItemStack(Items.BAMBOO, 3), false);
            gym.control("intensity");
            gym.control("intensity");
            gym.energy.setStored(31999);
            gym.serverTick();
            require(gym.getGeneration() == 0 && gym.food.getStackInSlot(0).getCount() == 3
                && gym.feeding.serving().isEmpty(), "Full buffer does not charge a meal");
            gym.energy.setStored(0);
            gym.serverTick();
            require(gym.getGeneration() == 36 && gym.energy.stored() == 36,
                "Intensity triples configured output");
            require(
                gym.captive.animal().getPersistentData()
                    .getInt("industrial_overkill:gym_ration") == 597,
                "Intensity consumes three of the configured 600 work units");
            gym.control("toggle");
            var saved = gym.saveWithFullMetadata(level.registryAccess());
            gym.loadWithComponents(saved, level.registryAccess());
            manager.replaceRecipes(java.util.List.of(new RecipeHolder<>(id, parse(h,
                """
                    {"entity":"minecraft:panda","food":{"item":"minecraft:bamboo"},"energy_per_tick":20,"food_duration_ticks":900}
                    """))));
            gym.control("toggle");
            for (int i = 1; i < 200; i++)
                gym.serverTick();
            require(gym.energy.stored() == 7200 && gym.getGeneration() == 36,
                "Saved meal keeps 600 units at 12 FE despite recipe reload");
            require(
                gym.captive.animal().getPersistentData()
                    .getInt("industrial_overkill:gym_ration") == 0,
                "Long allowance is not clamped to legacy 200");
            gym.serverTick();
            require(gym.getGeneration() == 60
                && gym.captive.animal().getPersistentData()
                    .getInt("industrial_overkill:gym_ration") == 897,
                "Next meal takes reloaded power and duration");
        } finally {
            manager.replaceRecipes(original);
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void overlappingRulesAreStableAndShortMealsNeverOverpay(GameTestHelper h) {
        var level = h.getLevel();
        var manager = level.getRecipeManager();
        var original = new ArrayList<>(manager.getRecipes());
        try {
            var shortMeal = parse(h,
                """
                    {"entity":"minecraft:panda","food":{"item":"minecraft:bamboo"},"energy_per_tick":7,"food_duration_ticks":1}
                    """);
            var other = parse(h,
                """
                    {"entity":"minecraft:panda","food":{"item":"minecraft:bamboo"},"energy_per_tick":99,"food_duration_ticks":200}
                    """);
            manager.replaceRecipes(java.util.List.of(
                new RecipeHolder<>(ResourceLocation.parse("industrial_overkill:z"), other),
                new RecipeHolder<>(ResourceLocation.parse("industrial_overkill:a"), shortMeal)));
            var gym = AnimalGymTests.place(h, Direction.NORTH);
            gym.food.insertItem(3,
                MobTrapData.stack(MobTrapData.save(EntityType.PANDA.create(level))), false);
            gym.food.insertItem(0, new ItemStack(Items.BAMBOO, 2), false);
            gym.control("intensity");
            gym.control("intensity");
            gym.serverTick();
            require(gym.getGeneration() == 7 && gym.energy.stored() == 7
                && gym.captive.animal().getPersistentData()
                    .getInt("industrial_overkill:gym_ration") == 0,
                "Stable ID order and partial final tick cannot overpay a one-unit meal");
            for (int i = 0; i < 60; i++)
                gym.serverTick();
            require(gym.energy.stored() == 14 && gym.food.getStackInSlot(0).isEmpty(),
                "Short meals refill after chewing and do not stall permanently or grant free work");
        } finally {
            manager.replaceRecipes(original);
        }
        for (var fields : java.util.List.of("\"energy_per_tick\":0", "\"energy_per_tick\":10667",
            "\"food_duration_ticks\":0", "\"food_duration_ticks\":-1")) {
            require(GymAnimalRecipe.Serializer.CODEC.codec().parse(
                level.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString(
                    "{\"entity\":\"minecraft:panda\",\"food\":{\"item\":\"minecraft:bamboo\"},"
                        + fields + "}"))
                .error().isPresent(), "Invalid recipe rates rejected: " + fields);
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void recipesAddAnimalsOverrideFoodsAndReloadWithoutRestart(GameTestHelper h) {
        var level = h.getLevel();
        var manager = level.getRecipeManager();
        var original = new ArrayList<>(manager.getRecipes());
        var panda = EntityType.PANDA.create(level);
        var parrot = EntityType.PARROT.create(level);
        require(GymAnimalRecipe.eligible(panda) && !GymAnimalRecipe.eligible(parrot),
            "Default roster from recipes");
        require(GymAnimalRecipe.foodFor(panda, new ItemStack(Items.BAMBOO)),
            "Default bamboo recipe");
        try {
            var custom = parse(h, """
                {"entity":"minecraft:parrot","food":{"item":"minecraft:apple"}}
                """);
            require(custom.energyPerTick() == 8 && custom.foodDurationTicks() == 200,
                "Old recipes retain defaults");
            var disabled = parse(h, """
                {"entity":"minecraft:panda","food":{"item":"minecraft:bamboo"},"enabled":false}
                """);
            manager.replaceRecipes(java.util.List.of(
                new RecipeHolder<>(ResourceLocation.parse("industrial_overkill:test_parrot"),
                    custom),
                new RecipeHolder<>(ResourceLocation.parse("industrial_overkill:test_disabled"),
                    disabled)));
            require(!GymAnimalRecipe.eligible(panda) && GymAnimalRecipe.eligible(parrot),
                "Reload replaces cached roster; disabled recipe excluded");
            require(!GymAnimalRecipe.food(level, new ItemStack(Items.BAMBOO)),
                "Disabled foods leave inventory filter");
            require(GymAnimalRecipe.foodFor(parrot, new ItemStack(Items.APPLE)),
                "Recipe overrides vanilla animal diet");
            var gym = AnimalGymTests.place(h, Direction.NORTH);
            gym.food.insertItem(3, MobTrapData.stack(MobTrapData.save(parrot)), false);
            require(gym.food.insertItem(0, new ItemStack(Items.APPLE, 4), false).isEmpty(),
                "Custom recipe updates inventory filter");
            gym.serverTick();
            require(gym.getGeneration() == 8, "Custom animal trains using its recipe");
            manager.replaceRecipes(java.util.List.of());
            gym.serverTick();
            require(gym.getGeneration() == 0 && gym.training.status().equals("unsupported"),
                "Removed recipe stops current trainee even with paid ration");
        } finally {
            manager.replaceRecipes(original);
        }
        require(GymAnimalRecipe.eligible(panda) && !GymAnimalRecipe.eligible(parrot),
            "Reload restores defaults without stale cache");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void entityTagsIngredientsAndNetworkCodecRoundTrip(GameTestHelper h) {
        var rule = parse(h,
            """
                {"entity":"#industrial_overkill:gym/horses","food":[{"item":"minecraft:apple"},{"tag":"minecraft:fox_food"}],"energy_per_tick":24,"food_duration_ticks":1200}
                """);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            var codec = GymAnimalRecipe.SERIALIZER.get().streamCodec();
            codec.encode(buffer, rule);
            var decoded = codec.decode(buffer);
            require(decoded.energyPerTick() == 24 && decoded.foodDurationTicks() == 1200,
                "Custom rates survive network sync");
            require(
                decoded.entity().matches(EntityType.HORSE)
                    && decoded.entity().matches(EntityType.LLAMA)
                    && !decoded.entity().matches(EntityType.PANDA),
                "Entity tags survive network sync");
            require(decoded.food().test(new ItemStack(Items.APPLE))
                && decoded.food().test(new ItemStack(Items.GLOW_BERRIES))
                && !decoded.food().test(new ItemStack(Items.WHEAT)),
                "Ingredient alternatives and tags survive network sync");
        } finally {
            buffer.release();
        }
        require(GymAnimalRecipe.Serializer.CODEC.codec().parse(JsonOps.INSTANCE,
            JsonParser.parseString("""
                {"entity":"missing:animal","food":{"item":"minecraft:apple"}}
                """)).error().isPresent(), "Unknown entity IDs fail validation");
        h.succeed();
    }
}

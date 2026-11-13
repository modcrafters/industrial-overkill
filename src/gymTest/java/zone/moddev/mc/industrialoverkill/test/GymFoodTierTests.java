package zone.moddev.mc.industrialoverkill.test;

import static zone.moddev.mc.industrialoverkill.test.AnimalGymTests.require;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.industrialoverkill.gym.GymAnimalRecipe;
import zone.moddev.mc.industrialoverkill.trap.MobTrapData;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GymFoodTierTests {
    private record Meal(EntityType<? extends Animal> animal, Item food, int power, int duration) {
    }

    @GameTest(template = "gym_empty")
    public static void defaultFoodTiersAreDisjointAndPayTheirConfiguredRates(GameTestHelper h) {
        var gym = AnimalGymTests.place(h, Direction.NORTH);
        gym.control("intensity");
        gym.control("intensity");
        for (var meal : List.of(
            new Meal(EntityType.WOLF, Items.BEEF, 6, 600),
            new Meal(EntityType.WOLF, Items.COOKED_BEEF, 6, 900),
            new Meal(EntityType.WOLF, Items.COOKED_RABBIT, 6, 900),
            new Meal(EntityType.WOLF, Items.ROTTEN_FLESH, 6, 240),
            new Meal(EntityType.HORSE, Items.WHEAT, 24, 200),
            new Meal(EntityType.HORSE, Items.HAY_BLOCK, 24, 1800),
            new Meal(EntityType.HORSE, Items.GOLDEN_APPLE, 30, 400),
            new Meal(EntityType.LLAMA, Items.GOLDEN_CARROT, 30, 400),
            new Meal(EntityType.RABBIT, Items.CARROT, 2, 1200),
            new Meal(EntityType.RABBIT, Items.GOLDEN_CARROT, 3, 1800))) {
            var animal = meal.animal().create(h.getLevel());
            var food = new ItemStack(meal.food());
            long matches = GymAnimalRecipe.rules(h.getLevel()).stream()
                .filter(rule -> rule.entity().matches(animal.getType()) && rule.food().test(food))
                .count();
            require(matches == 1, "Exactly one default tier matches " + meal.food());
            gym.food.insertItem(3, MobTrapData.stack(MobTrapData.save(animal)), false);
            require(gym.food.insertItem(0, food, false).isEmpty(), "Tier food accepted");
            gym.energy.setStored(0);
            gym.serverTick();
            require(
                gym.getGeneration() == meal.power() * 3 && gym.energy.stored() == meal.power() * 3,
                "Tier pays selected-intensity output: " + meal.food());
            require(
                gym.captive.animal().getPersistentData()
                    .getInt("industrial_overkill:gym_ration") == meal.duration() - 3,
                "Tier pays its own duration: " + meal.food());
            require(gym.food.getStackInSlot(0).isEmpty(), "Exactly one food consumed");
            gym.training.reset();
            gym.food.extractItem(3, 1, false);
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void nextFoodTierDoesNotChangeTheCurrentlyPaidMeal(GameTestHelper h) {
        var gym = AnimalGymTests.place(h, Direction.NORTH);
        gym.food.insertItem(3,
            MobTrapData.stack(MobTrapData.save(EntityType.HORSE.create(h.getLevel()))), false);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT), false);
        gym.food.insertItem(1, new ItemStack(Items.GOLDEN_CARROT), false);
        for (int tick = 0; tick < 200; tick++)
            gym.serverTick();
        require(gym.energy.stored() == 4800 && gym.getGeneration() == 24,
            "Basic meal keeps its power while premium food hangs on the stick");
        require(gym.feeding.serving().is(Items.GOLDEN_CARROT), "Premium serving reserved once");
        gym.serverTick();
        require(gym.getGeneration() == 30 && gym.energy.stored() == 4830
            && gym.captive.animal().getPersistentData()
                .getInt("industrial_overkill:gym_ration") == 399,
            "Premium power and duration start only when the premium serving is eaten");
        h.succeed();
    }
}

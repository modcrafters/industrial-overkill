package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierData;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GymPowerLeftTests {
    @GameTest(template = "gym_empty")
    public static void mixedFoodIncludesHeldServingAndPaidRationExactlyOnce(GameTestHelper h) {
        var gym = AnimalGymTests.place(h, Direction.NORTH);
        gym.food.insertItem(2, new ItemStack(Items.SALMON, 10), false);
        gym.food.insertItem(3,
            CritterCourierData.stack(CritterCourierData.save(EntityType.HORSE.create(h.getLevel()))), false);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 2), false);
        gym.food.insertItem(1, new ItemStack(Items.GOLDEN_CARROT, 3), false);
        long total = 2 * 24 * 200 + 3 * 30 * 400;
        require(gym.getPowerLeft() == total, "Count each suitable food tier, excluding fish");
        require(gym.getFoodTicksLeft() == 1600,
            "Mixed food runtime uses each duration, not total FE divided by current power");
        require(gym.getStatusText().getString().contains("Power left: 45,600 FE\nTime left: 1m 20s"),
            "Status includes formatted remaining food energy and duration");
        for (int tick = 0; tick < 60; tick++) {
            gym.serverTick();
            require(gym.getPowerLeft() + gym.energy.stored() == total,
                "Eating, crumbs and refilling preserve total available energy");
            require(gym.getFoodTicksLeft() == 1600 - tick - 1,
                "Each working tick decrements runtime without double-counting servings");
        }
        require(gym.feeding.serving().is(Items.WHEAT), "Uneaten serving reserved on stick");
        gym.control("intensity");
        gym.control("intensity");
        require(gym.getPowerLeft() == total - 60 * 24, "Intensity changes rate, not total FE");
        require(gym.getFoodTicksLeft() == 516,
            "Intensity three rounds each meal's partial final tick independently");
        gym.serverTick();
        require(gym.getPowerLeft() + gym.energy.stored() == total, "Intensity-three work decrements total");
        require(gym.getFoodTicksLeft() == 515, "Selected-intensity runtime decrements once per work tick");
        gym.control("toggle");
        var saved = gym.saveWithFullMetadata(h.getLevel().registryAccess());
        gym.loadWithComponents(saved, h.getLevel().registryAccess());
        gym.serverTick();
        require(gym.getPowerLeft() + gym.energy.stored() == total, "Paused reload retains ration and serving");
        require(gym.getFoodTicksLeft() == 515, "Paused reload retains runtime at saved intensity");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void looseAnimalEstimateSurvivesRestAndDoesNotCountAbsentAnimals(GameTestHelper h) {
        var gym = AnimalGymTests.place(h, Direction.NORTH);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 4), false);
        require(gym.getPowerLeft() == 0, "No animal means no usable food estimate");
        var cow = AnimalGymTests.animal(h, gym, EntityType.COW);
        long total = 4 * 16 * 300;
        require(gym.getPowerLeft() == total, "Estimate works before acquisition");
        gym.serverTick();
        gym.energy.setStored(32000);
        gym.serverTick();
        require(gym.training.animal() == null && gym.getPowerLeft() == total - 16,
            "Demand wait releases claim but retains current cow's food estimate");
        require(gym.getFoodTicksLeft() == 1199, "Demand rest does not spend runtime");
        gym.control("toggle");
        require(gym.getPowerLeft() == total - 16, "Manual pause retains the estimate");
        cow.discard();
        require(gym.getPowerLeft() == 0, "Departed animal's ration is not counted");
        require(gym.getFoodTicksLeft() == 0 && gym.getStatusText().getString().contains("Time left: 0s"),
            "No animal has zero remaining runtime");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void paidRationUsesSnapshotAndLongArithmetic(GameTestHelper h) {
        var gym = AnimalGymTests.place(h, Direction.NORTH);
        var cow = EntityType.COW.create(h.getLevel());
        cow.getPersistentData().putInt("industrial_overkill:gym_ration", Integer.MAX_VALUE);
        gym.food.insertItem(3, CritterCourierData.stack(CritterCourierData.save(cow)), false);
        require(gym.getPowerLeft() == (long) Integer.MAX_VALUE * 8,
            "Legacy paid ration retains 8 FE rate without integer overflow");
        gym.captive.animal().getPersistentData().putInt("industrial_overkill:gym_ration_power", 10000);
        require(gym.getPowerLeft() == (long) Integer.MAX_VALUE * 10000,
            "Paid snapshot overrides current recipe rate");
        require(gym.getFoodTicksLeft() == Integer.MAX_VALUE
            && gym.getStatusText().getString().contains("h "), "Long durations display hours without overflow");
        h.succeed();
    }
}

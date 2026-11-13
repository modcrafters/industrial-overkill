package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGymEntity;
import net.modcrafters.minecraft.industrialoverkill.gym.GymScreenElements;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierData;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.ProcessorButtonState;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GymDemandTests {
    private static final String RATION = "industrial_overkill:gym_ration";

    static DefinedMachineEntity factory(AnimalGymEntity gym) {
        var level = gym.getLevel();
        var pos = gym.getBlockPos().relative(gym.facing().getOpposite());
        level.setBlock(pos, BuiltInRegistries.BLOCK.get(RockStewTests.ID).defaultBlockState(), 3);
        var factory = (DefinedMachineEntity) level.getBlockEntity(pos);
        factory.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        factory.fluids.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        factory.energy.setStored(factory.energy.capacity());
        return factory;
    }

    @GameTest(template = "gym_empty")
    public static void slowFactoryDemandDoesNotChatterAndEnergyIsConserved(GameTestHelper h) {
        var gym = AnimalGymTests.place(h, Direction.NORTH);
        var cow = AnimalGymTests.animal(h, gym, EntityType.COW);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 32), false);
        var factory = factory(gym);
        gym.energy.setStored(31984);
        long initialEnergy = gym.energy.stored() + factory.energy.stored();
        long generated = 0;
        int transitions = 0;
        boolean previous = true;
        var button = new GymScreenElements.RunButton(Common.empty());
        for (int tick = 0; tick < 3000; tick++) {
            gym.serverTick();
            factory.serverTick();
            boolean running = gym.getGeneration() > 0;
            generated += gym.getGeneration();
            if (running != previous) transitions++;
            previous = running;
            require(button.snapshot(gym).orElseThrow().value().equals(
                (running ? ProcessorButtonState.PROCESSING : ProcessorButtonState.IDLE).wireValue()),
                "Run button distinguishes demand idle from manual pause");
            require(gym.training.status().equals(running ? "running" : "full"), "Coherent activity status");
            AnimalGymTests.lamps(gym, running ? 14 : 0);
            require((cow.getAttributeValue(Attributes.SCALE) < 1) == running, "Cow fit follows stable activity");
        }
        require(transitions >= 2 && transitions <= 4, "Slow consumer causes long runs/rests, not tick-by-tick switching: " + transitions);
        int cobblestone = 0;
        for (int slot = 0; slot < 4; slot++) cobblestone += factory.items.getStackInSlot(slot).getCount();
        require(cobblestone > 60, "Factory continues making cobblestone while gym rests");
        long spent = cobblestone * 320L + factory.getProgress() * 8L;
        require(gym.energy.stored() + factory.energy.stored() == initialEnergy + generated - spent,
            "No discarded or free energy during demand cycles");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void fullBufferWaitSurvivesReloadAndResumesAtSeventyFivePercent(GameTestHelper h) {
        var gym = AnimalGymTests.place(h, Direction.NORTH);
        var cow = EntityType.COW.create(h.getLevel());
        gym.food.insertItem(3, CritterCourierData.stack(CritterCourierData.save(cow)), false);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 4), false);
        gym.serverTick();
        gym.energy.setStored(32000);
        gym.serverTick();
        int ration = gym.captive.animal().getPersistentData().getInt(RATION);
        var heldFood = gym.food.serializeNBT(h.getLevel().registryAccess());
        gym.energy.setStored(31984);
        var saved = gym.saveWithoutMetadata(h.getLevel().registryAccess());
        gym.loadWithComponents(saved, h.getLevel().registryAccess());
        for (int i = 0; i < 100; i++) gym.serverTick();
        require(gym.getGeneration() == 0 && gym.training.status().equals("full"), "Reload preserves demand wait");
        require(gym.captive.animal().getPersistentData().getInt(RATION) == ration
            && gym.food.serializeNBT(h.getLevel().registryAccess()).equals(heldFood), "Rest spends no food or ration");
        gym.control("toggle");
        gym.energy.setStored(24000);
        gym.serverTick();
        require(gym.training.status().equals("paused") && gym.getGeneration() == 0, "Demand does not bypass manual pause");
        gym.control("toggle");
        gym.energy.setStored(24001);
        gym.serverTick();
        require(gym.getGeneration() == 0, "Does not restart just above 75 percent");
        gym.energy.setStored(24000);
        gym.serverTick();
        require(gym.getGeneration() == 16 && gym.energy.stored() == 24016, "Restarts at exactly 75 percent");
        require(gym.captive.animal().getPersistentData().getInt(RATION) == ration - 1, "Resumes the same paid ration");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void restartStillRequiresRoomForTheWholeWorkUnit(GameTestHelper h) {
        var gym = AnimalGymTests.place(h, Direction.NORTH);
        var cow = AnimalGymTests.animal(h, gym, EntityType.COW);
        cow.getPersistentData().putInt(RATION, 100);
        cow.getPersistentData().putInt("industrial_overkill:gym_ration_power", 10000);
        gym.energy.setStored(32000);
        gym.serverTick();
        gym.energy.setStored(24000);
        gym.serverTick();
        require(gym.getGeneration() == 0 && cow.getPersistentData().getInt(RATION) == 100,
            "Large datapack rates cannot overfill or spend unpaid work");
        gym.energy.setStored(22000);
        gym.serverTick();
        require(gym.getGeneration() == 10000 && gym.energy.stored() == 32000, "Enough room permits the complete work unit");
        h.succeed();
    }
}

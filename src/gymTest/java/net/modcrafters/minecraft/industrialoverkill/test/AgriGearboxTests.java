package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriBrainProcessor;
import net.modcrafters.minecraft.industrialoverkill.upgrade.MachineUpgrades;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@GameTestHolder("industrial_overkill_agri")
@PrefixGameTestTemplate(false)
public final class AgriGearboxTests {
    private static DefinedMachineEntity place(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(
            "industrial_overkill:agri_brain")).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        machine.energy.setStored(100000);
        for (int index = 0; index < 9; index++) {
            var crop = crop(machine, index);
            h.getLevel().setBlock(crop.above(), Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(crop, Blocks.STONE.defaultBlockState(), 3);
            h.getLevel().setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
        }
        return machine;
    }

    private static BlockPos crop(DefinedMachineEntity machine, int index) {
        var front = machine.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        return machine.getBlockPos().relative(front.getClockWise(), (index / 3 % 2 == 0 ? index % 3 : 2 - index % 3) - 1).relative(front, -1 - index / 3);
    }

    private static void ticks(DefinedMachineEntity machine, int count) {
        for (int tick = 0; tick < count; tick++) machine.serverTick();
    }

    private static void at(DefinedMachineEntity machine, int index) {
        require(crop(machine, index).below().equals(AgriBrainProcessor.from(machine).droneSoil()),
            "Visualizer and work cycle remain at expected scan index " + index);
    }

    @GameTest(template = "gym_empty")
    public static void eightGearboxesDoubleWorkAndEnergyRateWithoutWear(GameTestHelper h) {
        var machine = place(h);
        machine.energy.setStored(1000);
        var gearbox = new ItemStack(MachineUpgrades.GEARBOX.get(), 8);
        machine.screenInventory("mmdlib_gearbox").setStackInSlot(0, gearbox.copy());
        machine.screenInventory("hoe").setStackInSlot(0, new ItemStack(Items.IRON_HOE));
        machine.screenInventory("seeds").setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 10));
        h.getLevel().setBlock(crop(machine, 0), Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(crop(machine, 0).below(), Blocks.DIRT.defaultBlockState(), 3);
        h.getLevel().setBlock(crop(machine, 1), ((CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
        h.getLevel().setBlock(crop(machine, 2), Blocks.AIR.defaultBlockState(), 3);
        ticks(machine, 8);
        require(machine.energy.stored() == 620 && h.getLevel().getBlockState(crop(machine, 0)).is(Blocks.WHEAT),
            "Eight game ticks fund sixteen work ticks plus till and plant actions");
        ticks(machine, 10);
        at(machine, 1);
        require(h.getLevel().getBlockState(crop(machine, 1)).getValue(CropBlock.AGE) == 7,
            "Gearbox does not harvest early or visit multiple blocks in one tick");
        machine.serverTick();
        at(machine, 2);
        require(machine.energy.stored() == 310 && h.getLevel().getBlockState(crop(machine, 1)).getValue(CropBlock.AGE) == 0,
            "The full harvest-and-replant visit adds twenty-one work ticks and two actions");
        ticks(machine, 8);
        at(machine, 3);
        require(machine.energy.stored() == 130 && h.getLevel().getBlockState(crop(machine, 2)).is(Blocks.WHEAT),
            "Planting also follows the faster cadence with unchanged action cost");
        machine.energy.setStored(100000);
        ticks(machine, 200);
        require(!gearbox.isDamageableItem() && ItemStack.matches(gearbox, machine.screenInventory("mmdlib_gearbox").getStackInSlot(0)),
            "Repeated complete work cycles never damage, consume or alter the gearbox");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void eachGearboxAddsOneEighthToScanningAndEnergyUse(GameTestHelper h) {
        var machine = place(h);
        var socket = machine.screenInventory("mmdlib_gearbox");
        require(BuiltInRegistries.ITEM.getKey(MachineUpgrades.GEARBOX.get()).toString().equals("mmdlib:gearbox_upgrade"),
            "The upgrade item belongs to MMDLib");
        for (int count = 0; count <= 8; count++) {
            socket.setStackInSlot(0, count == 0 ? ItemStack.EMPTY : new ItemStack(MachineUpgrades.GEARBOX.get(), count));
            long before = machine.energy.stored();
            ticks(machine, 8);
            require(before - machine.energy.stored() == 5L * (8 + count),
                "Each installed gearbox adds one extra five-FE work tick per eight game ticks: " + count);
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void fractionalSpeedSurvivesPauseReloadAndRemoval(GameTestHelper h) {
        var machine = place(h);
        machine.screenInventory("mmdlib_gearbox").setStackInSlot(0, new ItemStack(MachineUpgrades.GEARBOX.get()));
        ticks(machine, 7);
        require(machine.energy.stored() == 99965, "One gearbox has not earned an extra tick before the eighth tick");
        var processor = AgriBrainProcessor.from(machine);
        processor.setPaused(true);
        ticks(machine, 50);
        var registry = h.getLevel().registryAccess();
        machine.loadWithComponents(machine.saveWithoutMetadata(registry), registry);
        processor.setPaused(false);
        machine.screenInventory("mmdlib_gearbox").setStackInSlot(0, ItemStack.EMPTY);
        ticks(machine, 3);
        machine.screenInventory("mmdlib_gearbox").setStackInSlot(0, new ItemStack(MachineUpgrades.GEARBOX.get()));
        machine.serverTick();
        require(machine.energy.stored() == 99940, "Fractional work survives pause, reload and temporary removal");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void gearboxSwapsPreserveProgressAcrossPauseReloadAndPowerLoss(GameTestHelper h) {
        var machine = place(h);
        var processor = AgriBrainProcessor.from(machine);
        var upgrades = machine.screenInventory("upgrades");
        // Direct storage assignment also proves only the component socket drives speed.
        upgrades.setStackInSlot(0, new ItemStack(MachineUpgrades.GEARBOX.get(), 8));
        ticks(machine, 8);
        require(machine.energy.stored() == 99960, "Gearboxes outside the configured socket do not increase FE usage");
        upgrades.setStackInSlot(0, ItemStack.EMPTY);
        ticks(machine, 2); // One work tick remains on the first idle plot.
        at(machine, 0);
        machine.screenInventory("mmdlib_gearbox").setStackInSlot(0, new ItemStack(MachineUpgrades.GEARBOX.get(), 8));
        machine.serverTick();
        at(machine, 1); // The other work tick has started the next plot's travel.
        processor.setPaused(true);
        machine.screenInventory("mmdlib_gearbox").setStackInSlot(0, ItemStack.EMPTY);
        ticks(machine, 50);
        var registry = h.getLevel().registryAccess();
        machine.loadWithComponents(machine.saveWithoutMetadata(registry), registry);
        processor.setPaused(false);
        ticks(machine, 9);
        at(machine, 1);
        machine.serverTick();
        at(machine, 2);
        machine.screenInventory("mmdlib_gearbox").setStackInSlot(0, new ItemStack(MachineUpgrades.GEARBOX.get(), 8));
        machine.energy.setStored(4);
        ticks(machine, 20);
        require(processor.droneSoil() == null && machine.energy.stored() == 4,
            "A gearbox cannot advance the scan without energy");
        machine.loadWithComponents(machine.saveWithoutMetadata(registry), registry);
        require(machine.screenInventory("mmdlib_gearbox").getStackInSlot(0).getCount() == 8,
            "Installed gearbox stack survives reload");
        machine.energy.setStored(60);
        ticks(machine, 5);
        at(machine, 2);
        machine.serverTick();
        at(machine, 3);
        h.succeed();
    }
}

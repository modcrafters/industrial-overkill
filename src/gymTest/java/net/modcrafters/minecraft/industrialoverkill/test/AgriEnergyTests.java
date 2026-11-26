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
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.neoforge.MachineVisualState;
import zone.moddev.mc.lib.machine.presentation.VisualCondition;

@GameTestHolder("industrial_overkill_agri")
@PrefixGameTestTemplate(false)
public final class AgriEnergyTests {
    private static DefinedMachineEntity place(GameTestHelper h, long energy) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(
            "industrial_overkill:agri_brain")).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        machine.energy.setStored(energy);
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

    @GameTest(template = "gym_empty")
    public static void capacitorsUpdateLimitsImmediatelyAndNeverWearOut(GameTestHelper h) {
        var machine = place(h, 50000);
        var socket = machine.screenInventory("mmdlib_capacitor");
        var capacitor = new ItemStack(net.modcrafters.minecraft.industrialoverkill.upgrade.MachineUpgrades.CAPACITOR.get(), 8);
        require(BuiltInRegistries.ITEM.getKey(capacitor.getItem()).toString().equals("mmdlib:capacitor_upgrade")
            && !capacitor.isDamageableItem(), "Capacitors are permanent MMDLib items");
        AgriBrainProcessor.from(machine).setPaused(true);
        for (int count = 0; count <= 8; count++) {
            socket.setStackInSlot(0, count == 0 ? ItemStack.EMPTY : capacitor.copyWithCount(count));
            require(machine.energy.capacity() == 100000 + 12500L * count
                && machine.energy.receiveEnergy(9999, true) == 1000 + 125 * count
                && !machine.energy.canExtract() && machine.energy.stored() == 50000,
                "Capacitors immediately scale capacity and input, preserving zero output and stored FE: " + count);
        }
        ticks(machine, 40);
        require(ItemStack.matches(capacitor, socket.getStackInSlot(0)), "Capacitors are unchanged by machine ticks");
        machine.energy.setStored(180000);
        socket.setStackInSlot(0, capacitor.copyWithCount(4));
        require(machine.energy.capacity() == 150000 && machine.energy.stored() == 150000,
            "Removing capacitors clamps excess FE to the reduced capacity");
        socket.setStackInSlot(0, ItemStack.EMPTY);
        require(machine.energy.capacity() == 100000 && machine.energy.receiveEnergy(9999, true) == 0,
            "Empty socket restores base capacity without creating energy");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void capacitorCapacityLoadsBeforeEnergyOnAFreshMachine(GameTestHelper h) {
        var machine = place(h, 0);
        machine.screenInventory("mmdlib_capacitor").setStackInSlot(0,
            new ItemStack(net.modcrafters.minecraft.industrialoverkill.upgrade.MachineUpgrades.CAPACITOR.get(), 8));
        machine.energy.setStored(190000);
        var registry = h.getLevel().registryAccess();
        var saved = machine.saveWithFullMetadata(registry);
        var copy = (DefinedMachineEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
            machine.getBlockPos(), machine.getBlockState(), saved, registry);
        require(copy != null && copy.energy.capacity() == 200000 && copy.energy.stored() == 190000
            && copy.energy.receiveEnergy(9999, true) == 2000,
            "A fresh instance restores the upgraded capacity before loading energy above its base capacity");
        copy.loadWithComponents(saved, registry);
        require(copy.energy.capacity() == 200000 && copy.energy.stored() == 190000,
            "Repeated loads do not compound capacity or lose stored FE");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void emptyBufferHoldsScanAndResumesItsSavedCountdown(GameTestHelper h) {
        var machine = place(h, 14);
        var processor = AgriBrainProcessor.from(machine);
        var replica = (DefinedMachineEntity) machine.getType().create(machine.getBlockPos(), machine.getBlockState());
        requireLights(machine, replica, false);
        ticks(machine, 2);
        requireLights(machine, replica, true);
        require(machine.energy.stored() == 4, "Scanning consumes exactly five FE per running tick");
        ticks(machine, 50);
        requireLights(machine, replica, false);
        require(machine.energy.stored() == 4 && processor.executionStatus() == ProcessState.HELD
            && processor.droneSoil() == null, "Insufficient scan energy is retained and stops particles");
        var registry = h.getLevel().registryAccess();
        machine.loadWithComponents(machine.saveWithoutMetadata(registry), registry);
        machine.energy.setStored(45);
        ticks(machine, 8);
        requireLights(machine, replica, true);
        require(crop(machine, 0).below().equals(processor.droneSoil()),
            "Power loss and reload preserve the remaining check and travel ticks on the first block");
        machine.serverTick();
        require(machine.energy.stored() == 0 && crop(machine, 1).below().equals(processor.droneSoil()),
            "Scanning resumes at the next block after its retained countdown");
        machine.energy.setStored(100);
        processor.setPaused(true);
        requireLights(machine, replica, false);
        ticks(machine, 50);
        require(machine.energy.stored() == 100 && processor.droneSoil() == null, "Pause consumes no energy");
        h.succeed();
    }

    private static void requireLights(DefinedMachineEntity machine, DefinedMachineEntity replica, boolean lit) {
        var registry = machine.getLevel().registryAccess();
        replica.loadWithComponents(machine.getUpdateTag(registry), registry);
        var snapshot = replica.getModelData().get(MachineVisualState.VARIABLES);
        var id = BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock());
        var lights = MachineContent.definitions().get(id).modelParts().stream()
            .filter(part -> part.id().equals("powered_top") || part.id().startsWith("display_powered/")).toList();
        require(lights.size() == 2 && lights.stream().anyMatch(part -> part.overlay() != null && part.overlay().emissive()),
            "The powered top model and emissive front overlay are both present");
        for (var light : lights)
            require(VisualCondition.compile(light.when(), snapshot).test(snapshot) == lit,
                "Tracking clients see the correct working state for " + light.id());
    }

    @GameTest(template = "gym_empty")
    public static void chargesExactSuccessfulActionCostsOnTopOfScanning(GameTestHelper h) {
        var machine = place(h, 1000);
        machine.screenInventory("hoe").setStackInSlot(0, new ItemStack(Items.IRON_HOE));
        machine.screenInventory("seeds").setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 10));
        h.getLevel().setBlock(crop(machine, 0), Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(crop(machine, 0).below(), Blocks.DIRT.defaultBlockState(), 3);
        h.getLevel().setBlock(crop(machine, 1), ((CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
        h.getLevel().setBlock(crop(machine, 2), Blocks.AIR.defaultBlockState(), 3);
        ticks(machine, 16);
        require(machine.energy.stored() == 620 && h.getLevel().getBlockState(crop(machine, 0)).is(Blocks.WHEAT),
            "Tilling and planting cost 200 + 100 FE in addition to travel and two hover phases");
        ticks(machine, 21);
        require(machine.energy.stored() == 315
            && h.getLevel().getBlockState(crop(machine, 1)).getValue(CropBlock.AGE) == 0
            && machine.inventoryContents("output").stream().anyMatch(stack -> stack.is(Items.WHEAT)),
            "Harvesting and replanting cost 100 FE each, plus travel and three hover phases");
        ticks(machine, 16);
        require(machine.energy.stored() == 135 && machine.energy.used().longValueExact() == 865
            && h.getLevel().getBlockState(crop(machine, 2)).is(Blocks.WHEAT),
            "Planting existing farmland costs 100 FE; statistics include only completed work and scanning");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void underfundedTillingAndPlantingPreserveToolsSeedsAndTurn(GameTestHelper h) {
        var machine = place(h, 279);
        var processor = AgriBrainProcessor.from(machine);
        processor.cyclePlantingMode();
        machine.screenInventory("hoe").setStackInSlot(0, new ItemStack(Items.IRON_HOE));
        machine.screenInventory("seeds").setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        machine.screenInventory("seeds").setStackInSlot(1, new ItemStack(Items.CARROT, 2));
        var target = crop(machine, 0);
        h.getLevel().setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(target.below(), Blocks.DIRT.defaultBlockState(), 3);
        ticks(machine, 16);
        require(machine.energy.stored() == 199 && h.getLevel().getBlockState(target.below()).is(Blocks.DIRT)
            && machine.screenInventory("hoe").getStackInSlot(0).getDamageValue() == 0,
            "One FE short of tilling leaves soil and hoe unchanged");
        machine.energy.setStored(994);
        ticks(machine, 139);
        require(machine.energy.stored() == 99 && h.getLevel().getBlockState(target.below()).is(Blocks.FARMLAND)
            && h.getLevel().isEmptyBlock(target) && processor.nextSeedSlot() == 0
            && machine.screenInventory("seeds").getStackInSlot(0).getCount() == 2
            && machine.screenInventory("hoe").getStackInSlot(0).getDamageValue() == 0,
            "A later pass funds tilling alone, preserving seed and turn; this first successful till still has free wear");
        machine.energy.setStored(800);
        ticks(machine, 139);
        require(machine.energy.stored() == 5 && h.getLevel().getBlockState(target).is(Blocks.WHEAT)
            && processor.nextSeedSlot() == 1 && machine.screenInventory("seeds").getStackInSlot(0).getCount() == 1,
            "Replenished energy funds planting on the next pass");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void harvestingAndReplantingRequireSeparateFunding(GameTestHelper h) {
        var machine = place(h, 204);
        var target = crop(machine, 0);
        h.getLevel().setBlock(target, ((CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
        ticks(machine, 21);
        require(machine.energy.stored() == 99 && h.getLevel().getBlockState(target).getValue(CropBlock.AGE) == 7
            && machine.inventoryContents("output").stream().allMatch(ItemStack::isEmpty)
            && machine.inventoryContents("seeds").stream().allMatch(ItemStack::isEmpty),
            "One FE short of harvesting leaves the crop and every inventory untouched");
        machine.energy.setStored(919);
        ticks(machine, 144);
        require(machine.energy.stored() == 99 && h.getLevel().isEmptyBlock(target)
            && machine.inventoryContents("output").stream().anyMatch(stack -> stack.is(Items.WHEAT))
            && machine.inventoryContents("seeds").stream().anyMatch(stack -> stack.is(Items.WHEAT_SEEDS)),
            "Harvesting can finish and return seeds while replanting waits for its own energy");
        machine.energy.setStored(800);
        ticks(machine, 139);
        require(machine.energy.stored() == 5 && h.getLevel().getBlockState(target).is(Blocks.WHEAT),
            "Replanting resumes on the next funded pass");
        h.succeed();
    }
}

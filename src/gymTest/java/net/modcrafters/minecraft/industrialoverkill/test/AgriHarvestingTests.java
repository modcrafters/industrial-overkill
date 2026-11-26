package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriBrainProcessor;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmSupplies;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@GameTestHolder("industrial_overkill_agri")
@PrefixGameTestTemplate(false)
public final class AgriHarvestingTests {
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

    private static int count(DefinedMachineEntity machine, String inventory, Item item) {
        return machine.inventoryContents(inventory).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void matureWheat(GameTestHelper h, DefinedMachineEntity machine, int index) {
        h.getLevel().setBlock(crop(machine, index), ((CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
    }

    @GameTest(template = "gym_empty")
    public static void commitsFertilizeHarvestAndPlantOnTheLastHoverTick(GameTestHelper h) {
        var machine = place(h);
        var processor = AgriBrainProcessor.from(machine);
        require(processor.invoke(FarmSupplies.id("fertilizing_smart"), h.makeMockPlayer(GameType.SURVIVAL)).accepted(),
            "Smart is selectable from the machine's button action");
        var pos = crop(machine, 0);
        h.getLevel().setBlock(pos, ((CropBlock) Blocks.WHEAT).getStateForAge(5), 3);
        machine.screenInventory("seeds").setStackInSlot(0, new ItemStack(Items.CARROT, 8));
        machine.screenInventory("fertilizer").setStackInSlot(0, new ItemStack(FarmSupplies.FERTILIZER.get()));
        machine.screenInventory("filtered_fertilizer").setStackInSlot(0, new ItemStack(FarmSupplies.FILTERED_FERTILIZER.get()));
        machine.screenInventory("pellets").setStackInSlot(0, new ItemStack(FarmSupplies.SLOW_RELEASE_PELLET.get()));

        ticks(machine, 6);
        require(processor.phase() == AgriBrainProcessor.Phase.CHECK, "Travel ends before the maturity check");
        ticks(machine, 5);
        require(processor.phase() == AgriBrainProcessor.Phase.FERTILIZE, "The check schedules a finishing dose");
        ticks(machine, 5);
        require(processor.phase() == AgriBrainProcessor.Phase.HARVEST, "The finishing dose schedules harvest");
        ticks(machine, 5);
        require(processor.phase() == AgriBrainProcessor.Phase.PLANT, "The drone prepares to replant");
        ticks(machine, 4);
        require(h.getLevel().getBlockState(pos).getValue(CropBlock.AGE) == 5
            && count(machine, "output", Items.WHEAT) == 0, "The first nineteen hover ticks leave the crop and drops untouched");
        var registries = h.getLevel().registryAccess();
        machine.loadWithComponents(machine.saveWithoutMetadata(registries), registries);
        machine.serverTick();
        var planted = h.getLevel().getBlockState(pos);
        require(planted.is(Blocks.CARROTS) && ((CropBlock) Blocks.CARROTS).getAge(planted) == 2
            && count(machine, "output", Items.WHEAT) == 1,
            "The twentieth hover tick drains the finishing dose, harvests wheat, and plants and feeds a replacement");
        require(count(machine, "fertilizer", FarmSupplies.FERTILIZER.get()) == 1
            && count(machine, "filtered_fertilizer", FarmSupplies.FILTERED_FERTILIZER.get()) == 0
            && count(machine, "pellets", FarmSupplies.SLOW_RELEASE_PELLET.get()) == 0
            && machine.energy.stored() == 100000 - 26 * 5 - 400,
            "Smart uses filtered on the old crop and one pellet on the new crop, charging each successful action once");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void finishOnlySavesFilteredDosesUntilBothStagesCanBeUsed(GameTestHelper h) {
        var machine = place(h);
        var processor = AgriBrainProcessor.from(machine);
        require(processor.invoke(FarmSupplies.id("fertilizing_finish"), h.makeMockPlayer(GameType.SURVIVAL)).accepted(),
            "Finish Only is selectable");
        var wheat = (CropBlock) Blocks.WHEAT;
        h.getLevel().setBlock(crop(machine, 0), wheat.getStateForAge(6), 3);
        h.getLevel().setBlock(crop(machine, 1), wheat.getStateForAge(4), 3);
        h.getLevel().setBlock(crop(machine, 2), wheat.getStateForAge(5), 3);
        machine.screenInventory("filtered_fertilizer").setStackInSlot(0, new ItemStack(FarmSupplies.FILTERED_FERTILIZER.get()));
        machine.screenInventory("pellets").setStackInSlot(0, new ItemStack(FarmSupplies.SLOW_RELEASE_PELLET.get()));
        machine.screenInventory("seeds").setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 8));
        ticks(machine, 22);
        require(wheat.getAge(h.getLevel().getBlockState(crop(machine, 0))) == 6
            && wheat.getAge(h.getLevel().getBlockState(crop(machine, 1))) == 4
            && count(machine, "filtered_fertilizer", FarmSupplies.FILTERED_FERTILIZER.get()) == 1,
            "A filtered dose is saved when one stage would be wasted or the crop would stay immature");
        ticks(machine, 26);
        require(wheat.getAge(h.getLevel().getBlockState(crop(machine, 2))) == 0
            && count(machine, "output", Items.WHEAT) == 1
            && count(machine, "pellets", FarmSupplies.SLOW_RELEASE_PELLET.get()) == 1,
            "An exact finishing dose harvests and replants while leaving pellet stock alone");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void returnsSeedsBeforeOutputAndReplantsRoundRobinSelection(GameTestHelper h) {
        var machine = place(h);
        var seeds = machine.screenInventory("seeds");
        seeds.setStackInSlot(0, new ItemStack(Items.CARROT, 5));
        seeds.setStackInSlot(2, new ItemStack(Items.WHEAT_SEEDS, 32));
        var processor = AgriBrainProcessor.from(machine);
        processor.cyclePlantingMode();
        matureWheat(h, machine, 0);
        h.getLevel().setBlock(crop(machine, 1), ((CropBlock) Blocks.WHEAT).getStateForAge(6), 3);
        matureWheat(h, machine, 2);
        ticks(machine, 21);
        require(h.getLevel().getBlockState(crop(machine, 0)).is(Blocks.CARROTS)
            && count(machine, "output", Items.WHEAT) == 1, "Harvest output is stored and the selected carrot is replanted");
        require(seeds.getStackInSlot(2).getCount() > 32 && seeds.getStackInSlot(1).isEmpty()
            && count(machine, "output", Items.WHEAT_SEEDS) == 0,
            "Harvested seeds merge into the existing input stack before empty inputs or output");
        require(processor.nextSeedSlot() == 2, "Harvest replanting advances round-robin exactly once");
        ticks(machine, 11);
        require(h.getLevel().getBlockState(crop(machine, 1)).getValue(CropBlock.AGE) == 6
            && count(machine, "output", Items.WHEAT) == 1, "Immature crops are untouched");
        ticks(machine, 21);
        require(h.getLevel().getBlockState(crop(machine, 2)).is(Blocks.WHEAT)
            && h.getLevel().getBlockState(crop(machine, 2)).getValue(CropBlock.AGE) == 0
            && count(machine, "output", Items.WHEAT) == 2 && processor.nextSeedSlot() == 0,
            "The next mature crop replants from the next non-empty seed slot");
        var registry = h.getLevel().registryAccess();
        var outputBefore = machine.inventoryContents("output");
        machine.loadWithComponents(machine.saveWithoutMetadata(registry), registry);
        for (int slot = 0; slot < 9; slot++) require(ItemStack.matches(outputBefore.get(slot), machine.screenInventory("output").getStackInSlot(slot)),
            "Harvest outputs survive save/reload");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void fullOutputLeavesCropAndInventoriesUntouchedUntilSpaceReturns(GameTestHelper h) {
        var machine = place(h);
        for (int slot = 0; slot < 9; slot++) machine.screenInventory("output").setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        machine.screenInventory("seeds").setStackInSlot(0, new ItemStack(Items.CARROT));
        matureWheat(h, machine, 0);
        var before = machine.inventoryContents("seeds");
        ticks(machine, 21);
        require(h.getLevel().getBlockState(crop(machine, 0)).getValue(CropBlock.AGE) == 7,
            "A crop stays mature when all its drops cannot fit");
        for (int slot = 0; slot < 4; slot++) require(ItemStack.matches(before.get(slot), machine.screenInventory("seeds").getStackInSlot(slot)),
            "Failed capacity checks do not partially insert seeds or consume planting stock");
        require(count(machine, "output", Items.COBBLESTONE) == 576, "Full output remains unchanged");
        require(machine.energy.stored() == 99895 && machine.energy.used().longValueExact() == 105,
            "A capacity-blocked harvest costs only the travel and hover ticks");
        machine.screenInventory("output").setStackInSlot(0, ItemStack.EMPTY);
        ticks(machine, 143);
        require(h.getLevel().getBlockState(crop(machine, 0)).getValue(CropBlock.AGE) == 7,
            "The first crop stays untouched throughout the return flight");
        machine.serverTick();
        require(count(machine, "output", Items.WHEAT) == 1 && h.getLevel().getBlockState(crop(machine, 0)).is(Blocks.CARROTS),
            "The next pass harvests once when output space returns");
        require(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(crop(machine, 0)).inflate(1)).isEmpty(),
            "Harvesting spawns no loose item drops");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void seedOverflowUsesOutputAndSeedOnlyHarvestCanUseFullOutput(GameTestHelper h) {
        var machine = place(h);
        var seeds = machine.screenInventory("seeds");
        for (int slot = 0; slot < 4; slot++) seeds.setStackInSlot(slot, new ItemStack(Items.WHEAT_SEEDS, 64));
        seeds.setStackInSlot(2, new ItemStack(Items.CARROT, 63));
        h.getLevel().setBlock(crop(machine, 0), ((CropBlock) Blocks.CARROTS).getStateForAge(7), 3);
        ticks(machine, 21);
        require(seeds.getStackInSlot(2).getCount() == 64 && count(machine, "output", Items.CARROT) >= 1,
            "Seed drops first fill the last input space, then overflow to output");
        require(seeds.getStackInSlot(0).getCount() == 63 && h.getLevel().getBlockState(crop(machine, 0)).is(Blocks.WHEAT),
            "First-found replanting still uses slot one rather than forcing the harvested seed type");
        for (int slot = 0; slot < 9; slot++) machine.screenInventory("output").setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        seeds.setStackInSlot(2, new ItemStack(Items.CARROT, 10));
        h.getLevel().setBlock(crop(machine, 1), ((CropBlock) Blocks.CARROTS).getStateForAge(7), 3);
        ticks(machine, 21);
        require(h.getLevel().getBlockState(crop(machine, 1)).is(Blocks.WHEAT) && seeds.getStackInSlot(2).getCount() > 10,
            "Full output does not block a harvest whose drops all fit in seed inputs");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void rowsModeAndBreakCancellationApplyToHarvesting(GameTestHelper h) {
        var machine = place(h);
        var processor = AgriBrainProcessor.from(machine);
        processor.cyclePlantingMode();
        processor.cyclePlantingMode();
        machine.screenInventory("seeds").setStackInSlot(0, new ItemStack(Items.CARROT, 3));
        machine.screenInventory("seeds").setStackInSlot(1, new ItemStack(Items.POTATO, 3));
        // Flowers do not yield seeds that could populate the deliberately empty third slot.
        for (int index : List.of(0, 3, 6)) h.getLevel().setBlock(crop(machine, index), Blocks.TORCHFLOWER.defaultBlockState(), 3);
        Consumer<BlockEvent.BreakEvent> denied = event -> {
            if (event.getLevel() == h.getLevel() && event.getPos().equals(crop(machine, 0))) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(denied);
        try {
            ticks(machine, 21);
            require(h.getLevel().getBlockState(crop(machine, 0)).is(Blocks.TORCHFLOWER)
                && count(machine, "output", Items.TORCHFLOWER) == 0, "Denied crop breaking produces no drops or replacement");
            require(machine.energy.stored() == 99895 && machine.energy.used().longValueExact() == 105,
                "A cancelled harvest costs only the travel and hover ticks");
        } finally { NeoForge.EVENT_BUS.unregister(denied); }
        ticks(machine, 71);
        require(h.getLevel().getBlockState(crop(machine, 3)).is(Blocks.POTATOES), "Row two replants from slot two");
        require(h.getLevel().isEmptyBlock(crop(machine, 6)) && count(machine, "output", Items.TORCHFLOWER) == 2,
            "An empty assigned seed slot leaves the harvested row empty");
        processor.setPaused(true);
        ticks(machine, 180);
        require(h.getLevel().getBlockState(crop(machine, 0)).is(Blocks.TORCHFLOWER), "Pause prevents harvesting");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void maturePitcherIsCollectedOnceAndStemsRemainIntact(GameTestHelper h) {
        var machine = place(h);
        var lower = Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, 4)
            .setValue(PitcherCropBlock.HALF, DoubleBlockHalf.LOWER);
        h.getLevel().setBlock(crop(machine, 0), lower, 2);
        h.getLevel().setBlock(crop(machine, 0).above(), lower.setValue(PitcherCropBlock.HALF, DoubleBlockHalf.UPPER), 3);
        h.getLevel().setBlock(crop(machine, 1), Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE, 7), 3);
        h.getLevel().setBlock(crop(machine, 2), Blocks.MELON.defaultBlockState(), 3);
        ticks(machine, 43);
        require(count(machine, "output", Items.PITCHER_PLANT) == 1
            && h.getLevel().isEmptyBlock(crop(machine, 0)) && h.getLevel().isEmptyBlock(crop(machine, 0).above()),
            "Both pitcher halves are removed with exactly one plant drop stored");
        require(h.getLevel().getBlockState(crop(machine, 1)).is(Blocks.MELON_STEM)
            || h.getLevel().getBlockState(crop(machine, 1)).is(Blocks.ATTACHED_MELON_STEM),
            "The mature stem is preserved");
        require(count(machine, "output", Items.MELON_SLICE) >= 3, "The fruit is harvested through its normal loot table");
        require(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(crop(machine, 0)).inflate(1)).isEmpty(),
            "Pitcher partner removal does not spill duplicate drops");
        h.succeed();
    }
}

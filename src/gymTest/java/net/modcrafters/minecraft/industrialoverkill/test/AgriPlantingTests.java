package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriBrainProcessor;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriBrainScreen;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriSettings;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.api.agri.AgriHarvestHandler;
import net.modcrafters.minecraft.industrialoverkill.upgrade.MachineUpgrades;
import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.drawer.BuiltinDrawers;
import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.model.elements.CycleButtonElement;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenNetwork;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@GameTestHolder("industrial_overkill_agri")
@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public final class AgriPlantingTests {
    private static DefinedMachineEntity place(GameTestHelper h, int upgrades) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(
            "industrial_overkill:agri_brain")).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        machine.energy.setStored(100000);
        for (int i = 0; i < upgrades; i++)
            machine.screenInventory("upgrades").setStackInSlot(i, new ItemStack(MachineUpgrades.RANGE.get()));
        int width = 3 + 2 * upgrades;
        for (int index = 0; index < width * width; index++) {
            var crop = crop(machine, width, index);
            h.getLevel().setBlock(crop, Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(crop.above(), Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
        }
        return machine;
    }

    private static BlockPos crop(DefinedMachineEntity machine, int width, int index) {
        var front = machine.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        return machine.getBlockPos().relative(front.getClockWise(), (index / width % 2 == 0 ? index % width : width - 1 - index % width) - width / 2)
            .relative(front, -1 - index / width);
    }

    private static void ticks(DefinedMachineEntity machine, int count) {
        for (int tick = 0; tick < count; tick++) machine.serverTick();
    }

    // A test-only compatibility provider, registered exactly as a separate mod would register one.
    @SubscribeEvent
    public static void registerBerryHarvest(RegisterCapabilitiesEvent event) {
        AgriHarvestHandler picker = (level, base, state) -> state.getValue(BlockStateProperties.BERRIES)
            ? new AgriHarvestHandler.Harvest(base, state.setValue(BlockStateProperties.BERRIES, false),
                (world, target, harvested, player) -> List.of(new ItemStack(Items.GLOW_BERRIES)))
            : null;
        event.registerBlock(AgriHarvestHandler.CAPABILITY, (level, pos, state, entity, context) -> picker, Blocks.CAVE_VINES);
    }

    @GameTest(template = "gym_empty")
    public static void plantsStalksOnTheirOwnSoils(GameTestHelper h) {
        var machine = place(h, 0);
        var seeds = machine.screenInventory("seeds");
        var level = h.getLevel();
        machine.screenInventory("hoe").setStackInSlot(0, new ItemStack(Items.IRON_HOE));
        for (int i = 0; i < 3; i++) level.setBlockAndUpdate(crop(machine, 3, i).below(), Blocks.DIRT.defaultBlockState());
        require(seeds.insertItem(0, new ItemStack(Items.SUGAR_CANE, 2), false).isEmpty(), "The ordinary seed port accepts cane");
        ticks(machine, 16);
        require(level.isEmptyBlock(crop(machine, 3, 0)) && level.getBlockState(crop(machine, 3, 0).below()).is(Blocks.DIRT)
            && seeds.getStackInSlot(0).getCount() == 2, "Dry cane skips the dirt and spends no planting item");
        level.setBlockAndUpdate(crop(machine, 3, 4).below(), Blocks.WATER.defaultBlockState());
        ticks(machine, 16);
        require(level.getBlockState(crop(machine, 3, 1)).is(Blocks.SUGAR_CANE) && seeds.getStackInSlot(0).getCount() == 1,
            "Cane plants beside water with a hoe installed");
        seeds.setStackInSlot(0, new ItemStack(Items.BAMBOO));
        ticks(machine, 16);
        require(level.getBlockState(crop(machine, 3, 2)).is(Blocks.BAMBOO_SAPLING) && seeds.getStackInSlot(0).isEmpty(),
            "Bamboo uses vanilla shoot placement on dirt");
        level.removeBlock(crop(machine, 3, 2), false); // Clear the side of the first cactus plot.
        for (int i = 3; i <= 4; i++) level.setBlockAndUpdate(crop(machine, 3, i).below(), Blocks.SAND.defaultBlockState());
        seeds.setStackInSlot(0, new ItemStack(Items.CACTUS, 2));
        ticks(machine, 32);
        require(level.getBlockState(crop(machine, 3, 3)).is(Blocks.CACTUS) && level.isEmptyBlock(crop(machine, 3, 4))
            && seeds.getStackInSlot(0).getCount() == 1, "Cactus plants on sand and skips the spot beside the first cactus");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void keepsTheFourPlotsBesideCactusClear(GameTestHelper h) {
        var machine = place(h, 0);
        var level = h.getLevel();
        var cactus = crop(machine, 3, 4);
        level.setBlockAndUpdate(cactus.below(), Blocks.SAND.defaultBlockState());
        level.setBlockAndUpdate(cactus, Blocks.CACTUS.defaultBlockState());
        var seeds = machine.screenInventory("seeds");
        seeds.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 12));
        ticks(machine, 300);
        for (int i : List.of(1, 3, 5, 7))
            require(level.isEmptyBlock(crop(machine, 3, i)), "Cactus keeps each face-adjacent plot empty");
        for (int i : List.of(0, 2, 6, 8))
            require(level.getBlockState(crop(machine, 3, i)).is(Blocks.WHEAT), "Diagonal plots can grow wheat");
        require(seeds.getStackInSlot(0).getCount() == 8, "Only the four planted plots spend seeds");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void rechecksCactusClearanceBeforeSpendingTheBerry(GameTestHelper h) {
        var machine = place(h, 0);
        var level = h.getLevel();
        var target = crop(machine, 3, 0);
        var neighbor = crop(machine, 3, 5);
        level.setBlockAndUpdate(target.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(neighbor.below(), Blocks.SAND.defaultBlockState());
        var seeds = machine.screenInventory("seeds");
        seeds.setStackInSlot(0, new ItemStack(Items.SWEET_BERRIES));
        seeds.setStackInSlot(1, new ItemStack(Items.BAMBOO));
        var processor = AgriBrainProcessor.from(machine);
        processor.cyclePlantingMode();
        ticks(machine, 11);
        require(processor.phase() == AgriBrainProcessor.Phase.PLANT, "The clear plot has a planned berry planting");
        level.setBlockAndUpdate(neighbor, Blocks.CACTUS.defaultBlockState());
        ticks(machine, 5);
        require(level.isEmptyBlock(target) && seeds.getStackInSlot(0).getCount() == 1 && processor.nextSeedSlot() == 0
            && machine.energy.stored() == 100000 - 16 * AgriBrainProcessor.SCAN_FE_PER_TICK,
            "A new cactus during hover preserves the berry, its turn, and the planting FE");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void waitsForSpaceBeforePlantingCactusBesideWheat(GameTestHelper h) {
        var machine = place(h, 0);
        var level = h.getLevel();
        var target = crop(machine, 3, 0);
        var neighbor = crop(machine, 3, 1);
        level.setBlockAndUpdate(target.below(), Blocks.SAND.defaultBlockState());
        level.setBlockAndUpdate(neighbor, Blocks.WHEAT.defaultBlockState());
        var seeds = machine.screenInventory("seeds");
        seeds.setStackInSlot(0, new ItemStack(Items.CACTUS));
        ticks(machine, 16);
        require(level.isEmptyBlock(target) && seeds.getStackInSlot(0).getCount() == 1, "A growing crop keeps cactus out");
        level.removeBlock(neighbor, false);
        ticks(machine, 300);
        require(level.getBlockState(target).is(Blocks.CACTUS) && seeds.getStackInSlot(0).isEmpty(),
            "The same plot accepts cactus after the neighbor is cleared");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void trimsColumnsAndPicksCustomCropsOnlyWhenDropsFit(GameTestHelper h) {
        var machine = place(h, 0);
        var level = h.getLevel();
        var output = machine.screenInventory("output");
        for (int i = 0; i < 2; i++) {
            var base = crop(machine, 3, i);
            level.setBlockAndUpdate(base.below(), Blocks.DIRT.defaultBlockState());
            for (int height = 0; height < 3; height++) level.setBlockAndUpdate(base.above(height), Blocks.BAMBOO.defaultBlockState());
        }
        var vine = crop(machine, 3, 2);
        level.setBlockAndUpdate(vine.above(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(vine, Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, true));
        for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        ticks(machine, 16);
        require(level.getBlockState(crop(machine, 3, 0).above(2)).is(Blocks.BAMBOO), "Full output preserves the entire column");
        output.setStackInSlot(0, ItemStack.EMPTY);
        ticks(machine, 16);
        var base = crop(machine, 3, 1);
        require(level.getBlockState(base).is(Blocks.BAMBOO) && level.getBlockState(base.above()).is(Blocks.BAMBOO)
            && level.isEmptyBlock(base.above(2)) && output.getStackInSlot(0).is(Items.BAMBOO)
            && output.getStackInSlot(0).getCount() == 1, "The top harvest goes to output and preserves its supports");
        output.setStackInSlot(1, ItemStack.EMPTY);
        ticks(machine, 16);
        require(!level.getBlockState(vine).getValue(BlockStateProperties.BERRIES) && output.getStackInSlot(1).is(Items.GLOW_BERRIES)
            && output.getStackInSlot(1).getCount() == 1, "The compatibility handler picks berries and leaves the vine");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void plantsAndPicksTaggedBushesWithoutUpgrades(GameTestHelper h) {
        var machine = place(h, 0);
        var level = h.getLevel();
        var bush = crop(machine, 3, 0);
        level.setBlockAndUpdate(bush.below(), Blocks.DIRT.defaultBlockState());
        var seeds = machine.screenInventory("seeds");
        require(seeds.insertItem(0, new ItemStack(Items.SWEET_BERRIES), false).isEmpty(), "Seed slots accept sweet berries");
        ticks(machine, 16);
        require(level.getBlockState(bush).is(Blocks.SWEET_BERRY_BUSH) && level.getBlockState(bush.below()).is(Blocks.DIRT)
            && seeds.getStackInSlot(0).isEmpty(), "One berry plants a bush directly on dirt");

        var unripe = crop(machine, 3, 1);
        level.setBlockAndUpdate(unripe, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 2));
        var wart = crop(machine, 3, 2);
        level.setBlockAndUpdate(wart.below(), Blocks.SOUL_SAND.defaultBlockState());
        level.setBlockAndUpdate(wart, Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3));
        level.setBlockAndUpdate(bush, level.getBlockState(bush).setValue(SweetBerryBushBlock.AGE, 3));
        var output = machine.screenInventory("output");
        for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        ticks(machine, 300);
        require(level.getBlockState(bush).getValue(SweetBerryBushBlock.AGE) == 3, "A full harvest inventory preserves ripe berries");
        output.setStackInSlot(0, ItemStack.EMPTY);
        output.setStackInSlot(1, ItemStack.EMPTY);
        ticks(machine, 300);
        var picked = output.getStackInSlot(0);
        require(picked.is(Items.SWEET_BERRIES) && picked.getCount() >= 2 && picked.getCount() <= 3
            && seeds.getStackInSlot(0).isEmpty(), "Picking sends the full ripe yield to Harvest");
        require(level.getBlockState(bush).getValue(SweetBerryBushBlock.AGE) == 1
            && level.getBlockState(unripe).getValue(SweetBerryBushBlock.AGE) == 2, "The picked bush regrows and the unripe bush is untouched");
        require(level.getBlockState(wart).getValue(NetherWartBlock.AGE) == 3, "An untagged age-based plant is left alone");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void firstFoundConsumesInSlotOrderAndSkipsOccupiedFarmland(GameTestHelper h) {
        var machine = place(h, 0);
        var seeds = machine.screenInventory("seeds");
        require(seeds.getSlots() == 4 && seeds.insertItem(0, new ItemStack(Items.STONE), false).is(Items.STONE),
            "Four seed input slots reject non-seeds through storage");
        seeds.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        seeds.setStackInSlot(1, new ItemStack(Items.CARROT, 2));
        h.getLevel().setBlock(crop(machine, 3, 0), Blocks.STONE.defaultBlockState(), 3);
        ticks(machine, 11);
        require(seeds.getStackInSlot(0).getCount() == 2, "Occupied crop space consumes no seed");
        ticks(machine, 48);
        require(h.getLevel().getBlockState(crop(machine, 3, 1)).is(Blocks.WHEAT)
            && h.getLevel().getBlockState(crop(machine, 3, 2)).is(Blocks.WHEAT)
            && h.getLevel().getBlockState(crop(machine, 3, 3)).is(Blocks.CARROTS),
            "First-found mode exhausts the first seed stack before using the next");
        require(seeds.getStackInSlot(0).isEmpty() && seeds.getStackInSlot(1).getCount() == 1,
            "Each successful planting consumes exactly one item");
        ticks(machine, 180);
        require(h.getLevel().getBlockState(crop(machine, 3, 1)).is(Blocks.WHEAT),
            "Later passes preserve existing crops");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void roundRobinSkipsEmptySlotsAndSurvivesReloadAndCancellation(GameTestHelper h) {
        var machine = place(h, 0);
        var processor = AgriBrainProcessor.from(machine);
        processor.cyclePlantingMode();
        var seeds = machine.screenInventory("seeds");
        seeds.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 3));
        seeds.setStackInSlot(2, new ItemStack(Items.CARROT, 3));
        require(processor.nextSeedSlot() == 0, "Round-robin starts at the first populated slot");
        ticks(machine, 16);
        require(processor.nextSeedSlot() == 2, "A successful planting advances past empty slot two");
        var registry = h.getLevel().registryAccess();
        machine.loadWithComponents(machine.saveWithoutMetadata(registry), registry);
        require(processor.plantingMode() == 1 && processor.nextSeedSlot() == 2,
            "Mode, seeds and next slot survive reload");
        var denied = crop(machine, 3, 1);
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getLevel() == h.getLevel() && event.getPos().equals(denied)) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            ticks(machine, 16);
            require(h.getLevel().isEmptyBlock(denied) && seeds.getStackInSlot(2).getCount() == 3
                && processor.nextSeedSlot() == 2, "Cancelled placement consumes neither seed nor round-robin turn");
            require(machine.energy.stored() == 100000 - 32 * 5 - 100,
                "Cancelled planting does not consume its action cost");
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
        ticks(machine, 16);
        require(h.getLevel().getBlockState(crop(machine, 3, 2)).is(Blocks.CARROTS)
            && processor.nextSeedSlot() == 0, "The next successful planting uses the retained slot and wraps");
        processor.setPaused(true);
        ticks(machine, 40);
        require(h.getLevel().isEmptyBlock(crop(machine, 3, 3)), "Pause prevents planting");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void rowsRepeatEveryFourAndLeaveMissingSeedRowsEmpty(GameTestHelper h) {
        var machine = place(h, 3); // Nine rows, including repeats at five and nine.
        var processor = AgriBrainProcessor.from(machine);
        processor.cyclePlantingMode();
        processor.cyclePlantingMode();
        var seeds = machine.screenInventory("seeds");
        seeds.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 64));
        seeds.setStackInSlot(2, new ItemStack(Items.CARROT, 64));
        seeds.setStackInSlot(3, new ItemStack(Items.POTATO, 64));
        ticks(machine, 63 * 16 + 18 * 11);
        for (int row = 0; row < 9; row++) {
            for (int column = 0; column < 9; column++) {
                var state = h.getLevel().getBlockState(crop(machine, 9, row * 9 + column));
                var expected = switch (row % 4) {
                    case 0 -> Blocks.WHEAT;
                    case 2 -> Blocks.CARROTS;
                    case 3 -> Blocks.POTATOES;
                    default -> Blocks.AIR;
                };
                require(state.is(expected), "Row " + (row + 1) + " uses its assigned slot without fallback");
            }
        }
        require(seeds.getStackInSlot(0).getCount() == 37 && seeds.getStackInSlot(2).getCount() == 46
            && seeds.getStackInSlot(3).getCount() == 46, "Row planting consumes the expected number of each seed");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void seedGridModeButtonAndCurrentSlotSnapshotsWorkTogether(GameTestHelper h) {
        var machine = place(h, 0);
        machine.screenInventory("seeds").setStackInSlot(1, new ItemStack(Items.WHEAT_SEEDS, 2));
        var player = h.makeMockServerPlayerInLevel();
        player.setPos(machine.getBlockPos().getX() + .5, machine.getBlockPos().getY() + .5, machine.getBlockPos().getZ() + .5);
        var previous = player.containerMenu;
        var menu = new MachineMenu(93, player.getInventory(), machine);
        player.containerMenu = menu;
        try {
            var outputs = java.util.stream.IntStream.range(0, 9).mapToObj(i ->
                (zone.moddev.mc.lib.gui.model.elements.ItemSlotElement) menu.bound.screen().find("harvest_" + i).orElseThrow()).toList();
            require(menu.slot(outputs.get(0)).y == menu.slot(outputs.get(2)).y
                && menu.slot(outputs.get(0)).x == menu.slot(outputs.get(3)).x
                && menu.slot(outputs.get(3)).y > menu.slot(outputs.get(0)).y
                && menu.slot(outputs.get(8)).y > menu.slot(outputs.get(3)).y
                && outputs.stream().noneMatch(slot -> menu.slot(slot).mayPlace(new ItemStack(Items.WHEAT))),
                "The nine harvest outputs form a three-by-three grid and reject insertion");
            var slots = java.util.stream.IntStream.range(0, 4).mapToObj(i ->
                (zone.moddev.mc.lib.gui.model.elements.ItemSlotElement) menu.bound.screen().find("seed_" + i).orElseThrow()).toList();
            require(menu.slot(slots.get(0)).x < menu.slot(slots.get(1)).x
                && menu.slot(slots.get(0)).y == menu.slot(slots.get(1)).y
                && menu.slot(slots.get(2)).x == menu.slot(slots.get(0)).x
                && menu.slot(slots.get(2)).y > menu.slot(slots.get(0)).y,
                "Native seed slots form a two-by-two grid in row-major order");
            var settings = (AgriSettings) menu.bound.screen().find("settings").orElseThrow();
            var button = (CycleButtonElement) menu.bound.screen().find("planting_mode").orElseThrow();
            var fertilizer = (CycleButtonElement) menu.bound.screen().find("fertilizing_mode").orElseThrow();
            var leftTabs = menu.layout().drawers().stream().filter(drawer -> drawer.placement().edge() == Edge.LEFT
                && drawer.placement().anchor() == DrawerPlacement.Anchor.TOP)
                .sorted(Comparator.comparingInt(drawer -> drawer.placement().order()))
                .limit(3).map(BuiltinDrawers::featureType).toList();
            require(leftTabs.equals(List.of(BuiltinDrawers.INFO, BuiltinDrawers.STATS, AgriSettings.ID)),
                "The first three tabs on the left are Info, Stats, and Settings");
            require(!menu.visible(button) && !menu.visible(fertilizer), "Both mode controls start hidden");
            var open = AgriSettings.SET_OPEN;
            var opened = menu.elementAction(player, new ScreenNetwork.Widget(menu.containerId, menu.sessionId(),
                menu.elements.indexOf(settings), 1, open.id(), open.encode(player.registryAccess(), true)));
            require(opened.accepted() && menu.visible(button) && menu.visible(fertilizer)
                && menu.layout().bounds(button).right() <= menu.layout().bounds(settings).x()
                && menu.layout().bounds(button).bottom() <= menu.layout().bounds(fertilizer).y(),
                "Opening Settings reveals planting above fertilizing in the left drawer");
            var seeds = (zone.moddev.mc.lib.gui.model.containers.InventoryElement)
                menu.bound.screen().find("seed").orElseThrow();
            for (int request = 1; request <= 3; request++) {
                var action = ElementActions.CLICK;
                var reply = menu.elementAction(player, new ScreenNetwork.Widget(menu.containerId, menu.sessionId(),
                    menu.elements.indexOf(button), request + 1, action.id(), action.encode(player.registryAccess(),
                        new ElementActions.Click(0, 0, 0))));
                require(reply.accepted() && AgriBrainProcessor.from(machine).plantingMode() == request % 3,
                    "The authorized mode button cycles through all three modes");
                var mode = button.modeFor(button.snapshot(machine).orElseThrow().value()).orElseThrow();
                require(mode.value() == request % 3
                    && mode.label().key().equals("agri.planting.mode." + request % 3)
                    && mode.tooltip().orElseThrow().key().equals("agri.planting.help." + request % 3),
                    "Shared cycle button snapshots select the XML mode label and help");
                var selected = seeds.selectedSlot(AgriBrainScreen.selectedSeedSlot(machine));
                require(request == 1 ? selected.orElseThrow() == slots.get(1) : selected.isEmpty(),
                    "Shared inventory selection highlights only the next non-empty seed in one-at-a-time mode");
            }
            var clickAction = ElementActions.CLICK;
            var ignored = menu.elementAction(player, new ScreenNetwork.Widget(menu.containerId, menu.sessionId(),
                menu.elements.indexOf(button), 5, clickAction.id(), clickAction.encode(player.registryAccess(),
                    new ElementActions.Click(1, 0, 0))));
            require(!ignored.accepted() && AgriBrainProcessor.from(machine).plantingMode() == 0,
                "Right clicks do not change the selected mode");
            // A change outside this menu must be read fresh when cycling, even in the same tick.
            AgriBrainProcessor.from(machine).cyclePlantingMode();
            var next = menu.elementAction(player, new ScreenNetwork.Widget(menu.containerId, menu.sessionId(),
                menu.elements.indexOf(button), 6, clickAction.id(), clickAction.encode(player.registryAccess(),
                    new ElementActions.Click(0, 0, 0))));
            require(next.accepted() && AgriBrainProcessor.from(machine).plantingMode() == 2,
                "The shared cycle uses current server state, not an old menu snapshot");
            var fertilized = menu.elementAction(player, new ScreenNetwork.Widget(menu.containerId, menu.sessionId(),
                menu.elements.indexOf(fertilizer), 7, clickAction.id(), clickAction.encode(player.registryAccess(),
                    new ElementActions.Click(0, 0, 0))));
            var closed = menu.elementAction(player, new ScreenNetwork.Widget(menu.containerId, menu.sessionId(),
                menu.elements.indexOf(settings), 8, open.id(), open.encode(player.registryAccess(), false)));
            require(fertilized.accepted() && closed.accepted() && !menu.visible(button) && !menu.visible(fertilizer)
                && fertilizer.modeFor(fertilizer.snapshot(machine).orElseThrow().value()).orElseThrow().value() == 1
                && AgriBrainProcessor.from(machine).plantingMode() == 2,
                "Closing Settings hides both controls and preserves their independent selections");
        } finally {
            menu.removed(player);
            player.containerMenu = previous;
        }
        h.succeed();
    }
}

package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import java.util.ArrayList;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.FarmlandWaterManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import zone.moddev.mc.lib.gui.model.elements.UpgradeSlotElement;
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;
import zone.moddev.mc.lib.gui.model.elements.WorkingAreaButtonElement;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriBrainProcessor;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.client.AgriDroneFlight;
import net.modcrafters.minecraft.industrialoverkill.upgrade.MachineUpgrades;

@GameTestHolder("industrial_overkill_agri")
@PrefixGameTestTemplate(false)
public final class AgriBrainTests {
    @GameTest(template = "gym_empty")
    public static void droneTracksChangingWorkTargets(GameTestHelper h) {
        var soil = new BlockPos(10, 63, 8);
        var first = AgriDroneFlight.hoverPoint(soil);
        var second = AgriDroneFlight.hoverPoint(soil.east());
        var third = AgriDroneFlight.hoverPoint(soil.east().south());
        require(first.equals(new Vec3(10.5, 65.72, 8.5)) && first.y == second.y && second.y == third.y,
            "Every target in the field uses the same flight plane across irrigation gaps");
        var flight = new AgriDroneFlight(first, 80);
        require(flight.positionAt(100).equals(first), "A newly visible drone hovers over the active target");
        flight.aimAt(second, 100, 6);
        flight.aimAt(second, 102, 6);
        var halfway = flight.positionAt(103);
        require(halfway.equals(first.lerp(second, .5)), "Repeated frames keep the flight moving");
        flight.aimAt(third, 103, 6);
        require(flight.positionAt(103).equals(halfway), "A new target preserves the in-flight position");
        require(flight.positionAt(109).equals(third) && flight.positionAt(130).equals(third),
            "The drone reaches the replacement target and hovers there");
        require(flight.beginEmission(130) && !flight.beginEmission(130) && flight.beginEmission(131),
            "Rendering extra frames never multiplies the scanner's particle rate");
        flight.returnTo(first, 140, .05, 20);
        flight.returnTo(first, 145, .05, 20);
        require(flight.positionAt(150).equals(third.lerp(first, .5)),
            "The drone crosses both axes smoothly between server snapshots");
        flight.returnTo(first, 150, .55, 5);
        require(flight.positionAt(150).equals(third.lerp(first, .5)) && flight.positionAt(155).equals(first),
            "A faster return keeps the current position and arrives on the revised schedule");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void droneTurnsThroughRowChangesAndFacesHome(GameTestHelper h) {
        var first = new Vec3(1.5, 65.72, 2.5);
        var east = new Vec3(1, 0, 0);
        var south = new Vec3(0, 0, 1);
        var west = east.scale(-1);
        var endOfRow = first.add(east.scale(2));
        var nextRow = endOfRow.add(south);
        var flight = new AgriDroneFlight(first, 0);
        require(droneNose(flight, 0, east).dot(east) > .999, "The drone starts facing along the row");
        flight.aimAt(endOfRow, 10, 6);
        droneNose(flight, 20, east);
        flight.aimAt(nextRow, 20, 6);
        var turning = droneNose(flight, 21, west);
        require(turning.dot(east) > .8 && turning.dot(south) > .4, "The drone turns smoothly into the row change");
        require(droneNose(flight, 23, west).dot(south) > .999, "The nose follows the flight between rows");
        require(droneNose(flight, 30, west).dot(west) > .999, "The drone faces down the reversed row at rest");

        var home = first.subtract(nextRow).normalize();
        flight.returnTo(first, 40, .1, 20);
        require(droneNose(flight, 43, home).dot(home) > .999, "The diagonal return points at the first block");
        flight.returnTo(first, 49, .55, 5);
        require(droneNose(flight, 50, home).dot(home) > .999, "A new return snapshot preserves the heading");
        require(droneNose(flight, 54, home).dot(home) > .999, "The return heading holds through touchdown");
        flight.aimAt(first, 54, 6);
        require(droneNose(flight, 60, east).dot(east) > .999, "The next pass starts facing the first row");
        h.succeed();
    }

    private static Vec3 droneNose(AgriDroneFlight flight, double time, Vec3 hoverHeading) {
        return new Vec3(0, 0, -1).yRot((float) Math.toRadians(flight.yawDegreesAt(time, hoverHeading)));
    }

    @GameTest(template = "gym_empty")
    public static void droneLeansIntoFlightAndLevelsAtEachBlock(GameTestHelper h) {
        var start = new Vec3(2.5, 65.72, 2.5);
        var east = new Vec3(1, 0, 0);
        var south = new Vec3(0, 0, 1);
        var up = new Vec3(0, 1, 0);
        var corner = start.add(east);
        var nextRow = corner.add(south);
        var flight = new AgriDroneFlight(start, 0);
        flight.aimAt(corner, 10, 6);
        require(droneUp(flight, 10, east).distanceTo(up) < 1e-5, "Takeoff starts level");
        double departureLean = droneUp(flight, 11, east).dot(east);
        var midFlight = droneUp(flight, 13, east);
        double arrivalLean = droneUp(flight, 15, east).dot(east);
        require(midFlight.x > .1 && midFlight.y > .98 && Math.abs(midFlight.z) < 1e-5
            && departureLean > 0 && departureLean < midFlight.x && arrivalLean > 0 && arrivalLean < midFlight.x,
            "The leading edge dips gently in flight and eases up on approach");
        require(droneUp(flight, 16, east).distanceTo(up) < 1e-5, "The drone hovers level");

        flight.aimAt(nextRow, 20, 6);
        require(droneUp(flight, 21, east.scale(-1)).dot(south) > 0, "The lean follows the row crossing while yaw turns");
        var home = start.subtract(nextRow).normalize();
        flight.returnTo(start, 40, .1, 20);
        var diagonal = droneUp(flight, 45, home);
        flight.returnTo(start, 45, .3, 15);
        require(diagonal.dot(home) > .1 && droneUp(flight, 45, home).distanceTo(diagonal) < 1e-5,
            "The return leans toward the first block across tracking updates");
        require(droneUp(flight, 60, home).distanceTo(up) < 1e-5, "The drone levels out at the first block");
        h.succeed();
    }

    private static Vec3 droneUp(AgriDroneFlight flight, double time, Vec3 hoverHeading) {
        return new Vec3(flight.orientationAt(time, hoverHeading).transform(new org.joml.Vector3f(0, 1, 0)));
    }

    @GameTest(template = "gym_empty")
    public static void droneDocksAndTakesOffFromItsCurrentPosition(GameTestHelper h) {
        var controller = new BlockPos(4, 64, 4);
        var dock = AgriDroneFlight.dockPoint(controller);
        var field = AgriDroneFlight.hoverPoint(controller.below().east(6));
        require(dock.y - 2.75 / 16 == controller.getY() + 1, "The parked lens rests on the roof");
        var flight = new AgriDroneFlight(field, 0);
        flight.landOn(dock, 10);
        var approach = flight.positionAt(16);
        require(approach.x < field.x && approach.x > dock.x && approach.y == field.y,
            "Power loss sends the drone home at flight height");
        flight.landOn(dock, 16);
        var descent = flight.positionAt(30);
        require(descent.x == dock.x && descent.y > dock.y && descent.y < field.y,
            "The drone descends over the center of the machine");
        float earlyCoast = rotorStep(flight, 35);
        float lateCoast = rotorStep(flight, 46);
        require(earlyCoast > lateCoast && lateCoast > 0 && earlyCoast < 45,
            "After touchdown the rotors coast at progressively lower speed");
        require(Math.abs(45 * flight.rotorSpeedAt(35.5) - earlyCoast) < .001
            && Math.abs(45 * flight.rotorSpeedAt(46.5) - lateCoast) < .001 && flight.rotorSpeedAt(52) == 0,
            "The hum follows the visible blade speed through the coast to silence");
        float stopped = flight.rotorDegreesAt(52);
        require(flight.positionAt(60).equals(dock) && flight.rotorDegreesAt(60) == stopped,
            "The parked drone and its rotors stay still until work resumes");

        require(!flight.readyForWork(field, 60) && flight.rotorDegreesAt(60) == stopped,
            "Takeoff starts at the stopped rotor angle with the scanner off");
        require(flight.positionAt(68).equals(dock) && rotorStep(flight, 61) > 0
            && rotorStep(flight, 66) > rotorStep(flight, 61), "The rotors accelerate while parked");
        require(flight.rotorSpeedAt(60) == 0 && flight.rotorSpeedAt(64) == .5 && flight.rotorSpeedAt(68) == 1,
            "Motor sound reaches cruising speed at liftoff");
        var lift = flight.positionAt(70);
        require(lift.x == dock.x && lift.y > dock.y && lift.y < field.y,
            "The drone rises only after spin-up finishes");
        var outbound = flight.positionAt(80);
        flight.landOn(dock, 80);
        require(flight.positionAt(80).equals(outbound), "Pausing during takeoff starts a return from midair");
        var inbound = flight.positionAt(83);
        require(!flight.readyForWork(field, 83) && flight.positionAt(83).equals(inbound),
            "Restoring power during the approach reverses course without a position jump");
        require(flight.readyForWork(field, 110) && flight.positionAt(110).equals(field),
            "Field flight resumes at the saved work position");

        var first = field.add(-2, 0, 2);
        flight.returnTo(first, 111, .5, 20);
        var diagonal = flight.positionAt(118);
        flight.landOn(dock, 118);
        require(flight.positionAt(118).equals(diagonal), "A paused diagonal flight also returns from its current position");
        var parked = AgriDroneFlight.parked(dock, 200);
        require(rotorStep(parked, 201) == 0, "An already parked drone loads at rest");
        parked.readyForWork(field, 210);
        float angle = parked.rotorDegreesAt(214);
        parked.landOn(dock, 214);
        require(parked.rotorDegreesAt(214) == angle && parked.positionAt(218).equals(dock),
            "Pausing mid-spin-up leaves the drone on the dock and preserves its blade angle");
        float coasting = rotorStep(parked, 216);
        parked.readyForWork(field, 217);
        require(coasting > 0 && rotorStep(parked, 218) > coasting && parked.positionAt(218).equals(dock),
            "Restoring power reverses the coast into a smooth spin-up before liftoff");
        h.succeed();
    }

    private static float rotorStep(AgriDroneFlight flight, double tick) {
        return net.minecraft.util.Mth.wrapDegrees(flight.rotorDegreesAt(tick + 1) - flight.rotorDegreesAt(tick));
    }

    @GameTest(template = "gym_empty")
    public static void landingBayReservesTheSpaceAboveTheController(GameTestHelper h) {
        var level = h.getLevel();
        var base = h.absolutePos(new BlockPos(2, 2, 2));
        var above = base.above();
        var brain = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("industrial_overkill:agri_brain"));
        var context = new net.minecraft.world.item.context.DirectionalPlaceContext(level, base,
            net.minecraft.core.Direction.DOWN, new ItemStack(brain), net.minecraft.core.Direction.UP);
        level.setBlockAndUpdate(above, Blocks.STONE.defaultBlockState());
        require(brain.getStateForPlacement(context) == null, "Placing the machine requires a clear landing bay");
        level.setBlockAndUpdate(base, brain.defaultBlockState());
        require(level.getBlockState(above).is(Blocks.STONE), "An older controller preserves an existing obstruction");
        level.removeBlock(above, false);
        level.getBlockState(base).tick(level, base, level.random);
        var dock = level.getBlockState(above);
        require(dock.getBlock() instanceof net.modcrafters.minecraft.industrialoverkill.agri.AgriDockBlock
            && !dock.canBeReplaced(), "The cleared bay reserves its block even without power");
        var clearance = net.minecraft.world.phys.shapes.Shapes.box(0, 0, 0, 1, .5, 1);
        require(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(dock.getCollisionShape(level, above),
            clearance, net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),
            "The entire roof has collision up to exactly one and a half blocks");
        level.removeBlock(base, false);
        require(level.isEmptyBlock(above), "Removing the machine frees its landing bay");
        var placed = brain.getStateForPlacement(context);
        require(placed != null, "The controller fits when the docking space is clear");
        level.setBlockAndUpdate(base, placed);
        require(level.getBlockState(above).equals(dock), "A newly placed machine immediately owns its dock");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void legacyCombinedUpgradeInventoryMigratesOnce(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("industrial_overkill:agri_brain")).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        var registries = h.getLevel().registryAccess();
        var old = new zone.moddev.mc.lib.api.capabilities.ItemStorage(11);
        for (int i = 0; i < 11; i++) {
            old.slotId(i, "upgrades/" + i);
            old.setStackInSlot(i, i == 4 ? new ItemStack(MachineUpgrades.GEARBOX.get(), 8)
                : i == 5 ? new ItemStack(MachineUpgrades.CAPACITOR.get(), 8)
                : new ItemStack(MachineUpgrades.RANGE.get()));
        }
        var saved = machine.saveWithoutMetadata(registries);
        var named = saved.getCompound("items").getCompound("Inventories");
        named.put("upgrades", old.serializeNBT(registries));
        named.remove("mmdlib_gearbox");
        named.remove("mmdlib_capacitor");
        for (int pass = 0; pass < 2; pass++) {
            machine.loadWithComponents(saved, registries);
            require(machine.screenInventory("mmdlib_gearbox").getStackInSlot(0).getCount() == 8
                && machine.screenInventory("mmdlib_capacitor").getStackInSlot(0).getCount() == 8,
                "Old component stacks move into library storage exactly once");
            require(machine.inventoryContents("upgrades").size() == 9
                && machine.inventoryContents("upgrades").stream().allMatch(stack -> stack.is(MachineUpgrades.RANGE.get()) && stack.getCount() == 1)
                && machine.items.recoveryContents().isEmpty(), "All nine range upgrades survive migration and reload");
            saved = machine.saveWithoutMetadata(registries);
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void sharedRangeCountsSocketsAndKeepsLegacyItemsUsable(GameTestHelper h) {
        var machine = placeTillingMachine(h);
        var item = MachineUpgrades.RANGE.get();
        require(BuiltInRegistries.ITEM.getKey(item).toString().equals("mmdlib:range_upgrade"),
            "New range modules belong to MMDLib");
        var old = BuiltInRegistries.ITEM.get(ResourceLocation.parse("industrial_overkill:range_upgrade"));
        machine.screenInventory("upgrades").setStackInSlot(0, new ItemStack(old));
        require(AgriBrainProcessor.from(machine).rangeUpgrades() == 1
            && AgriBrainProcessor.from(machine).workingArea().size() == 25,
            "Saved pre-library range items retain the same effect");
        require(zone.moddev.mc.lib.machine.feature.RangeUpgrades.count(
            java.util.Collections.nCopies(12, new ItemStack(item, 64))) == 9,
            "Range counts occupied sockets rather than stack sizes, capped at nine");
        require(!new ItemStack(item).isDamageableItem(), "Range upgrades never wear out");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void scanningControllerConsumesEnergyAndGatesUpgradeSlots(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("industrial_overkill:agri_brain")).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        require(machine.screenAutomation().groups().stream().map(g -> g.id()).collect(java.util.stream.Collectors.toSet())
            .equals(java.util.Set.of("energy", "water", "hoe", "seeds", "output", "fertilizer", "filtered_fertilizer", "pellets")),
            "All inputs and outputs are configurable without exposing the manual upgrade slots");
        require(machine.fluids.getTanks() == 1 && machine.fluids.getTankCapacity(0) == 4000
            && machine.fluids.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0,
            "The four-bucket tank rejects lava");
        var upgrades = machine.screenInventory("upgrades");
        require(upgrades.getSlots() == 9, "Manual upgrade storage remains bound without an automation group");
        upgrades.setStackInSlot(4, new ItemStack(Items.IRON_INGOT));
        machine.energy.setStored(12345);
        var first = scanTarget(machine, 3, 0);
        h.getLevel().setBlock(first, Blocks.FARMLAND.defaultBlockState(), 3);
        for (int i = 0; i < 40; i++) machine.serverTick();
        require(machine.energy.stored() == 12145, "Forty scan ticks consume 200 FE");
        require(h.getLevel().getBlockState(first).getValue(FarmBlock.MOISTURE) == 0
            && !FarmlandWaterManager.hasBlockWaterTicket(h.getLevel(), first),
            "An empty tank allows scanning without watering the visited soil or the field");
        machine.fluids.fill(new FluidStack(Fluids.WATER, 321), FluidAction.EXECUTE);
        var processor = machine.screenFeatures().findProcessor("agri").orElseThrow();
        processor.setPaused(true);
        var registry = h.getLevel().registryAccess();
        machine.loadWithComponents(machine.saveWithoutMetadata(registry), registry);
        require(machine.fluids.getFluidInTank(0).getAmount() == 321, "A partly filled tank survives reload");
        require(machine.screenInventory("upgrades").getStackInSlot(4).is(Items.IRON_INGOT),
            "Manual upgrade contents survive save/load without automation registration");
        machine.screenInventory("upgrades").setStackInSlot(4, ItemStack.EMPTY);
        require(processor.paused() && machine.energy.stored() == 12145, "Pause and energy survive reload");
        var player = h.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        var previous = player.containerMenu;
        var menu = new MachineMenu(91, player.getInventory(), machine);
        player.containerMenu = menu;
        try {
            // Opening a real menu broadcasts before drawer requests can work.
            // This catches unsupported ordinary snapshot values, even if the screen constructs.
            menu.broadcastChanges();
            var tank = (FluidTankElement) menu.bound.screen().find("tank_0").orElseThrow();
            menu.setCarried(new ItemStack(Items.WATER_BUCKET));
            tank.interact(new ScreenClick(player, menu, tank, 0, false, false, false, 0));
            require(menu.getCarried().is(Items.BUCKET) && machine.fluids.getFluidInTank(0).getAmount() == 1321,
                "The irrigation gauge accepts a bucket of water");
            menu.setCarried(ItemStack.EMPTY);
            var hoeSlot = (zone.moddev.mc.lib.gui.model.elements.ItemSlotElement)
                menu.bound.screen().find("hoe_0").orElseThrow();
            require(menu.slot(hoeSlot).mayPlace(new ItemStack(Items.IRON_HOE))
                && !menu.slot(hoeSlot).mayPlace(new ItemStack(Items.DIRT))
                && menu.slot(hoeSlot).getMaxStackSize() == 1,
                "The main input accepts one hoe and rejects unrelated items");
            var drawer = menu.components().require(BuiltinMenuComponents.UPGRADES);
            var slots = menu.elements.stream().filter(UpgradeSlotElement.class::isInstance)
                .map(UpgradeSlotElement.class::cast).toList();
            require(slots.size() == 11, "Two component and nine general upgrade slots are bound");
            for (var component : new String[]{"gearbox", "capacitor"}) {
                var socket = (UpgradeSlotElement) menu.bound.screen().find(component).orElseThrow();
                require(socket.acceptedItems().location().equals(ResourceLocation.parse(
                    "mmdlib:" + component + "_upgrades")),
                    "The generated " + component + " socket uses the shared admission tag");
            }
            require(slots.stream().noneMatch(s -> menu.slot(s).isActive()), "Closed drawer slots are inactive");
            var open = zone.moddev.mc.lib.gui.model.containers.UpgradesDrawerElement.SET_OPEN;
            var reply = menu.elementAction(player, new zone.moddev.mc.lib.gui.runtime.ScreenNetwork.Widget(
                menu.containerId, menu.sessionId(), menu.elements.indexOf(drawer.element()), 1,
                open.id(), open.encode(registry, true)));
            require(reply.accepted(), "The upgrades open request is accepted");
            require(slots.stream().allMatch(s -> menu.slot(s).isActive()), "Open drawer slots are active");
            require(slots.stream().noneMatch(s -> menu.slot(s).mayPlace(new ItemStack(Items.DIRT))), "Unapproved items are rejected");
            var range = new ItemStack(MachineUpgrades.RANGE.get());
            require(slots.stream().filter(s -> menu.slot(s).mayPlace(range)).count() == 9,
                "Range upgrades fit all nine general slots, but neither component socket");
            require(slots.stream().filter(s -> menu.slot(s).mayPlace(range))
                .allMatch(s -> menu.slot(s).getMaxStackSize(range) == 1),
                "Each general upgrade slot still holds exactly one range item");
            var gearbox = new ItemStack(MachineUpgrades.GEARBOX.get());
            var gearboxSocket = (UpgradeSlotElement) menu.bound.screen().find("gearbox").orElseThrow();
            require(menu.slot(gearboxSocket).mayPlace(gearbox)
                && slots.stream().filter(s -> menu.slot(s).mayPlace(gearbox)).count() == 1
                && menu.slot(gearboxSocket).getMaxStackSize(gearbox) == 8,
                "Up to eight shared gearboxes fit only their dedicated socket");
            var capacitor = new ItemStack(MachineUpgrades.CAPACITOR.get());
            var capacitorSocket = (UpgradeSlotElement) menu.bound.screen().find("capacitor").orElseThrow();
            require(menu.slot(capacitorSocket).mayPlace(capacitor)
                && slots.stream().filter(s -> menu.slot(s).mayPlace(capacitor)).count() == 1
                && menu.slot(capacitorSocket).getMaxStackSize(capacitor) == 8,
                "Up to eight MMDLib capacitors fit only their dedicated socket");
            var legacyRange = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("industrial_overkill:range_upgrade")));
            var legacyGearbox = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("industrial_overkill:gearbox_upgrade")));
            require(slots.stream().filter(s -> menu.slot(s).mayPlace(legacyRange)).count() == 9
                && menu.slot(gearboxSocket).mayPlace(legacyGearbox),
                "Generated sockets still accept saved legacy range and gearbox items through the shared tags");
            var redstone = menu.components().require(BuiltinMenuComponents.REDSTONE);
            redstone.action(player, new zone.moddev.mc.lib.gui.runtime.RedstoneNetwork.Action(
                menu.containerId, menu.sessionId(), 1, redstone.revision(),
                zone.moddev.mc.lib.gui.runtime.RedstoneMenu.TOGGLE, 0, 0, 0));
            require(redstone.open(), "The redstone open request is accepted");
            require(slots.stream().noneMatch(s -> menu.slot(s).isActive()), "Another right drawer closes upgrades");
            var automation = menu.components().require(BuiltinMenuComponents.AUTOMATION);
            automation.action(player, new zone.moddev.mc.lib.gui.runtime.AutomationNetwork.Action(
                menu.containerId, menu.sessionId(), 1, "select", 0, 0, 0, 0, "", 0, ""));
            require(automation.open() && !redstone.open(), "The side configuration request opens and closes redstone");
            var area = (WorkingAreaButtonElement) menu.bound.screen().find("working_area").orElseThrow();
            var data = menu.elementStates().get(area, WorkingAreaButtonElement.AREA);
            require(data.getInt("color") == 0x59ff59 && machine.visualizerStyle().workColor() == 0x55ff88,
                "The machine XML supplies range and work visualizer colors");
            require(AgriBrainProcessor.from(machine).droneSoil() == null, "A paused machine has no active drone");
            require(data.getDouble("maxX") - data.getDouble("minX") == 3
                && data.getDouble("maxZ") - data.getDouble("minZ") == 3, "The base area snapshot covers three by three crop positions");
            var facing = machine.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            require((data.getDouble("minX") + data.getDouble("maxX")) / 2 == pos.getX() + .5 - 2 * facing.getStepX()
                && (data.getDouble("minZ") + data.getDouble("maxZ")) / 2 == pos.getZ() + .5 - 2 * facing.getStepZ(),
                "The synchronized field is centered behind the machine");
        } finally {
            menu.removed(player);
            player.containerMenu = previous;
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void workingAreaStaysBehindEveryFacing(GameTestHelper h) {
        var area = new net.modcrafters.minecraft.industrialoverkill.agri.AgriBrainProcessor().workingArea();
        var origin = new BlockPos(10, 64, 20);
        for (var facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var bounds = area.bounds(origin, facing);
            require(bounds.getCenter().x == origin.getX() + .5 - 2 * facing.getStepX()
                && bounds.getCenter().z == origin.getZ() + .5 - 2 * facing.getStepZ(),
                "Field stays behind the " + facing + " facing controller");
            require(!bounds.intersects(new net.minecraft.world.phys.AABB(origin)),
                "The field excludes the controller");
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void rangeUpgradesRefreshAreaSnapshotsWhilePaused(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(
            "industrial_overkill:agri_brain")).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        var processor = (AgriBrainProcessor) machine.screenFeatures().findProcessor("agri").orElseThrow();
        processor.setPaused(true);
        var player = h.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        var previous = player.containerMenu;
        var menu = new MachineMenu(92, player.getInventory(), machine);
        player.containerMenu = menu;
        int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        try {
            assertRange(machine, processor, menu, player, 0);
            for (int count = 1; count <= slots.length; count++) {
                machine.screenInventory("upgrades").setStackInSlot(slots[count - 1],
                    new ItemStack(MachineUpgrades.RANGE.get()));
                assertRange(machine, processor, menu, player, count);
            }
            var snapshot = machine.inventoryContents("upgrades");
            snapshot.get(0).setCount(0);
            require(machine.screenInventory("upgrades").getStackInSlot(0).getCount() == 1,
                "Processor inventory snapshots cannot mutate stored upgrades");
            // Even forced legacy contents in component sockets cannot add range.
            machine.screenInventory("mmdlib_gearbox").setStackInSlot(0, new ItemStack(MachineUpgrades.RANGE.get()));
            machine.screenInventory("mmdlib_capacitor").setStackInSlot(0, new ItemStack(MachineUpgrades.RANGE.get()));
            assertRange(machine, processor, menu, player, 9);
            machine.screenInventory("mmdlib_gearbox").setStackInSlot(0, ItemStack.EMPTY);
            machine.screenInventory("mmdlib_capacitor").setStackInSlot(0, ItemStack.EMPTY);
            var registry = h.getLevel().registryAccess();
            machine.loadWithComponents(machine.saveWithoutMetadata(registry), registry);
            assertRange(machine, processor, menu, player, 9);
            for (int count = 8; count >= 0; count--) {
                machine.screenInventory("upgrades").setStackInSlot(slots[count], ItemStack.EMPTY);
                assertRange(machine, processor, menu, player, count);
            }
            require(processor.paused(), "Range updates never require resuming or ticking the processor");
        } finally {
            menu.removed(player);
            player.containerMenu = previous;
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void scannerVisitsEveryBlockAndPreservesPausedProgress(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(
            "industrial_overkill:agri_brain")).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        machine.energy.setStored(100000);
        var processor = (AgriBrainProcessor) machine.screenFeatures().findProcessor("agri").orElseThrow();
        var first = scanTarget(machine, 3, 0);
        h.getLevel().setBlock(first, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var before = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (int index = 0; index < 9; index++) {
            var target = scanTarget(machine, 3, index);
            h.getLevel().setBlock(target, (index % 2 == 0 ? net.minecraft.world.level.block.Blocks.STONE
                : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(), 3);
            before.put(target, h.getLevel().getBlockState(target));
        }
        var visited = new java.util.HashSet<BlockPos>();
        int emptyVisit = AgriBrainProcessor.TRAVEL_TICKS + AgriBrainProcessor.PHASE_TICKS;
        for (int tick = 1; tick <= 9 * emptyVisit; tick++) {
            machine.serverTick();
            int index = Math.min(8, tick / emptyVisit);
            var expected = scanTarget(machine, 3, index);
            require(expected.equals(processor.droneSoil()) && processor.returnProgress() == 0,
                "An idle plot takes one travel leg and one maturity check");
            int row = index / 3;
            var travel = row % 2 == 0 ? machine.facing().getClockWise() : machine.facing().getCounterClockWise();
            require(processor.scanDirection() == travel, "The beam sweeps with travel on each row");
            visited.add(processor.droneSoil());
        }
        require(machine.workingBlocks().isEmpty(), "The drone is the only source of work particles");
        require(visited.equals(before.keySet()), "Every block is visited, including air");
        before.forEach((target, block) -> require(h.getLevel().getBlockState(target).equals(block),
            "Scanning leaves unsuitable target blocks unchanged"));
        var last = scanTarget(machine, 3, 8);
        var registries = h.getLevel().registryAccess();
        for (int tick = 1; tick <= 40; tick++) {
            machine.serverTick();
            require(last.equals(processor.droneSoil()) && first.equals(processor.firstSoil())
                && processor.returnProgress() == tick / 40.0, "The return crosses the field corner to corner");
            if (tick == 20) {
                processor.setPaused(true);
                machine.serverTick();
                machine.loadWithComponents(machine.saveWithoutMetadata(registries), registries);
                require(processor.droneSoil() == null && processor.returnProgress() == .5,
                    "Pausing and reloading preserve the halfway point");
                processor.setPaused(false);
            }
        }
        machine.serverTick();
        require(first.equals(processor.droneSoil()) && processor.returnProgress() == 0,
            "Scanning restarts at the first block after landing over it");
        require(machine.energy.stored() == 100000 - (9 * emptyVisit + 41) * 5,
            "Only powered scan and travel ticks consume five FE");

        processor.setPaused(true);
        for (int tick = 0; tick < 30; tick++) machine.serverTick();
        require(processor.droneSoil() == null, "Pause clears the active drone");
        machine.loadWithComponents(machine.saveWithoutMetadata(registries), registries);
        processor.setPaused(false);
        for (int tick = 0; tick < AgriBrainProcessor.PHASE_TICKS - 1; tick++) {
            machine.serverTick();
            require(first.equals(processor.droneSoil()),
                "Pause and save/reload retain the remaining time on the current block");
        }
        machine.serverTick();
        require(scanTarget(machine, 3, 1).equals(processor.droneSoil()),
            "Resume advances after the retained countdown");

        machine.screenInventory("upgrades").setStackInSlot(0, new ItemStack(MachineUpgrades.RANGE.get()));
        for (int tick = 1; tick <= 25 * emptyVisit; tick++) {
            machine.serverTick();
            require(scanTarget(machine, 5, Math.min(24, tick / emptyVisit)).equals(processor.droneSoil()),
                "Growing the range restarts the scan and covers the full expanded area");
        }
        for (int tick = 0; tick < 40; tick++) machine.serverTick();
        require(processor.returnProgress() == .5, "A five-wide field takes twice as long to cross");
        machine.screenInventory("upgrades").setStackInSlot(0, ItemStack.EMPTY);
        machine.serverTick();
        require(first.equals(processor.droneSoil()) && processor.returnProgress() == 0,
            "Shrinking the range immediately restarts within the new bounds");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void droneWatersEachMoistureLevelAtItsOwnCost(GameTestHelper h) {
        var machine = placeTillingMachine(h);
        var sounds = new ArrayList<PlayLevelSoundEvent.AtPosition>();
        Consumer<PlayLevelSoundEvent.AtPosition> listen = event -> {
            var sound = event.getSound().value();
            if (event.getLevel() == h.getLevel() && (sound == IndustrialOverkill.AGRI_WATER_SPRINKLE.get()
                || sound == IndustrialOverkill.AGRI_WATER_SPLASH.get())) sounds.add(event);
        };
        NeoForge.EVENT_BUS.addListener(listen);
        try {
            checkWateringVisits(h, machine);
        } finally {
            NeoForge.EVENT_BUS.unregister(listen);
        }
        require(sounds.size() == 7, "Only the seven paid watering visits make a sound");
        for (int moisture = 0; moisture < sounds.size(); moisture++) {
            var event = sounds.get(moisture);
            var expected = moisture < 4 ? IndustrialOverkill.AGRI_WATER_SPLASH : IndustrialOverkill.AGRI_WATER_SPRINKLE;
            require(event.getSound().value() == expected.get()
                && event.getPosition().equals(Vec3.atBottomCenterOf(scanTarget(machine, 3, moisture).above())),
                "The watering sound matches the dose and originates at the watered soil");
            if (moisture > 0) require(event.getOriginalVolume() < sounds.get(moisture - 1).getOriginalVolume()
                && event.getOriginalPitch() > sounds.get(moisture - 1).getOriginalPitch(),
                "Smaller doses sound softer and lighter");
        }
        h.succeed();
    }

    private static void checkWateringVisits(GameTestHelper h, DefinedMachineEntity machine) {
        var level = h.getLevel();
        var dry = Blocks.FARMLAND.defaultBlockState();
        var watered = dry.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
        int[] costs = {100, 86, 71, 57, 43, 29, 14, 0};
        for (int moisture = 0; moisture < costs.length; moisture++) {
            level.setBlock(scanTarget(machine, 3, moisture), dry.setValue(FarmBlock.MOISTURE, moisture), 3);
        }
        var last = scanTarget(machine, 3, 8);
        level.setBlock(last, dry, 3);
        // The first eight plots cost 400 mB, leaving the last plot one mB short.
        int remaining = 499;
        machine.fluids.fill(new FluidStack(Fluids.WATER, remaining), FluidAction.EXECUTE);
        var first = scanTarget(machine, 3, 0);
        var processor = AgriBrainProcessor.from(machine);
        processor.setPaused(true);
        machine.serverTick();
        processor.setPaused(false);
        machine.energy.setStored(0);
        machine.serverTick();
        require(machine.fluids.getFluidInTank(0).getAmount() == remaining && level.getBlockState(first).equals(dry),
            "Pausing and losing power preserve dry soil and tank contents");

        machine.energy.setStored(100000);
        int visit = AgriBrainProcessor.TRAVEL_TICKS + AgriBrainProcessor.PHASE_TICKS;
        for (int moisture = 0; moisture < costs.length; moisture++) {
            for (int tick = 1; tick < visit; tick++) machine.serverTick();
            require(machine.fluids.getFluidInTank(0).getAmount() == remaining,
                "Water is charged at the end of the hover");
            machine.serverTick();
            remaining -= costs[moisture];
            require(level.getBlockState(scanTarget(machine, 3, moisture)).equals(watered)
                && machine.fluids.getFluidInTank(0).getAmount() == remaining,
                "Watering from moisture " + moisture + " costs " + costs[moisture] + " mB");
        }
        for (int tick = 0; tick < visit; tick++) machine.serverTick();
        require(last.equals(processor.droneSoil()) && level.getBlockState(last).equals(dry)
            && machine.fluids.getFluidInTank(0).getAmount() == 99,
            "An underfunded visit keeps all remaining water and leaves the soil dry");
        for (int tick = 0; tick < 41; tick++) machine.serverTick();
        require(first.equals(processor.droneSoil()) && machine.fluids.getFluidInTank(0).getAmount() == 99,
            "The diagonal return and revisiting fully watered farmland consume no water");
    }

    @GameTest(template = "gym_empty")
    public static void poweredFieldSlowsDryingAtEveryMoistureLevel(GameTestHelper h) {
        var machine = placeTillingMachine(h);
        var level = h.getLevel();
        var upgrades = machine.screenInventory("upgrades");
        for (int slot = 0; slot < 3; slot++) upgrades.setStackInSlot(slot, new ItemStack(MachineUpgrades.RANGE.get()));
        machine.serverTick();
        var dry = Blocks.FARMLAND.defaultBlockState();
        BlockPos outer = null;
        for (int moisture = 0; moisture <= FarmBlock.MAX_MOISTURE; moisture++) {
            int retained = 0;
            var soilState = dry.setValue(FarmBlock.MOISTURE, moisture);
            var dried = moisture == 0 ? Blocks.DIRT.defaultBlockState() : dry.setValue(FarmBlock.MOISTURE, moisture - 1);
            for (int i = 0; i < 8; i++) {
                var soil = scanTarget(machine, 9, i);
                level.setBlock(soil.above(3), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(soil, soilState, 3);
                soilState.randomTick(level, soil, level.random);
                if (level.getBlockState(soil).equals(soilState)) {
                    retained++;
                    if (i < 2) outer = soil;
                } else {
                    require(level.getBlockState(soil).equals(dried), "A drying tick follows the vanilla transition");
                }
            }
            require(retained == 7, "Seven out of eight plots keep their exact moisture at level " + moisture);
        }
        require(outer != null, "The expanded field includes a protected outer plot");
        // Vanilla checks water in the soil layer or the layer directly above it.
        var water = outer.relative(machine.facing().getOpposite()).above();
        level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(outer, dry, 3);
        dry.randomTick(level, outer, level.random);
        require(level.getBlockState(outer).getValue(FarmBlock.MOISTURE) == FarmBlock.MAX_MOISTURE,
            "Nearby water fully hydrates farmland even on a retention tick");
        level.setBlock(water, Blocks.AIR.defaultBlockState(), 3);

        var damp = dry.setValue(FarmBlock.MOISTURE, 4);
        var drier = dry.setValue(FarmBlock.MOISTURE, 3);
        var processor = AgriBrainProcessor.from(machine);
        processor.setPaused(true);
        level.setBlock(outer, damp, 3);
        damp.randomTick(level, outer, level.random);
        require(level.getBlockState(outer).equals(drier), "A paused field dries normally");
        processor.setPaused(false);
        machine.energy.setStored(0);
        machine.serverTick();
        level.setBlock(outer, damp, 3);
        damp.randomTick(level, outer, level.random);
        require(level.getBlockState(outer).equals(drier), "An unpowered field dries normally");
        machine.energy.setStored(10);
        machine.serverTick();
        level.setBlock(outer, damp, 3);
        damp.randomTick(level, outer, level.random);
        require(level.getBlockState(outer).equals(damp), "Resuming restores retention below full moisture");
        for (int slot = 0; slot < 3; slot++) upgrades.setStackInSlot(slot, ItemStack.EMPTY);
        machine.serverTick();
        damp.randomTick(level, outer, level.random);
        require(level.getBlockState(outer).equals(drier), "Shrinking the field releases its outer plots");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void tillsOnlyExposedDirtAndGrassAtTheScanCadence(GameTestHelper h) {
        var machine = placeTillingMachine(h);
        machine.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var level = h.getLevel();
        var blocks = new net.minecraft.world.level.block.Block[]{
            net.minecraft.world.level.block.Blocks.DIRT,
            net.minecraft.world.level.block.Blocks.GRASS_BLOCK,
            net.minecraft.world.level.block.Blocks.DIRT,
            net.minecraft.world.level.block.Blocks.DIRT,
            net.minecraft.world.level.block.Blocks.COARSE_DIRT,
            net.minecraft.world.level.block.Blocks.DIRT_PATH,
            net.minecraft.world.level.block.Blocks.ROOTED_DIRT,
            net.minecraft.world.level.block.Blocks.FARMLAND,
            net.minecraft.world.level.block.Blocks.STONE
        };
        for (int i = 0; i < blocks.length; i++) {
            var soil = scanTarget(machine, 3, i);
            level.setBlock(soil.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(soil, blocks[i].defaultBlockState(), 3);
        }
        level.setBlock(scanTarget(machine, 3, 2).above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(scanTarget(machine, 3, 3).above(), net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(), 3);
        var hydrated = net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState()
            .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7);
        level.setBlock(scanTarget(machine, 3, 7), hydrated, 3);
        for (int tick = 0; tick < 11; tick++) machine.serverTick();
        require(level.getBlockState(scanTarget(machine, 3, 0)).equals(hydrated),
            "The first scan visit tills and waters exposed dirt after the hover");
        for (int tick = 0; tick < 10; tick++) machine.serverTick();
        require(level.getBlockState(scanTarget(machine, 3, 1)).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK),
            "The next block is not tilled before its maturity check finishes");
        var processor = machine.screenFeatures().findProcessor("agri").orElseThrow();
        processor.setPaused(true);
        for (int tick = 0; tick < 40; tick++) machine.serverTick();
        require(level.getBlockState(scanTarget(machine, 3, 1)).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK),
            "Pause prevents world changes");
        processor.setPaused(false);
        machine.serverTick();
        require(level.getBlockState(scanTarget(machine, 3, 1)).is(net.minecraft.world.level.block.Blocks.FARMLAND),
            "The next turn tills exposed grass");
        for (int tick = 0; tick < 77; tick++) machine.serverTick();
        for (int i = 2; i < blocks.length; i++) {
            require(level.getBlockState(scanTarget(machine, 3, i)).is(blocks[i]),
                "Blocked soil and unsupported blocks remain unchanged at index " + i);
        }
        require(level.getBlockState(scanTarget(machine, 3, 7)).equals(hydrated),
            "Existing farmland keeps its moisture");
        require(level.getBlockState(scanTarget(machine, 3, 3).above()).is(net.minecraft.world.level.block.Blocks.SHORT_GRASS),
            "Tilling does not remove vegetation");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void tillingHonorsToolAndPlacementCancellation(GameTestHelper h) {
        var machine = placeTillingMachine(h);
        var first = scanTarget(machine, 3, 0);
        var second = scanTarget(machine, 3, 1);
        for (var soil : java.util.List.of(first, second)) {
            h.getLevel().setBlock(soil.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(soil, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
        }
        var toolCalls = new java.util.concurrent.atomic.AtomicInteger();
        var placeCalls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BlockToolModificationEvent> tool = event -> {
            if (event.getLevel() == h.getLevel() && event.getPos().equals(first)) {
                toolCalls.incrementAndGet();
                event.setCanceled(true);
            }
        };
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> placement = event -> {
            if (event.getLevel() == h.getLevel() && event.getPos().equals(second)) {
                placeCalls.incrementAndGet();
                event.setCanceled(true);
            }
        };
        var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        bus.addListener(tool);
        bus.addListener(placement);
        try {
            for (int tick = 0; tick < 22; tick++) machine.serverTick();
            require(toolCalls.get() == 1 && placeCalls.get() == 1,
                "One hoe attempt per visited block reaches the normal cancellation hooks");
            require(h.getLevel().getBlockState(first).is(net.minecraft.world.level.block.Blocks.DIRT)
                && h.getLevel().getBlockState(second).is(net.minecraft.world.level.block.Blocks.DIRT),
                "Denied tool use and placement both leave the original dirt intact");
            require(machine.screenInventory("hoe").getStackInSlot(0).getDamageValue() == 0,
                "Cancelled tilling does not damage the installed hoe");
            require(machine.energy.stored() == 100000 - 22 * 5
                && machine.energy.used().longValueExact() == 22 * 5,
                "Cancelled tilling charges only scanning, including the energy usage statistics");
            var third = scanTarget(machine, 3, 2);
            h.getLevel().setBlock(third.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(third, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
            for (int tick = 0; tick < 11; tick++) machine.serverTick();
            require(h.getLevel().getBlockState(third).is(net.minecraft.world.level.block.Blocks.FARMLAND)
                && machine.screenInventory("hoe").getStackInSlot(0).getDamageValue() == 0,
                "Cancelled attempts do not consume the free half of a durability pair");
        } finally {
            bus.unregister(tool);
            bus.unregister(placement);
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void hoeInputUsesHalfDurabilityAndBreaksOnlyOnPaidTills(GameTestHelper h) {
        var machine = placeTillingMachine(h);
        var tools = machine.screenInventory("hoe");
        tools.setStackInSlot(0, ItemStack.EMPTY);
        require(tools.insertItem(0, new ItemStack(Items.DIRT), false).is(Items.DIRT)
            && tools.getStackInSlot(0).isEmpty(), "Automation cannot insert non-hoes");
        for (int i = 0; i < 9; i++) {
            var soil = scanTarget(machine, 3, i);
            h.getLevel().setBlock(soil.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(soil, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
        }
        for (int tick = 0; tick < 11; tick++) machine.serverTick();
        require(h.getLevel().getBlockState(scanTarget(machine, 3, 0)).is(net.minecraft.world.level.block.Blocks.DIRT),
            "An empty hoe input cannot till");
        var namedHoe = new ItemStack(Items.IRON_HOE);
        namedHoe.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            net.minecraft.network.chat.Component.literal("Farm tool"));
        require(tools.insertItem(0, namedHoe, false).isEmpty(), "The input accepts a real hoe");
        for (int tick = 0; tick < 11; tick++) machine.serverTick();
        require(h.getLevel().getBlockState(scanTarget(machine, 3, 1)).is(net.minecraft.world.level.block.Blocks.FARMLAND)
            && tools.getStackInSlot(0).getDamageValue() == 0, "The first successful till is free");
        require(!tools.getStackInSlot(0).has(net.minecraft.core.component.DataComponents.UNBREAKABLE)
            && tools.getStackInSlot(0).getHoverName().getString().equals("Farm tool"),
            "The free use restores tool properties and retains its name");
        var registry = h.getLevel().registryAccess();
        machine.loadWithComponents(machine.saveWithoutMetadata(registry), registry);
        for (int tick = 0; tick < 11; tick++) machine.serverTick();
        require(machine.screenInventory("hoe").getStackInSlot(0).getDamageValue() == 1,
            "The second successful till costs one durability, even across reload");

        var almostBroken = new ItemStack(Items.WOODEN_HOE);
        almostBroken.setDamageValue(almostBroken.getMaxDamage() - 1);
        machine.screenInventory("hoe").setStackInSlot(0, almostBroken);
        for (int tick = 0; tick < 11; tick++) machine.serverTick();
        require(!machine.screenInventory("hoe").getStackInSlot(0).isEmpty()
            && machine.screenInventory("hoe").getStackInSlot(0).getDamageValue() == almostBroken.getMaxDamage() - 1,
            "A nearly broken replacement survives its free use");
        for (int tick = 0; tick < 11; tick++) machine.serverTick();
        require(machine.screenInventory("hoe").getStackInSlot(0).isEmpty()
            && h.getLevel().getBlockState(scanTarget(machine, 3, 4)).is(net.minecraft.world.level.block.Blocks.FARMLAND),
            "The paid use finishes tilling and consumes the broken hoe");
        for (int tick = 0; tick < 11; tick++) machine.serverTick();
        require(h.getLevel().getBlockState(scanTarget(machine, 3, 5)).is(net.minecraft.world.level.block.Blocks.DIRT),
            "Subsequent visits cannot till after the hoe breaks");
        h.succeed();
    }

    private static DefinedMachineEntity placeTillingMachine(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(
            "industrial_overkill:agri_brain")).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        machine.energy.setStored(100000);
        machine.screenInventory("hoe").setStackInSlot(0, new ItemStack(Items.IRON_HOE));
        return machine;
    }

    private static BlockPos scanTarget(DefinedMachineEntity machine, int width, int index) {
        var front = machine.getBlockState().getValue(
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        return machine.getBlockPos().relative(front.getClockWise(), (index / width % 2 == 0 ? index % width : width - 1 - index % width) - width / 2)
            .relative(front, -1 - index / width).below();
    }

    private static void assertRange(DefinedMachineEntity machine, AgriBrainProcessor processor,
        MachineMenu menu, net.minecraft.world.entity.player.Player player, int count) {
        require(processor.rangeUpgrades() == count, "Counts each occupied generic range socket once");
        int width = 3 + 2 * count;
        var area = processor.workingArea();
        var origin = machine.getBlockPos();
        for (var facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var bounds = area.bounds(origin, facing);
            require(bounds.getXsize() == width && bounds.getZsize() == width && bounds.getYsize() == 1,
                "Range " + count + " has the expected square footprint in every orientation");
            require(bounds.getCenter().x == origin.getX() + .5 - (count + 2) * facing.getStepX()
                && bounds.getCenter().z == origin.getZ() + .5 - (count + 2) * facing.getStepZ(),
                "Expanded field keeps its near edge behind the machine");
            require(!bounds.intersects(new net.minecraft.world.phys.AABB(origin)),
                "Expanded area excludes the controller");
        }
        // Exercise the same sampler and encoded state application used by menu broadcasts.
        // Mock players have no negotiated network channel.
        var clientState = new zone.moddev.mc.lib.gui.sync.ElementStates(menu.elements,
            player.registryAccess(), () -> {});
        clientState.apply(menu.elementStates().sample(machine, player));
        var button = (WorkingAreaButtonElement) menu.bound.screen().find("working_area").orElseThrow();
        var data = clientState.get(button, WorkingAreaButtonElement.AREA);
        var facing = machine.getBlockState().getValue(
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        var expected = area.bounds(origin, facing);
        require(data.getDouble("minX") == expected.minX && data.getDouble("maxX") == expected.maxX
            && data.getDouble("minY") == expected.minY && data.getDouble("maxY") == expected.maxY
            && data.getDouble("minZ") == expected.minZ && data.getDouble("maxZ") == expected.maxZ,
            "Live overlay snapshot matches current storage after adding/removing upgrades");
    }
}

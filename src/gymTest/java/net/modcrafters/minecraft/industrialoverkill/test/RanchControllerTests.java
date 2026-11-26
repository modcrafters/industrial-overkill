package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmSupplies;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;
import net.modcrafters.minecraft.industrialoverkill.ranch.RanchAddons;
import net.modcrafters.minecraft.industrialoverkill.ranch.RanchScan;
import net.modcrafters.minecraft.industrialoverkill.ranch.RanchToolSlot;
import net.modcrafters.minecraft.industrialoverkill.upgrade.MachineUpgrades;
import zone.moddev.mc.lib.gui.model.containers.InventoryElement;
import zone.moddev.mc.lib.gui.model.containers.UpgradesDrawerElement;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

@GameTestHolder("industrial_overkill_agri")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID)
public final class RanchControllerTests {
    private static final ResourceLocation TEST_PAIL = ResourceLocation.fromNamespaceAndPath("industrial_overkill_agri", "test_milk_pail");
    private static final DataComponentType<SimpleFluidContent> PAIL_CONTENTS = DataComponentType.<SimpleFluidContent>builder()
        .persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC).build();

    @SubscribeEvent
    public static void registerPail(RegisterEvent event) {
        event.register(Registries.DATA_COMPONENT_TYPE, TEST_PAIL, () -> PAIL_CONTENTS);
        event.register(Registries.ITEM, TEST_PAIL, () -> new Item(new Item.Properties().stacksTo(1)));
    }

    @SubscribeEvent
    public static void registerPailCapability(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM,
            (stack, context) -> new FluidHandlerItemStack(() -> PAIL_CONTENTS, stack, 2000),
            BuiltInRegistries.ITEM.get(TEST_PAIL));
    }

    @GameTest(template = "gym_empty")
    public static void ranchFeedsYoungAnimalsWithMatchingFood(GameTestHelper h) {
        var ranch = place(h, new BlockPos(3, 2, 3), Direction.EAST);
        var sheep = h.spawn(EntityType.SHEEP, new BlockPos(2, 2, 3));
        sheep.setBaby(true);
        int age = sheep.getAge();
        var frontSheep = h.spawn(EntityType.SHEEP, new BlockPos(4, 2, 3));
        frontSheep.setBaby(true);
        int frontAge = frontSheep.getAge();
        var food = ranch.items.inventory("food");
        food.setStackInSlot(0, new ItemStack(Items.CARROT, 4));
        food.setStackInSlot(1, new ItemStack(Items.WHEAT, 4));
        ranch.serverTick();
        require(sheep.getAge() == age && food.getStackInSlot(1).getCount() == 4, "Feeding requires FE");

        ranch.energy.setStored(200);
        ticks(ranch, 41);
        require(sheep.getAge() == age && ranch.energy.stored() == 200, "Feeding requires Snackcelerator");
        var upgrades = ranch.items.inventory("upgrades");
        require(upgrades.isItemValid(0, RanchAddons.FEEDING.toStack())
            && upgrades.isItemValid(0, RanchAddons.BREEDING.toStack()), "Any general slot accepts ranch add-ons");
        upgrades.setStackInSlot(0, RanchAddons.FEEDING.toStack());
        ticks(ranch, 50);
        require(sheep.getAge() > age && food.getStackInSlot(0).getCount() == 4
            && food.getStackInSlot(1).getCount() == 3 && ranch.energy.stored() == 100,
            "Feeding accelerates growth using one matching food and 100 FE");
        sheep.setBaby(false);
        ticks(ranch, 50);
        require(!sheep.isInLove() && frontSheep.getAge() == frontAge
            && food.getStackInSlot(1).getCount() == 3 && ranch.energy.stored() == 100,
            "Feeding skips adults and young animals in front of the controller");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void ranchBreedsPairsWithinTheConfiguredHerdLimit(GameTestHelper h) {
        var ranch = place(h, new BlockPos(3, 2, 3), Direction.NORTH);
        var first = h.spawn(EntityType.SHEEP, new BlockPos(3, 2, 4));
        var second = h.spawn(EntityType.SHEEP, new BlockPos(4, 2, 4));
        first.setBaby(false);
        second.setBaby(false);
        var young = h.spawn(EntityType.SHEEP, new BlockPos(3, 2, 5));
        young.setBaby(true);
        var food = ranch.items.inventory("food");
        food.setStackInSlot(0, new ItemStack(Items.WHEAT, 2));
        ranch.energy.setStored(400);
        ticks(ranch, 141);
        require(!first.isInLove() && !second.isInLove() && ranch.energy.stored() == 400, "Breeding requires Pair Pressure");

        ranch.items.inventory("upgrades").setStackInSlot(0, RanchAddons.BREEDING.toStack());
        for (int i = 0; i < 5; i++) adjust(h, ranch, "adults_target_down");
        var registries = h.getLevel().registryAccess();
        ranch.loadWithComponents(ranch.saveWithoutMetadata(registries), registries);
        ticks(ranch, 141);
        require(!first.isInLove() && !second.isInLove() && food.getStackInSlot(0).getCount() == 2,
            "The saved adults target of three counts the juvenile as a future adult");

        young.discard();
        food.setStackInSlot(0, new ItemStack(Items.WHEAT));
        ticks(ranch, 150);
        require(!first.isInLove() && !second.isInLove() && food.getStackInSlot(0).getCount() == 1,
            "A pair requires food for both adults");
        food.setStackInSlot(0, new ItemStack(Items.WHEAT, 2));
        var focused = new java.util.HashSet<Integer>();
        for (int tick = 0; tick < 190 && !first.isInLove(); tick++) {
            var scan = RanchScan.current(ranch);
            if (scan != null && !scan.moving()) focused.add(scan.target().entityId());
            ranch.serverTick();
        }
        require(first.isInLove() && second.isInLove() && food.getStackInSlot(0).isEmpty() && ranch.energy.stored() == 200,
            "A funded pair enters vanilla love mode together");
        require(focused.contains(first.getId()) && focused.contains(second.getId()),
            "The scanner focuses on both parents before committing their breeding turn");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void ranchPackagesOneSurplusAdultIntoTheSuppliedCourier(GameTestHelper h) {
        var ranch = place(h, new BlockPos(3, 2, 3), Direction.NORTH);
        var first = h.spawn(EntityType.SHEEP, new BlockPos(3, 2, 4));
        var second = h.spawn(EntityType.SHEEP, new BlockPos(4, 2, 4));
        first.setBaby(false);
        second.setBaby(false);
        first.setCustomName(Component.literal("First ewe"));
        second.setCustomName(Component.literal("Second ewe"));
        var young = h.spawn(EntityType.SHEEP, new BlockPos(3, 2, 5));
        young.setBaby(true);
        var couriers = ranch.items.inventory("couriers");
        var courier = new ItemStack(MachineContent.item(CritterCourierProcessor.ID));
        require(!couriers.isItemValid(0, courier), "The Courier input is closed without Livestock To Go");
        ranch.items.inventory("upgrades").setStackInSlot(0, RanchAddons.PACKAGING.toStack());
        require(couriers.isItemValid(0, courier) && !couriers.isItemValid(0, new ItemStack(Items.IRON_SWORD)),
            "The bottom tool slot takes empty Couriers");
        couriers.setStackInSlot(0, courier.copy());
        var registries = h.getLevel().registryAccess();
        var saved = ranch.saveWithoutMetadata(registries);
        var processor = saved.getCompound("features").getCompound("ranch");
        processor.remove(StoredProcessor.DATA_VERSION);
        processor.remove("adults_target");
        processor.putInt("breed_limit", 8);
        processor.putInt("keep_adults", 1);
        ranch.loadWithComponents(saved, registries);
        var output = ranch.items.inventory("output");
        for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        ranch.energy.setStored(200);
        ticks(ranch, 141);
        require(first.isAlive() && second.isAlive() && couriers.getStackInSlot(0).is(courier.getItem())
            && ranch.energy.stored() == 200, "A full item output preserves the animal, Courier, and energy");

        output.setStackInSlot(0, ItemStack.EMPTY);
        ticks(ranch, 150);
        var packed = output.getStackInSlot(0);
        var occupant = CritterCourierProcessor.OCCUPANT.read(packed);
        var removed = first.isRemoved() ? first : second;
        require(first.isRemoved() != second.isRemoved() && young.isAlive() && couriers.getStackInSlot(0).isEmpty()
            && occupant.uuid().orElseThrow().equals(removed.getUUID())
            && occupant.customName().orElseThrow().equals(removed.getCustomName())
            && !couriers.isItemValid(0, packed) && ranch.energy.stored() == 100,
            "An occupied Courier keeps the original adult's identity and cannot go back into the input");
        couriers.setStackInSlot(0, courier.copy());
        ticks(ranch, 150);
        require(first.isRemoved() != second.isRemoved() && young.isAlive() && couriers.getStackInSlot(0).is(courier.getItem())
            && ranch.energy.stored() == 100, "Packing stops at the migrated adults target even with juveniles present");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void ranchMilkingRequiresAContainerAndKeepsTheLastBucket(GameTestHelper h) {
        var ranch = place(h, new BlockPos(3, 2, 3), Direction.NORTH);
        var neighbor = place(h, new BlockPos(5, 2, 3), Direction.NORTH);
        h.spawn(EntityType.COW, new BlockPos(4, 2, 4)).setBaby(false);
        var containers = ranch.items.inventory("containers");
        require(containers.isItemValid(0, new ItemStack(Items.BUCKET))
            && !containers.isItemValid(0, new ItemStack(Items.WHEAT)), "The container slot requires a fluid handler");
        ranch.energy.setStored(300);
        ticks(ranch, 41);
        require(ranch.fluids.getFluidInTank(0).isEmpty() && ranch.energy.stored() == 300,
            "An empty tool slot prevents milking");
        containers.setStackInSlot(0, new ItemStack(Items.WATER_BUCKET));
        ticks(ranch, 50);
        require(ranch.fluids.getFluidInTank(0).isEmpty() && ranch.energy.stored() == 300,
            "A container that cannot hold milk prevents milking");

        containers.setStackInSlot(0, new ItemStack(Items.BUCKET, 2));
        ticks(ranch, 50);
        var output = ranch.items.inventory("output");
        require(output.getStackInSlot(0).is(Items.MILK_BUCKET) && containers.getStackInSlot(0).is(Items.BUCKET)
            && containers.getStackInSlot(0).getCount() == 1 && ranch.fluids.getFluidInTank(0).isEmpty()
            && ranch.energy.stored() == 200, "An extra bucket fills and moves to output for one FE charge");

        neighbor.items.inventory("containers").setStackInSlot(0, new ItemStack(Items.BUCKET));
        neighbor.energy.setStored(100);
        ticks(neighbor, 41);
        require(neighbor.fluids.getFluidInTank(0).isEmpty() && neighbor.energy.stored() == 100,
            "The same cow cannot be milked by another controller during its cooldown");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void ranchReusableBucketFillsTenBucketTank(GameTestHelper h) {
        var ranch = place(h, new BlockPos(3, 2, 3), Direction.NORTH);
        h.spawn(EntityType.COW, new BlockPos(3, 2, 4)).setBaby(false);
        h.spawn(EntityType.COW, new BlockPos(4, 2, 4)).setBaby(false);
        var containers = ranch.items.inventory("containers");
        containers.setStackInSlot(0, new ItemStack(Items.BUCKET));
        var output = ranch.items.inventory("output");
        for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        ranch.energy.setStored(300);
        ranch.fluids.restoreFluid(0, new FluidStack(NeoForgeMod.MILK, 9000));
        require(ranch.fluids.getTankCapacity(0) == 10000, "The milk tank holds ten buckets");
        ticks(ranch, 41);
        require(ranch.fluids.getFluidInTank(0).getAmount() == 10000 && containers.getStackInSlot(0).is(Items.BUCKET)
            && containers.getStackInSlot(0).getCount() == 1 && ranch.energy.stored() == 200,
            "The last bucket adds milk to the tank even when item output is full");

        ticks(ranch, 50);
        require(ranch.fluids.getFluidInTank(0).getAmount() == 10000 && containers.getStackInSlot(0).is(Items.BUCKET)
            && ranch.energy.stored() == 200, "A full tank preserves the bucket and power");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void ranchTopsUpOutputPailsBeforeUsingFreshOnes(GameTestHelper h) {
        var ranch = place(h, new BlockPos(3, 2, 3), Direction.NORTH);
        h.spawn(EntityType.COW, new BlockPos(3, 2, 4)).setBaby(false);
        var containers = ranch.items.inventory("containers");
        var emptyPail = new ItemStack(BuiltInRegistries.ITEM.get(TEST_PAIL));
        containers.setStackInSlot(0, emptyPail.copy());
        var output = ranch.items.inventory("output");
        for (int slot = 1; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        var partialPail = emptyPail.copy();
        partialPail.set(PAIL_CONTENTS, SimpleFluidContent.copyOf(new FluidStack(NeoForgeMod.MILK, 1500)));
        output.setStackInSlot(0, partialPail);
        ranch.energy.setStored(300);

        ticks(ranch, 41);
        require(output.getStackInSlot(0).getOrDefault(PAIL_CONTENTS, SimpleFluidContent.EMPTY).getAmount() == 1500
            && ItemStack.matches(containers.getStackInSlot(0), emptyPail) && ranch.energy.stored() == 300,
            "A blocked remainder leaves the partial pail, fresh pail and energy untouched");

        output.setStackInSlot(1, ItemStack.EMPTY);
        ticks(ranch, 50);
        require(output.getStackInSlot(0).getOrDefault(PAIL_CONTENTS, SimpleFluidContent.EMPTY).getAmount() == 2000
            && output.getStackInSlot(1).getOrDefault(PAIL_CONTENTS, SimpleFluidContent.EMPTY).getAmount() == 500
            && containers.getStackInSlot(0).isEmpty()
            && ranch.fluids.getFluidInTank(0).isEmpty() && ranch.energy.stored() == 200,
            "The existing pail fills first and the remainder moves to output in a fresh pail");

        h.spawn(EntityType.COW, new BlockPos(4, 2, 4)).setBaby(false);
        ticks(ranch, 150);
        require(output.getStackInSlot(1).getOrDefault(PAIL_CONTENTS, SimpleFluidContent.EMPTY).getAmount() == 500
            && ranch.energy.stored() == 200,
            "An empty tool slot prevents milking even when an output pail has room");
        containers.setStackInSlot(0, emptyPail.copy());
        ticks(ranch, 100);
        require(output.getStackInSlot(1).getOrDefault(PAIL_CONTENTS, SimpleFluidContent.EMPTY).getAmount() == 1500
            && ItemStack.matches(containers.getStackInSlot(0), emptyPail) && ranch.energy.stored() == 100,
            "When the output pail takes the whole yield, the fresh pail stays in the tool slot");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void ranchShearsIntoOutputOnlyWhenThereIsRoom(GameTestHelper h) {
        var ranch = place(h, new BlockPos(3, 2, 3), Direction.NORTH);
        var sheep = h.spawn(EntityType.SHEEP, new BlockPos(3, 2, 4));
        sheep.setBaby(false);
        sheep.setColor(DyeColor.PINK);
        var shears = ranch.items.inventory("shears");
        require(!shears.isItemValid(0, new ItemStack(Items.SHEARS)), "The shears input is closed without Baa-rber Shop");
        shears.setStackInSlot(0, new ItemStack(Items.SHEARS));
        ranch.energy.setStored(200);
        ticks(ranch, 41);
        require(!sheep.isSheared() && ranch.energy.stored() == 200, "Existing shears only work with Baa-rber Shop installed");
        ranch.items.inventory("upgrades").setStackInSlot(0, RanchAddons.SHEARING.toStack());
        require(shears.isItemValid(0, new ItemStack(Items.SHEARS)) && !shears.isItemValid(0, new ItemStack(Items.IRON_SWORD)),
            "The installed add-on opens the input for shears");
        var output = ranch.items.inventory("output");
        for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        ticks(ranch, 50);
        require(!sheep.isSheared() && shears.getStackInSlot(0).getDamageValue() == 0 && ranch.energy.stored() == 200,
            "A full output leaves sheep, shears and power untouched");

        output.setStackInSlot(0, ItemStack.EMPTY);
        ticks(ranch, 50);
        var wool = output.getStackInSlot(0);
        require(sheep.isSheared() && wool.is(Items.PINK_WOOL) && wool.getCount() >= 1 && wool.getCount() <= 3
            && shears.getStackInSlot(0).getDamageValue() == 1 && ranch.energy.stored() == 100,
            "Shearing puts the correct wool in output and applies one wear and FE charge");
        ticks(ranch, 50);
        require(output.getStackInSlot(0).getCount() == wool.getCount() && ranch.energy.stored() == 100,
            "A shorn sheep is skipped until its wool regrows");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void ranchLockedFeedSlotsAllowManualRemoval(GameTestHelper h) {
        var ranch = place(h, new BlockPos(3, 2, 3), Direction.NORTH);
        var player = h.makeMockServerPlayerInLevel();
        var pos = ranch.getBlockPos();
        player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        var menu = new MachineMenu(90, player.getInventory(), ranch);
        player.containerMenu = menu;
        try {
            var feed = (InventoryElement) menu.bound.screen().find("ranch_food").orElseThrow();
            var nativeSlot = menu.slot(feed.slots().getFirst());
            var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            var input = ranch.items.index("food", 0);
            var wheat = new ItemStack(Items.WHEAT, 4);
            var drawer = (UpgradesDrawerElement) menu.bound.screen().find("upgrades").orElseThrow();
            var herdTarget = menu.bound.screen().find("adults_target").orElseThrow();
            var shears = (RanchToolSlot) menu.bound.screen().find("ranch_shears_slot").orElseThrow();
            var bucket = (RanchToolSlot) menu.bound.screen().find("ranch_containers_slot").orElseThrow();
            var courier = (RanchToolSlot) menu.bound.screen().find("ranch_couriers_slot").orElseThrow();
            menu.elementStates().set(drawer, UpgradesDrawerElement.OPEN, true);
            menu.broadcastChanges();
            require(!shears.enabled(menu.elementStates()) && !courier.enabled(menu.elementStates())
                && bucket.enabled(menu.elementStates()) && shears.tooltip(ranch, ItemStack.EMPTY).size() == 1,
                "Tool presentation marks the add-on-gated slots disabled and explains the lock on hover");
            require(!menu.visible(herdTarget), "The shared target needs a herd-management add-on");
            require(port != null && !nativeSlot.mayPlace(wheat) && !port.insertItem(input, wheat.copy(), true).isEmpty(),
                "The menu and automation both reject new food before an add-on is installed");

            var upgrades = ranch.items.inventory("upgrades");
            upgrades.setStackInSlot(0, RanchAddons.BREEDING.toStack());
            upgrades.setStackInSlot(2, RanchAddons.SHEARING.toStack());
            menu.broadcastChanges();
            require(shears.enabled(menu.elementStates()) && shears.tooltip(ranch, ItemStack.EMPTY).isEmpty(),
                "Installing Baa-rber Shop updates the open menu and clears the disabled tooltip");
            require(menu.visible(herdTarget) && ranch.rangeUpgrades() == 0,
                "Pair Pressure shows the shared target below the grid and does not extend range");
            require(nativeSlot.mayPlace(wheat) && port.insertItem(input, wheat.copy(), false).isEmpty(),
                "Installing Pair Pressure opens the existing menu and cached automation port");
            upgrades.setStackInSlot(1, RanchAddons.FEEDING.toStack());
            upgrades.setStackInSlot(3, RanchAddons.PACKAGING.toStack());
            upgrades.setStackInSlot(0, ItemStack.EMPTY);
            menu.broadcastChanges();
            require(menu.visible(herdTarget), "Livestock To Go also shows the shared target");
            upgrades.setStackInSlot(3, ItemStack.EMPTY);
            menu.broadcastChanges();
            require(!menu.visible(herdTarget), "Removing the last herd-management add-on hides the controls");
            require(nativeSlot.mayPlace(wheat) && port.isItemValid(input, wheat), "Snackcelerator alone also opens the feed slots");
            upgrades.setStackInSlot(1, ItemStack.EMPTY);
            require(!nativeSlot.mayPlace(wheat) && !port.insertItem(input, wheat.copy(), true).isEmpty(),
                "Removing the last applicable add-on closes both insertion paths immediately");

            menu.clicked(nativeSlot.index, 0, ClickType.PICKUP, player);
            require(ItemStack.matches(menu.getCarried(), wheat) && ranch.items.getStackInSlot(input).isEmpty(),
                "Existing food can still be removed by hand");
        } finally {
            menu.removed(player);
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void ranchScansOneSnapshotAtTheGearboxRate(GameTestHelper h) {
        var ranch = place(h, new BlockPos(3, 2, 3), Direction.NORTH);
        var first = h.spawn(EntityType.PIG, new BlockPos(3, 2, 4));
        var second = h.spawn(EntityType.PIG, new BlockPos(4, 2, 4));
        var mountPos = ranch.getBlockPos().above();
        var mount = h.getLevel().getBlockState(mountPos);
        require(net.modcrafters.minecraft.industrialoverkill.agri.AgriDockBlock.isRanchMount(mount)
            && mount.getCollisionShape(h.getLevel(), mountPos).bounds().maxY == .5, "The antenna reserves half a block above the controller");
        ranch.energy.setStored(200);
        ranch.serverTick();
        var firstTarget = RanchScan.current(ranch).target().entityId();
        var newArrival = h.spawn(EntityType.PIG, new BlockPos(3, 2, 5));
        ticks(ranch, 39);
        require(RanchScan.current(ranch).target().entityId() == firstTarget && !RanchScan.current(ranch).moving(),
            "The antenna holds its first target for forty work ticks");
        ranch.serverTick();
        var sweep = RanchScan.current(ranch);
        int secondTarget = sweep.target().entityId();
        require(sweep.moving() && sweep.duration() == 10, "The antenna takes ten work ticks to move between animals");
        require(firstTarget != secondTarget && java.util.Set.of(first.getId(), second.getId()).equals(java.util.Set.of(firstTarget, secondTarget)),
            "A new animal waits for the next cycle instead of joining the current snapshot");
        ticks(ranch, 10);
        var targets = new java.util.HashSet<Integer>();
        for (int turn = 0; turn < 3; turn++) {
            ticks(ranch, 50);
            targets.add(RanchScan.current(ranch).target().entityId());
        }
        require(targets.contains(newArrival.getId()), "The next cycle includes the new arrival");

        var gears = ranch.items.inventory("mmdlib_gearbox");
        gears.setStackInSlot(0, new ItemStack(MachineUpgrades.GEARBOX.get(), 8));
        var registries = h.getLevel().registryAccess();
        ranch.loadWithComponents(ranch.saveWithoutMetadata(registries), registries);
        ranch.serverTick();
        var focus = RanchScan.current(ranch);
        require(!focus.moving() && focus.duration() == 20, "At top speed a focus lasts one second");
        ticks(ranch, 19);
        require(!RanchScan.current(ranch).moving() && RanchScan.current(ranch).target().equals(focus.target()),
            "The antenna keeps aiming at the same animal until the full dwell has elapsed");
        ranch.serverTick();
        var fastTurn = RanchScan.current(ranch);
        require(fastTurn.moving() && fastTurn.duration() == 5 && fastTurn.target().entityId() != focus.target().entityId(),
            "At top speed a turn to the next animal lasts a quarter of a second");
        ticks(ranch, 4);
        require(RanchScan.current(ranch).moving(), "Scanning stays off while the antenna is turning");
        ranch.serverTick();
        require(!RanchScan.current(ranch).moving() && RanchScan.current(ranch).target().equals(fastTurn.target()),
            "The antenna starts its next focus after the turn completes");
        h.succeed();
    }

    private static DefinedMachineEntity place(GameTestHelper h, BlockPos relative, Direction facing) {
        var pos = h.absolutePos(relative);
        var block = BuiltInRegistries.BLOCK.get(FarmSupplies.id("ranch_controller"));
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
        return (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
    }

    private static void ticks(DefinedMachineEntity machine, int count) {
        for (int tick = 0; tick < count; tick++) machine.serverTick();
    }

    private static void adjust(GameTestHelper h, DefinedMachineEntity ranch, String action) {
        var processor = ranch.screenFeatures().findProcessor("ranch").orElseThrow();
        require(processor.invoke(FarmSupplies.id(action), h.makeMockPlayer(GameType.SURVIVAL)).accepted(),
            "Ranch settings accept " + action);
    }
}

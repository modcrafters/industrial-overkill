package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierData;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;
import zone.moddev.mc.lib.machine.neoforge.MultiblockPorts;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CritterCourierTests {
    static CritterCourierProcessor trap(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(pos, MachineContent.block(CritterCourierData.ID).defaultBlockState(), 3);
        return CritterCourierProcessor.from(helper.getLevel().getBlockEntity(pos));
    }
    static <T extends Mob> T mob(GameTestHelper helper, CritterCourierProcessor trap, EntityType<T> type) {
        var entity = type.create(helper.getLevel());
        var pos = trap.getBlockPos();
        entity.setPos(pos.getX() + .5, pos.getY() + .1875, pos.getZ() + .5);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
    @GameTest(template = "gym_empty")
    public static void trappedPetsTrainAndKeepTheirOwner(GameTestHelper helper) {
        var gym = AnimalGymTests.place(helper, Direction.SOUTH);
        for (var type : java.util.List.of(EntityType.CAT, EntityType.WOLF)) {
            var cage = trap(helper, gym.getBlockPos().east(3));
            var pet = (net.minecraft.world.entity.TamableAnimal) mob(helper, cage, type);
            var owner = java.util.UUID.randomUUID();
            pet.setTame(true, true);
            pet.setOwnerUUID(owner);
            pet.setOrderedToSit(true);
            pet.setInSittingPose(true);
            var uuid = pet.getUUID();
            require(cage.capture(pet), "Pet captured");
            require(gym.food.insertItem(3, cage.take(), false).isEmpty(), "Pet trap installed");
            helper.getLevel().removeBlock(cage.getBlockPos(), false);
            var food = type == EntityType.CAT ? Items.COD : Items.BEEF;
            gym.food.insertItem(0, new ItemStack(food, 2), false);
            gym.serverTick();
            require(
                gym.getGeneration() == (type == EntityType.CAT ? 4 : 6)
                    && gym.displayFood().is(food),
                "Contained pet eats and works");
            AnimalGymTests.lamps(gym, 14);
            gym.training.reset();
            var carried = gym.food.extractItem(3, 1, false);
            var restored = (net.minecraft.world.entity.TamableAnimal) CritterCourierData
                .load(CritterCourierData.read(carried), helper.getLevel());
            require(restored != null && restored.isTame() && owner.equals(restored.getOwnerUUID())
                && restored.isOrderedToSit() && restored.isInSittingPose()
                && uuid.equals(restored.getUUID())
                && !restored.getPersistentData().contains("industrial_overkill:fitness"),
                "Trap keeps pet sitting preference, identity, owner and normal attributes");
            gym.food.extractItem(0, 64, false);
        }
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void captureCarryPlaceReleasePreservesIdentity(GameTestHelper helper) {
        var pos = AnimalGymTests.origin(helper);
        var source = trap(helper, pos);
        var cow = mob(helper, source, EntityType.COW);
        cow.setCustomName(Component.literal("Bessie"));
        cow.setHealth(7);
        var uuid = cow.getUUID();
        ((zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity) helper.getLevel().getBlockEntity(pos)).serverTick();
        require(source.occupied() && cow.isRemoved(),
            "Stepping onto armed plate captures exactly one mob");
        var stack = source.take();
        var payload = zone.moddev.mc.lib.machine.feature.PortableData.read(stack, "courier");
        require(payload.getAllKeys().equals(java.util.Set.of("occupant")),
            "Only the explicitly portable occupant travels; execution and derived state stay out");
        require(!source.occupied(), "Taking portable data refreshes the source's derived occupied state");
        require(source.take().isEmpty(), "Pickup ownership can transfer only once");
        helper.getLevel().removeBlock(pos, false);
        var target = pos.east(3);
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setPos(target.getX(), target.getY() + 5, target.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var use = new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
        require(
            CommonHooks.onPlaceItemIntoWorld(use).consumesAction()
                && player.getMainHandItem().isEmpty(),
            "Occupied trap transfers even in creative, without duplication");
        var host = (zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity) helper.getLevel().getBlockEntity(target);
        var placed = CritterCourierProcessor.from(host);
        require(placed.occupied(), "Placed cage still contains the mob");
        var detailLines = placed.variable("details");
        require(!placed.state().contains("details") && !placed.state().contains("occupied"),
            "Derived details and occupancy are reconstructed rather than persisted");
        var saved = host.saveWithFullMetadata(helper.getLevel().registryAccess());
        placed.setMob(new net.minecraft.nbt.CompoundTag());
        host.loadWithComponents(saved, helper.getLevel().registryAccess());
        require(detailLines.equals(placed.variable("details")), "Translated lines recompute identically after save/load");
        require(placed.release(player), "Mob releases into clear adjacent space");
        var released = (Animal) helper.getLevel().getEntity(uuid);
        require(
            released != null && released.getHealth() == 7
                && released.getName().getString().equals("Bessie")
                && !released.getPersistentData().contains("industrial_overkill:fitness"),
            "Name, UUID, health survive transport");
        require(!placed.occupied() && !placed.release(player), "No second release possible");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void blockedReleaseAndInvalidDataRemainOwned(GameTestHelper helper) {
        var pos = AnimalGymTests.origin(helper);
        var trap = trap(helper, pos);
        var chicken = mob(helper, trap, EntityType.CHICKEN);
        require(trap.capture(chicken), "Chicken captured");
        for (var direction : Direction.Plane.HORIZONTAL) {
            helper.getLevel().setBlock(pos.relative(direction), Blocks.STONE.defaultBlockState(),
                3);
            helper.getLevel().setBlock(pos.relative(direction).above(),
                Blocks.STONE.defaultBlockState(), 3);
        }
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var before = trap.mobData();
        require(!trap.release(player) && trap.mobData().equals(before),
            "Blocked release keeps full occupant data");
        var invalid = new CompoundTag();
        invalid.putString("id", "missing_mod:unknown_mob");
        invalid.putString("custom_payload", "retain me");
        trap.setMob(invalid);
        require(!trap.release(player) && CritterCourierData.read(trap.take()).equals(invalid),
            "Undecodable occupants remain recoverable");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void installedTrapTrainsWithoutWorldEntityAndSurvivesStops(
        GameTestHelper helper) {
        var gym = AnimalGymTests.place(helper, Direction.NORTH);
        var cage = trap(helper, AnimalGymTests.origin(helper).east(3));
        var chicken = mob(helper, cage, EntityType.CHICKEN);
        var uuid = chicken.getUUID();
        require(cage.capture(chicken), "Chicken captured for gym");
        var stack = cage.take();
        helper.getLevel().removeBlock(cage.getBlockPos(), false);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var use = new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(gym.getBlockPos().west()), Direction.UP,
                gym.getBlockPos().west(), false));
        require(MachineContent.item(CritterCourierData.ID).useOn(use).consumesAction() && stack.isEmpty(),
            "Right-click a gym part installs occupied trap");
        gym.serverTick();
        require(
            gym.captive.installed() && gym.getGeneration() == 0
                && helper.getLevel().getEntity(uuid) == null,
            "No food leaves the captive safely contained");
        gym.food.insertItem(0, new ItemStack(Items.WHEAT_SEEDS, 2), false);
        gym.serverTick();
        require(
            gym.energy.stored() == 2
                && gym.captive.animal().getPersistentData()
                    .getInt("industrial_overkill:gym_ration") == 1199,
            "Contained chicken consumes seeds and trains");
        require(helper.getLevel().getEntity(uuid) == null,
            "No free world entity can escape or duplicate");
        var port = MultiblockPorts.items(gym, Direction.NORTH);
        require(port.extractItem(3, 1, false).isEmpty(), "Food automation cannot steal the trap");
        helper.getLevel().setBlock(gym.getBlockPos().west().above(),
            Blocks.STONE.defaultBlockState(), 3);
        gym.serverTick();
        require(gym.captive.installed() && gym.getGeneration() == 0,
            "Obstruction retains containment");
        helper.getLevel().removeBlock(gym.getBlockPos().west().above(), false);
        gym.control("toggle");
        gym.serverTick();
        require(gym.captive.installed() && gym.getGeneration() == 0, "Pause retains containment");
        gym.control("toggle");
        gym.energy.setStored(gym.energy.capacity());
        gym.serverTick();
        require(gym.captive.installed() && helper.getLevel().getEntity(uuid) == null,
            "Full buffer retains containment");
        var save = gym.saveWithFullMetadata(helper.getLevel().registryAccess());
        gym.loadWithComponents(save, helper.getLevel().registryAccess());
        require(
            gym.captive.installed()
                && gym.captive.animal().getPersistentData()
                    .getInt("industrial_overkill:gym_ration") == 1199,
            "Captive and paid food allowance survive gym reload");
        var returned = gym.food.extractItem(3, 1, false);
        var decoded = (Animal) CritterCourierData.load(CritterCourierData.read(returned), helper.getLevel());
        require(
            decoded.getUUID().equals(uuid)
                && decoded.getPersistentData().getInt("industrial_overkill:gym_ration") == 1199,
            "Removing trap carries animal and original identity");
        require(!gym.captive.installed(), "Removed slot no longer owns an animal");
        helper.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void receiverOwnsInstallationAndRejectsWithoutChangingEitherSide(GameTestHelper helper) {
        var gym = AnimalGymTests.place(helper, Direction.NORTH);
        var cow = EntityType.COW.create(helper.getLevel());
        var stack = CritterCourierData.stack(CritterCourierData.save(cow));
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var member = gym.getBlockPos().west();
        var use = new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(member), Direction.UP, member, false));
        require(MachineContent.item(CritterCourierData.ID).useOn(use).consumesAction(),
            "Shared item routing finds the gym receiver through a multiblock part");
        stack.setCount(1); // Vanilla restores this original reference after creative Item.useOn.
        require(player.getMainHandItem().isEmpty(), "Creative installation consumes unique contents despite vanilla count restoration");
        var installed = gym.food.getStackInSlot(3).copy();
        require(CritterCourierData.read(installed).getUUID("UUID").equals(cow.getUUID()), "Receiver preserves the carried identity");
        var second = CritterCourierData.stack(CritterCourierData.save(EntityType.SHEEP.create(helper.getLevel())));
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        var before = second.copy();
        var training = gym.training.state();
        use = new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(member), Direction.UP, member, false));
        require(MachineContent.item(CritterCourierData.ID).useOn(use) == net.minecraft.world.InteractionResult.FAIL,
            "Full receiver rejects the exchange without placing the courier beside it");
        require(ItemStack.matches(player.getMainHandItem(), before) && ItemStack.matches(gym.food.getStackInSlot(3), installed),
            "Failed exchange leaves both items unchanged");
        require(training.equals(gym.training.state()), "Rejected installation does not reset training");
        gym.food.extractItem(3, 1, false);
        player.setItemInHand(InteractionHand.OFF_HAND, second);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var offhand = new UseOnContext(player, InteractionHand.OFF_HAND,
            new BlockHitResult(Vec3.atCenterOf(member), Direction.UP, member, false));
        require(MachineContent.item(CritterCourierData.ID).useOn(offhand).consumesAction()
            && player.getOffhandItem().isEmpty(), "Offhand exchanges use the same ownership rules");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void captiveCachePreservesEveryTickAndRefreshesOnReplacement(
        GameTestHelper helper) {
        var gym = AnimalGymTests.place(helper, Direction.NORTH);
        var cow = EntityType.COW.create(helper.getLevel());
        gym.food.insertItem(3, CritterCourierData.stack(CritterCourierData.save(cow)), false);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 64), false);
        var cached = gym.captive.mob();
        for (int tick = 0; tick < 25; tick++) {
            gym.serverTick();
            require(gym.captive.mob() == cached, "Training must reuse the decoded captive");
        }
        var returned = gym.food.extractItem(3, 1, false);
        var decoded = (Animal) CritterCourierData.load(CritterCourierData.read(returned), helper.getLevel());
        require(decoded.getPersistentData().getInt("industrial_overkill:gym_ration") == 275,
            "Immediate extraction preserves every tick between visual updates");
        require(!gym.captive.installed() && gym.captive.mob() == null, "Removal invalidates cache");
        var other = EntityType.COW.create(helper.getLevel());
        gym.food.insertItem(3, CritterCourierData.stack(CritterCourierData.save(other)), false);
        require(gym.captive.mob().getUUID().equals(other.getUUID()) && gym.captive.mob() != cached,
            "Replacement must decode the new occupant");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void breakingTrapAndGymDropsOneOccupiedItem(GameTestHelper helper) {
        var gym = AnimalGymTests.place(helper, Direction.NORTH);
        var cage = trap(helper, AnimalGymTests.origin(helper).east(3));
        var rabbit = mob(helper, cage, EntityType.RABBIT);
        var uuid = rabbit.getUUID();
        require(cage.capture(rabbit), "Rabbit captured");
        helper.getLevel().destroyBlock(cage.getBlockPos(), true);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(cage.getBlockPos()).inflate(2));
        var traps = drops.stream().filter(e -> e.getItem().is(MachineContent.item(CritterCourierData.ID))).toList();
        require(
            traps.size() == 1
                && CritterCourierData.read(traps.getFirst().getItem()).getUUID("UUID").equals(uuid),
            "Breaking trap drops one intact occupant");
        gym.food.insertItem(3, traps.getFirst().getItem().copy(), false);
        traps.getFirst().discard();
        gym.multiblock().dismantle(MultiblockInstance.Cause.SURVIVAL);
        var gymDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(gym.getBlockPos()).inflate(5));
        require(gymDrops.stream().filter(e -> CritterCourierData.occupied(e.getItem())).count() == 1
            && helper.getLevel().getEntity(uuid) == null,
            "Gym teardown returns occupied trap, never a loose animal");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void unsupportedMobsAndPassengersStaySafe(GameTestHelper helper) {
        var gym = AnimalGymTests.place(helper, Direction.NORTH);
        var cage = trap(helper, AnimalGymTests.origin(helper).east(3));
        var pig = mob(helper, cage, EntityType.PIG);
        var rider = mob(helper, cage, EntityType.CHICKEN);
        rider.startRiding(pig, true);
        require(!cage.capture(pig) && !cage.capture(rider),
            "Riding groups are not partially serialized");
        rider.stopRiding();
        rider.discard();
        pig.discard();
        var zombie = mob(helper, cage, EntityType.ZOMBIE);
        require(cage.capture(zombie), "Ordinary hostile mobs can be transported");
        gym.food.insertItem(3, cage.take(), false);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 2), false);
        gym.serverTick();
        require(
            gym.captive.installed() && gym.energy.stored() == 0
                && gym.food.getStackInSlot(0).getCount() == 2,
            "Unsupported gym occupants stay contained without spending food");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void xmlCourierActionsAndShapesFollowStoredState(GameTestHelper helper) {
        var pos = AnimalGymTests.origin(helper);
        var courier = trap(helper, pos);
        var host = (zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity) helper.getLevel().getBlockEntity(pos);
        require(host.resources.automation().groups().isEmpty(), "Courier supports zero automation groups");
        require(host.screenAutomation() == null, "Resource-free courier has no automation drawer");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 5, pos.getY(), pos.getZ());
        var release = net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym.id("release");
        var pickup = zone.moddev.mc.lib.machine.feature.PortableStoredProcessor.PICKUP;
        require(pickup.equals(host.definition.behavior().secondaryAction()),
            "XML sneak-use and the pickup button share the library action");
        require(!host.screenFeatures().condition("$processors.courier.occupied"), "Empty courier disables Release");
        require(!courier.invoke(release, player).accepted(), "Empty release action is rejected on the server");
        require(host.getBlockState().getShape(helper.getLevel(), pos).max(net.minecraft.core.Direction.Axis.Y) == 3.0 / 16,
            "Empty XML courier has the plate collision shape");
        var cow = mob(helper, courier, EntityType.COW);
        require(courier.capture(cow), "Cow captured by custom processor");
        require(host.screenFeatures().condition("$processors.courier.occupied"), "Capture enables Release without polling");
        require(host.getBlockState().getShape(helper.getLevel(), pos).max(net.minecraft.core.Direction.Axis.Y) == 1,
            "Occupied XML courier has the cage collision shape");
        courier.setPaused(true);
        require(courier.invoke(release, player).accepted(), "Release remains available while processing is paused");
        require(!host.screenFeatures().condition("$processors.courier.occupied"), "Release disables its button immediately");
        require(!courier.invoke(release, player).accepted(), "Repeated stale release cannot duplicate the occupant");
        host.serverTick();
        require(courier.state().getInt("cooldown") == 40, "Cooldown stays frozen while paused");
        courier.setPaused(false);
        for (int i = 0; i < 39; i++) host.serverTick();
        require(courier.state().getInt("cooldown") == 1, "Idle timer counts down through the shared lifecycle");
        host.serverTick();
        require(courier.state().getInt("cooldown") == 0, "Cooldown reaches zero without underflow");
        require(courier.invoke(pickup, player).accepted() && helper.getLevel().getBlockState(pos).isAir(),
            "Pickup action removes the generic machine host");
        require(!courier.invoke(pickup, player).accepted(), "Repeated pickup cannot produce another item");
        helper.succeed();
    }

}

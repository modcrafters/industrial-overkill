package zone.moddev.mc.industrialoverkill.test;

import static zone.moddev.mc.industrialoverkill.test.AnimalGymTests.require;

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

import zone.moddev.mc.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.industrialoverkill.trap.MobTrap;
import zone.moddev.mc.industrialoverkill.trap.MobTrapData;
import zone.moddev.mc.industrialoverkill.trap.MobTrapEntity;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;
import zone.moddev.mc.lib.machine.neoforge.MultiblockPorts;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobTrapTests {
    static MobTrapEntity trap(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(pos, MobTrap.BLOCK.get().defaultBlockState(), 3);
        return (MobTrapEntity) helper.getLevel().getBlockEntity(pos);
    }
    static <T extends Mob> T mob(GameTestHelper helper, MobTrapEntity trap, EntityType<T> type) {
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
            var restored = (net.minecraft.world.entity.TamableAnimal) MobTrapData
                .load(MobTrapData.read(carried), helper.getLevel());
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
        source.serverTick();
        require(source.occupied() && cow.isRemoved(),
            "Stepping onto armed plate captures exactly one mob");
        var stack = source.take();
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
        var placed = (MobTrapEntity) helper.getLevel().getBlockEntity(target);
        require(placed.occupied(), "Placed cage still contains the mob");
        var saved = placed.saveWithFullMetadata(helper.getLevel().registryAccess());
        placed.loadWithComponents(saved, helper.getLevel().registryAccess());
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
        require(!trap.release(player) && MobTrapData.read(trap.take()).equals(invalid),
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
        require(MobTrap.ITEM.get().useOn(use).consumesAction() && stack.isEmpty(),
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
        var decoded = (Animal) MobTrapData.load(MobTrapData.read(returned), helper.getLevel());
        require(
            decoded.getUUID().equals(uuid)
                && decoded.getPersistentData().getInt("industrial_overkill:gym_ration") == 1199,
            "Removing trap carries animal and original identity");
        require(!gym.captive.installed(), "Removed slot no longer owns an animal");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void captiveCachePreservesEveryTickAndRefreshesOnReplacement(
        GameTestHelper helper) {
        var gym = AnimalGymTests.place(helper, Direction.NORTH);
        var cow = EntityType.COW.create(helper.getLevel());
        gym.food.insertItem(3, MobTrapData.stack(MobTrapData.save(cow)), false);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 64), false);
        var cached = gym.captive.mob();
        for (int tick = 0; tick < 25; tick++) {
            gym.serverTick();
            require(gym.captive.mob() == cached, "Training must reuse the decoded captive");
        }
        var returned = gym.food.extractItem(3, 1, false);
        var decoded = (Animal) MobTrapData.load(MobTrapData.read(returned), helper.getLevel());
        require(decoded.getPersistentData().getInt("industrial_overkill:gym_ration") == 275,
            "Immediate extraction preserves every tick between visual updates");
        require(!gym.captive.installed() && gym.captive.mob() == null, "Removal invalidates cache");
        var other = EntityType.COW.create(helper.getLevel());
        gym.food.insertItem(3, MobTrapData.stack(MobTrapData.save(other)), false);
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
        var traps = drops.stream().filter(e -> e.getItem().is(MobTrap.ITEM.get())).toList();
        require(
            traps.size() == 1
                && MobTrapData.read(traps.getFirst().getItem()).getUUID("UUID").equals(uuid),
            "Breaking trap drops one intact occupant");
        gym.food.insertItem(3, traps.getFirst().getItem().copy(), false);
        traps.getFirst().discard();
        gym.multiblock().dismantle(MultiblockInstance.Cause.SURVIVAL);
        var gymDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(gym.getBlockPos()).inflate(5));
        require(gymDrops.stream().filter(e -> MobTrapData.occupied(e.getItem())).count() == 1
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
}

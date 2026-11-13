package zone.moddev.mc.industrialoverkill.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import zone.moddev.mc.industrialoverkill.gym.AnimalGym;
import zone.moddev.mc.industrialoverkill.gym.AnimalGymEntity;
import zone.moddev.mc.industrialoverkill.gym.GymAnimalFit;
import zone.moddev.mc.industrialoverkill.gym.GymAnimalRecipe;
import zone.moddev.mc.industrialoverkill.gym.GymAnimals;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;
import zone.moddev.mc.lib.machine.neoforge.MultiblockMembers;
import zone.moddev.mc.lib.machine.neoforge.MultiblockPorts;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimalGymTests {
    static BlockPos origin(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(6, 2, 6));
    }
    static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
    static UseOnContext context(GameTestHelper helper, Direction facing) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var pos = origin(helper);
        player.setPos(pos.getX(), pos.getY() + 6, pos.getZ());
        player.setYRot(facing.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AnimalGym.ITEM.get(), 2));
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
    static AnimalGymEntity place(GameTestHelper helper, Direction facing) {
        var use = context(helper, facing);
        require(CommonHooks.onPlaceItemIntoWorld(use).consumesAction(), "Whole gym placed");
        require(use.getItemInHand().getCount() == 1, "Exactly one machine item consumed");
        return (AnimalGymEntity) helper.getLevel().getBlockEntity(origin(helper));
    }
    static Animal animal(GameTestHelper helper, AnimalGymEntity gym,
        EntityType<? extends Animal> type) {
        var animal = type.create(helper.getLevel());
        animal.setAge(0);
        var center = gym.beltCenter();
        animal.setPos(center.x, center.y, center.z);
        helper.getLevel().addFreshEntity(animal);
        return animal;
    }
    static void lamps(AnimalGymEntity gym, int expected) {
        for (int i = 1; i <= 2; i++) {
            var pos = gym.multiblock().transform().position(new BlockPos(-i, 0, 0));
            require(
                gym.getLevel().getBlockState(pos).getLightEmission(gym.getLevel(), pos) == expected,
                "Both lamps must emit " + expected + " while " + gym.training.status());
            require(MultiblockMembers.resolve(gym.getLevel(), pos) == gym.multiblock(),
                "Lamp switching preserves member ownership");
        }
    }
    @GameTest(template = "gym_empty")
    public static void lampsFollowWorkAndClearPropagatedLight(GameTestHelper helper) {
        var gym = place(helper, Direction.NORTH);
        var belt = gym.getBlockPos().west();
        var level = helper.getLevel();
        lamps(gym, 0);
        var cow = animal(helper, gym, EntityType.COW);
        cow.setNoAi(true);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 2), false);
        gym.serverTick();
        lamps(gym, 14);
        var saved = gym.saveWithFullMetadata(level.registryAccess());
        gym.loadWithComponents(saved, level.registryAccess());
        lamps(gym, 0);
        gym.serverTick();
        lamps(gym, 14);
        helper.runAfterDelay(5, () -> {
            require(
                level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, belt.above()) >= 12,
                "Working lamps illuminate the animal space");
            gym.control("toggle");
            lamps(gym, 0);
            helper.runAfterDelay(5, () -> {
                require(
                    level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                        belt.above()) < 12,
                    "Pause removes propagated block light");
                gym.control("toggle");
                gym.serverTick();
                lamps(gym, 14);
                helper.runAfterDelay(5, () -> {
                    require(
                        level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                            belt.above()) >= 12,
                        "Resuming relights the world");
                    gym.multiblock().dismantle(MultiblockInstance.Cause.CREATIVE);
                    require(level.getBlockState(belt).isAir(), "Dismantling removes light sources");
                    helper.runAfterDelay(5, () -> {
                        require(
                            level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                                belt.above()) < 12,
                            "Dismantling leaves no orphan light");
                        helper.succeed();
                    });
                });
            });
        });
    }
    @GameTest(template = "gym_empty")
    public static void catsAndDogsTrainWithTheirOwnFood(GameTestHelper helper) {
        var gym = place(helper, Direction.EAST);
        for (var type : java.util.List.of(EntityType.CAT, EntityType.WOLF)) {
            var pet = (net.minecraft.world.entity.TamableAnimal) animal(helper, gym, type);
            var owner = java.util.UUID.randomUUID();
            pet.setTame(true, true);
            pet.setOwnerUUID(owner);
            var food = type == EntityType.CAT ? Items.SALMON : Items.COOKED_BEEF;
            var wrong = type == EntityType.CAT ? Items.COOKED_BEEF : Items.SALMON;
            require(gym.food.insertItem(0, new ItemStack(wrong, 2), false).isEmpty(),
                "Pet food accepted by inventory");
            gym.serverTick();
            require(gym.getGeneration() == 0 && gym.food.getStackInSlot(0).getCount() == 2,
                "Pet does not eat another species' food");
            lamps(gym, 0);
            require(gym.food.insertItem(1, new ItemStack(food, 2), false).isEmpty(),
                "Suitable pet food accepted");
            gym.serverTick();
            require(
                gym.getGeneration() == (type == EntityType.CAT ? 4 : 6)
                    && !pet.getPersistentData().contains("industrial_overkill:fitness"),
                "Cat/dog trains and generates power");
            require(gym.displayFood().is(food) && gym.food.getStackInSlot(1).getCount() == 1,
                "One ration consumed and correct hanging food displayed");
            require(pet.isTame() && owner.equals(pet.getOwnerUUID()),
                "Training preserves pet ownership");
            lamps(gym, 14);
            gym.training.reset();
            pet.discard();
            gym.food.extractItem(0, 64, false);
            gym.food.extractItem(1, 64, false);
        }
        require(
            GymAnimalRecipe.food(helper.getLevel(), new ItemStack(Items.COD))
                && GymAnimalRecipe.food(helper.getLevel(), new ItemStack(Items.ROTTEN_FLESH)),
            "Vanilla cat and wolf food tags are accepted");
        require(!GymAnimalRecipe.food(helper.getLevel(), new ItemStack(Items.COOKED_COD)),
            "Cooked fish is not cat food");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void foodDisplayUsesAvailableEdibleFoodOnlyWhileWorking(GameTestHelper helper) {
        var gym = place(helper, Direction.NORTH);
        animal(helper, gym, EntityType.COW);
        gym.food.insertItem(0, new ItemStack(Items.CARROT, 8), false);
        gym.food.insertItem(1, new ItemStack(Items.WHEAT, 2), false);
        require(gym.displayFood().isEmpty(), "Idle gym has no food display");
        gym.serverTick();
        require(gym.displayFood().is(Items.WHEAT) && gym.displayFood().getCount() == 1,
            "Display chooses food edible by the animal, not the first occupied slot");
        require(gym.food.getStackInSlot(1).getCount() == 1,
            "Display consumes no food beyond the training ration");
        gym.food.extractItem(1, 1, false);
        require(gym.displayFood().is(Items.WHEAT), "The committed bite survives inventory edits");
        for (int tick = 0; tick < zone.moddev.mc.industrialoverkill.gym.GymFeeding.CONTACT; tick++)
            gym.serverTick();
        require(gym.getGeneration() > 0 && gym.displayFood().isEmpty(),
            "The bite removes food from the stick at contact");
        gym.food.insertItem(1, new ItemStack(Items.WHEAT, 3), false);
        gym.control("toggle");
        require(gym.displayFood().isEmpty(), "Pause hides the stocked food display");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void placementRotatesAndRejectsClearance(GameTestHelper helper) {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var gym = place(helper, facing);
            require(gym.multiblock().ready() && gym.multiblock().structure().members().size() == 3,
                "Exactly one controller and two belt members formed");
            require(
                gym.multiblock().structure().members().stream()
                    .allMatch(member -> member.offset().getY() == 0 && member.offset().getZ() == 0
                        && member.offset().getX() >= -2 && member.offset().getX() <= 0),
                "Footprint is exactly three adjacent base blocks");
            var cow = animal(helper, gym, EntityType.COW);
            require(gym.onBelt(cow), "Cow fits narrow belt in every orientation");
            gym.food.insertItem(0, new ItemStack(Items.WHEAT), false);
            gym.serverTick();
            GymAnimals.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(cow));
            var towardController = Vec3.atCenterOf(gym.getBlockPos()).subtract(cow.position())
                .multiply(1, 0, 1).normalize();
            require(cow.getLookAngle().dot(towardController) > .99,
                "Working animal faces controller in every orientation");
            require(cow.position().distanceTo(gym.beltCenter()) < .001,
                "Running animal remains centered on belt");
            cow.discard();
            for (var member : gym.multiblock().structure().members())
                require(
                    MultiblockMembers.resolve(helper.getLevel(),
                        gym.multiblock().transform().position(member.offset())) == gym.multiblock(),
                    "Reciprocal member");
            gym.multiblock().dismantle(MultiblockInstance.Cause.CREATIVE);
        }
        helper.getLevel().setBlock(origin(helper).west().above(), Blocks.STONE.defaultBlockState(),
            3);
        var use = context(helper, Direction.NORTH);
        require(!CommonHooks.onPlaceItemIntoWorld(use).consumesAction(),
            "Occupied animal space refused");
        require(use.getItemInHand().getCount() == 2
            && helper.getLevel().getBlockState(origin(helper)).isAir(), "Failure is atomic");
        helper.getLevel().removeBlock(origin(helper).west().above(), false);
        helper.getLevel().setBlock(origin(helper).above(), Blocks.STONE.defaultBlockState(), 3);
        require(!CommonHooks.onPlaceItemIntoWorld(use).consumesAction()
            && use.getItemInHand().getCount() == 2, "Controller gantry clearance protected");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void foodPowersAutomaticTrainingAndFullBufferStopsWork(GameTestHelper helper) {
        var gym = place(helper, Direction.NORTH);
        var cow = animal(helper, gym, EntityType.COW);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 4), false);
        gym.serverTick();
        require(
            gym.energy.stored() == 16
                && cow.getPersistentData().getInt("industrial_overkill:gym_ration") == 299,
            "Food powers training without input FE");
        require(gym.food.getStackInSlot(0).getCount() == 3, "One ration paid");
        gym.energy.setStored(gym.energy.capacity());
        gym.serverTick();
        require(gym.training.animal() == null && !cow.isNoAi(),
            "Full buffer releases animal without disabling AI");
        require(cow.getPersistentData().getInt("industrial_overkill:gym_ration") == 299
            && gym.food.getStackInSlot(0).getCount() == 3, "No full-buffer training/food use");
        lamps(gym, 0);
        gym.energy.setStored(0);
        gym.serverTick();
        require(cow.getPersistentData().getInt("industrial_overkill:gym_ration") == 298
            && gym.food.getStackInSlot(0).getCount() == 3, "Resume uses remaining ration");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void speciesFoodEligibilityAndIntensity(GameTestHelper helper) {
        var gym = place(helper, Direction.NORTH);
        var pig = animal(helper, gym, EntityType.PIG);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 2), false);
        gym.serverTick();
        require(gym.energy.stored() == 0 && gym.food.getStackInSlot(0).getCount() == 2,
            "Pig rejects wheat");
        gym.food.insertItem(1, new ItemStack(Items.CARROT, 2), false);
        gym.control("intensity");
        gym.control("intensity");
        gym.control("health");
        gym.serverTick();
        require(
            gym.energy.stored() == 24
                && pig.getPersistentData().getInt("industrial_overkill:gym_ration") == 477,
            "Intensity scales generation and food consumption");
        require(!gym.food.insertItem(2, new ItemStack(Items.DIAMOND), false).isEmpty(),
            "Nonfood rejected");
        gym.control("release");
        gym.serverTick();
        require(!gym.training.enabled() && gym.training.animal() == null && !pig.isNoAi(),
            "Release pauses");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void horseFitsAndOnlyOneGymOwnsAnAnimal(GameTestHelper helper) {
        var gym = place(helper, Direction.EAST);
        var horse = animal(helper, gym, EntityType.HORSE);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 2), false);
        gym.serverTick();
        require(gym.onBelt(horse) && gym.training.animal() == horse,
            "Adult horse fits rotated belt");
        var center = gym.beltCenter();
        horse.setPos(center.x + .6, center.y, center.z);
        require(!gym.onBelt(horse), "Horse beside the narrow belt is not acquired");
        horse.setPos(center.x, center.y, center.z);
        var contender = new AnimalGymEntity(origin(helper), gym.getBlockState());
        contender.setLevel(helper.getLevel());
        require(!GymAnimals.claim(horse, contender), "Competing owner refused");
        gym.onChunkUnloaded();
        require(gym.training.animal() == null && !horse.isNoAi(),
            "Chunk unload releases ownership");
        lamps(gym, 0);
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void legacyBonusesClearOnWorldJoinAndTrapLoad(GameTestHelper helper) {
        var horse = EntityType.HORSE.create(helper.getLevel());
        var unrelated = AnimalGym.id("test_unrelated_health");
        var health = horse.getAttribute(Attributes.MAX_HEALTH);
        health.addPermanentModifier(new AttributeModifier(unrelated, 2,
            AttributeModifier.Operation.ADD_VALUE));
        float normalHealth = horse.getMaxHealth();
        var attributes = java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.MAX_HEALTH,
            Attributes.KNOCKBACK_RESISTANCE, Attributes.JUMP_STRENGTH);
        var ids = java.util.List.of("gym_speed", "gym_health", "gym_strength", "gym_jump");
        for (int i = 0; i < ids.size(); i++)
            horse.getAttribute(attributes.get(i)).addPermanentModifier(new AttributeModifier(
                AnimalGym.id(ids.get(i)), 1, AttributeModifier.Operation.ADD_VALUE));
        horse.setHealth(horse.getMaxHealth());
        var scores = new CompoundTag();
        scores.putInt("HEALTH", 10000);
        horse.getPersistentData().put("industrial_overkill:fitness", scores);
        var saved = zone.moddev.mc.industrialoverkill.trap.MobTrapData.save(horse);
        var center = Vec3.atCenterOf(origin(helper));
        horse.setPos(center.x, center.y, center.z);
        helper.getLevel().addFreshEntity(horse);
        var captive = (Animal) zone.moddev.mc.industrialoverkill.trap.MobTrapData.load(saved,
            helper.getLevel());
        require(captive != null, "Legacy captive decodes");
        for (var animal : java.util.List.of(horse, captive)) {
            for (int i = 0; i < ids.size(); i++)
                require(
                    !animal.getAttribute(attributes.get(i)).hasModifier(AnimalGym.id(ids.get(i))),
                    "Retired gym bonus removed: " + ids.get(i));
            require(animal.getMaxHealth() == normalHealth && animal.getHealth() == normalHealth
                && animal.getAttribute(Attributes.MAX_HEALTH).hasModifier(unrelated),
                "Health clamps to normal maximum while unrelated modifiers survive");
            require(
                animal.getPersistentData().getCompound("industrial_overkill:fitness")
                    .equals(scores),
                "Old scores remain dormant without being rewritten");
        }
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void obstructionSuspendsCachedPortsAndReleasesAnimal(GameTestHelper helper) {
        var gym = place(helper, Direction.NORTH);
        var cow = animal(helper, gym, EntityType.COW);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 2), false);
        gym.serverTick();
        var port = MultiblockPorts.energy(gym, Direction.NORTH);
        require(port != null && !port.canReceive() && port.canExtract(),
            "Output-only guarded energy");
        var obstruction = origin(helper).west().above();
        helper.getLevel().setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
        gym.serverTick();
        require(
            gym.training.animal() == null && !cow.isNoAi() && port.extractEnergy(100, false) == 0,
            "Obstruction stops work and cached export");
        lamps(gym, 0);
        helper.getLevel().removeBlock(obstruction, false);
        gym.serverTick();
        require(gym.training.animal() == cow && port.canExtract(), "Same structure resumes");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void breakingBeltReleasesAndDropsOnce(GameTestHelper helper) {
        var gym = place(helper, Direction.NORTH);
        var cow = animal(helper, gym, EntityType.COW);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 4), false);
        gym.serverTick();
        var frame = origin(helper).west(2);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.getLevel().getBlockState(frame).getBlock().playerWillDestroy(helper.getLevel(),
            frame, helper.getLevel().getBlockState(frame), player);
        gym.multiblock().dismantle(MultiblockInstance.Cause.SURVIVAL);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(origin(helper)).inflate(6));
        require(drops.stream().filter(e -> e.getItem().is(AnimalGym.ITEM.get()))
            .mapToInt(e -> e.getItem().getCount()).sum() == 1, "One machine drop");
        require(drops.stream().filter(e -> e.getItem().is(Items.WHEAT))
            .mapToInt(e -> e.getItem().getCount()).sum() == 3, "Remaining food dropped once");
        require(cow.isAlive() && !cow.isNoAi() && gym.training.animal() == null,
            "Animal unharmed and released");
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void saveReloadSettingsAndInvalidControls(GameTestHelper helper) {
        var gym = place(helper, Direction.NORTH);
        var legacySettings = new CompoundTag();
        legacySettings.putString("mode", "STRENGTH");
        legacySettings.putInt("intensity", 1);
        gym.training.restore(legacySettings);
        require(!gym.training.state().contains("mode"), "Retired mode ignored on load");
        gym.control("strength");
        gym.control("intensity");
        gym.control("toggle");
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 8), false);
        gym.energy.setStored(1234);
        var saved = gym.saveWithFullMetadata(helper.getLevel().registryAccess());
        gym.loadWithComponents(saved, helper.getLevel().registryAccess());
        gym.control("arbitrary_remote_action");
        require(gym.training.intensity() == 2
            && !gym.training.enabled(), "Settings restored and unknown action ignored");
        require(gym.food.getStackInSlot(0).getCount() == 8 && gym.energy.stored() == 1234,
            "Storage retained");
        gym.serverTick();
        require(gym.multiblock().ready(), "Loaded manifest revalidated");
        helper.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void rationExhaustionAndIneligibleAnimalsStopWithoutDamage(
        GameTestHelper helper) {
        var gym = place(helper, Direction.NORTH);
        var cow = animal(helper, gym, EntityType.COW);
        float health = cow.getHealth();
        gym.food.insertItem(0, new ItemStack(Items.WHEAT), false);
        for (int tick = 0; tick < 300; tick++)
            gym.serverTick();
        require(
            gym.energy.stored() == 4800
                && !cow.getPersistentData().contains("industrial_overkill:fitness"),
            "One food pays for exactly 300 work units");
        lamps(gym, 14);
        gym.serverTick();
        require(gym.training.animal() == null && gym.energy.stored() == 4800
            && cow.getHealth() == health && !cow.isNoAi(),
            "Starvation releases without damage or free work");
        lamps(gym, 0);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT), false);
        cow.setAge(-1000);
        gym.serverTick();
        require(gym.training.animal() == null && gym.food.getStackInSlot(0).getCount() == 1,
            "Baby refused without consuming food");
        cow.setAge(0);
        gym.serverTick();
        require(gym.training.animal() == cow, "Adult reacquired");
        cow.discard();
        gym.serverTick();
        require(gym.training.animal() == null, "Lost animal releases ownership");
        lamps(gym, 0);
        helper.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void oversizedAnimalsFitTemporarilyWithoutChangingOriginalScale(
        GameTestHelper helper) {
        var gym = place(helper, Direction.SOUTH);
        var horse = animal(helper, gym, EntityType.HORSE);
        var scale = horse.getAttribute(Attributes.SCALE);
        scale.setBaseValue(2);
        var otherId = AnimalGym.id("test_existing_scale");
        scale.addPermanentModifier(new AttributeModifier(otherId, .25,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        horse.refreshDimensions();
        float originalScale = horse.getScale();
        float health = horse.getHealth();
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 3), false);
        gym.serverTick();
        require(gym.training.animal() == horse, "Oversized animal acquired by its feet");
        require(horse.getBbWidth() <= GymAnimalFit.MAX_WIDTH + .001
            && horse.getBbHeight() <= GymAnimalFit.MAX_HEIGHT + .001,
            "Actual dimensions fit belt and overhead frame");
        float fittedScale = horse.getScale();
        for (int i = 0; i < 20; i++)
            gym.serverTick();
        require(Math.abs(horse.getScale() - fittedScale) < .0001, "Fit never stacks or oscillates");
        var saved = new CompoundTag();
        horse.saveWithoutId(saved);
        var restored = EntityType.HORSE.create(helper.getLevel());
        restored.load(saved);
        require(Math.abs(restored.getScale() - originalScale) < .0001,
            "Temporary shrinking is not persisted in animal saves");
        gym.control("release");
        require(Math.abs(horse.getScale() - originalScale) < .0001
            && scale.hasModifier(otherId) && horse.getHealth() == health,
            "Release preserves original scale, other modifiers and health");
        gym.control("toggle");
        var center = gym.beltCenter();
        horse.setPos(center.x, center.y, center.z);
        gym.serverTick();
        require(horse.getScale() < originalScale, "Resume reapplies fit");
        gym.onChunkUnloaded();
        require(Math.abs(horse.getScale() - originalScale) < .0001, "Unload restores size");
        horse.discard();
        var pig = animal(helper, gym, EntityType.PIG);
        pig.getAttribute(Attributes.SCALE).setBaseValue(.5);
        pig.refreshDimensions();
        gym.food.insertItem(1, new ItemStack(Items.CARROT), false);
        gym.serverTick();
        require(gym.training.animal() == pig && pig.getScale() == .5f,
            "Already small animals are not enlarged");
        helper.succeed();
    }

}

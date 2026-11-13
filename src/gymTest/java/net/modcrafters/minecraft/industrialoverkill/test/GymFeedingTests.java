package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.animal;
import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.place;
import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.gym.GymFeeding;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;
import zone.moddev.mc.lib.machine.neoforge.MachineItemInteractions;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GymFeedingTests {
    @GameTest(template = "gym_empty")
    public static void rightClickFoodMergesThenUsesEmptySlotsWithoutInterruptingTraining(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        animal(h, gym, EntityType.COW).setNoAi(true);
        gym.food.insertItem(1, new ItemStack(Items.WHEAT, 64), false);
        gym.serverTick();
        int generation = gym.training.generation();
        require(generation > 0 && gym.food.getStackInSlot(1).getCount() == 63, "Gym is already training with a paid serving");
        var serving = gym.feeding.serving().copy();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 4));
        var member = gym.getBlockPos().west();
        var hit = new BlockHitResult(Vec3.atCenterOf(member), Direction.UP, member, false);
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
            "Food offered to a linked gym part reaches its controller receiver");
        require(gym.food.getStackInSlot(0).isEmpty() && gym.food.getStackInSlot(1).getCount() == 64
            && player.getMainHandItem().getCount() == 3, "One food item tops up an existing stack before using an empty slot");
        require(gym.training.generation() == generation && ItemStack.matches(serving, gym.feeding.serving()),
            "Adding food preserves current training and the paid serving");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WHEAT, 2));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.OFF_HAND, hit)).consumesAction(),
            "Offhand food uses the same receiver");
        require(gym.food.getStackInSlot(0).getCount() == 1 && player.getOffhandItem().getCount() == 1
            && gym.food.getStackInSlot(3).isEmpty(), "The next item uses an empty food slot, never the occupant slot");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void foodReceiverRejectsFullSlotsAndKeepsCreativeFood(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        for (int slot = 0; slot < 3; slot++) gym.food.insertItem(slot, new ItemStack(Items.WHEAT, 64), false);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var hit = new BlockHitResult(Vec3.atCenterOf(gym.getBlockPos()), Direction.UP, gym.getBlockPos(), false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 3));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.FAIL,
            "Full food slots reject recognized food");
        require(player.getMainHandItem().getCount() == 3 && gym.food.getStackInSlot(3).isEmpty(),
            "Rejected food remains in hand and cannot enter the occupant slot");
        for (int slot = 0; slot < 3; slot++) require(gym.food.getStackInSlot(slot).getCount() == 64, "Rejection leaves food slots unchanged");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.PASS,
            "Unsupported items retain their normal interaction");
        gym.food.extractItem(2, 64, false);
        var creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 4));
        require(MachineItemInteractions.interact(new UseOnContext(creative, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && creative.getMainHandItem().getCount() == 4 && gym.food.getStackInSlot(2).getCount() == 1,
            "Creative adds one food item while retaining the held food");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void servingIsPaidOnceEatenThenRefilledAfterChewing(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        var cow = animal(h, gym, EntityType.COW);
        cow.setNoAi(true);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 4), false);
        gym.serverTick();
        require(gym.feeding.age(0) == 0 && gym.food.getStackInSlot(0).getCount() == 3,
            "First bite starts with one paid serving");
        require(gym.displayFood().is(Items.WHEAT), "Serving visible during approach");
        for (int i = 0; i < GymFeeding.CONTACT; i++)
            gym.serverTick();
        require(gym.displayFood().isEmpty() && gym.feeding.crumbs().is(Items.WHEAT),
            "Serving vanishes at mouth contact; matching crumbs retained");
        for (int i = GymFeeding.CONTACT; i < GymFeeding.REFILL - 1; i++)
            gym.serverTick();
        require(gym.displayFood().isEmpty() && gym.food.getStackInSlot(0).getCount() == 3,
            "Chewing and one-second refill delay do not charge extra food");
        gym.serverTick();
        require(
            gym.displayFood().is(Items.WHEAT) && gym.feeding.serving().getCount() == 1
                && gym.food.getStackInSlot(0).getCount() == 2,
            "One fresh serving moves from inventory onto stick");
        require(cow.getPersistentData().getInt("industrial_overkill:gym_ration") == 300
            - GymFeeding.REFILL - 1, "Refill grants no bonus ration");
        for (int i = GymFeeding.REFILL; i < 300; i++)
            gym.serverTick();
        require(
            gym.feeding.age(0) == 0 && gym.food.getStackInSlot(0).getCount() == 2
                && gym.feeding.serving().isEmpty(),
            "Next bite consumes the reserved serving, not another inventory item");
        h.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void reservedServingSurvivesPauseReloadAndDismantling(GameTestHelper h) {
        var gym = place(h, Direction.EAST);
        var cow = animal(h, gym, EntityType.COW);
        cow.setNoAi(true);
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 2), false);
        for (int i = 0; i <= GymFeeding.REFILL; i++)
            gym.serverTick();
        require(gym.food.getStackInSlot(0).isEmpty() && gym.feeding.serving().is(Items.WHEAT),
            "Last inventory serving is owned by stick");
        gym.control("toggle");
        for (int i = 0; i < 30; i++)
            gym.serverTick();
        require(gym.displayFood().isEmpty() && gym.feeding.serving().is(Items.WHEAT),
            "Pause hides but retains serving");
        var saved = gym.saveWithFullMetadata(h.getLevel().registryAccess());
        gym.loadWithComponents(saved, h.getLevel().registryAccess());
        require(gym.feeding.serving().getCount() == 1, "Reload preserves exact serving");
        gym.control("toggle");
        gym.serverTick();
        require(gym.displayFood().is(Items.WHEAT),
            "Resume reuses paid serving with empty inventory");
        var bounds = gym.renderBounds().inflate(2);
        gym.multiblock().dismantle(MultiblockInstance.Cause.CREATIVE);
        int count = h.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
            .map(ItemEntity::getItem).filter(stack -> stack.is(Items.WHEAT))
            .mapToInt(ItemStack::getCount).sum();
        require(count == 1,
            "Dismantling returns reserved serving exactly once, never eaten crumbs");
        h.succeed();
    }
}

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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;
import net.modcrafters.minecraft.industrialoverkill.gym.GymFeeding;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
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
        require(gym.food.getStackInSlot(0).getCount() == 3 && gym.food.getStackInSlot(1).getCount() == 64
            && player.getMainHandItem().isEmpty(), "The whole stack tops up existing food before using an empty slot");
        require(gym.training.generation() == generation && ItemStack.matches(serving, gym.feeding.serving()),
            "Adding food preserves current training and the paid serving");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WHEAT, 2));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.OFF_HAND, hit)).consumesAction(),
            "Offhand food uses the same receiver");
        require(gym.food.getStackInSlot(0).getCount() == 5 && player.getOffhandItem().isEmpty()
            && gym.food.getStackInSlot(3).isEmpty(), "The whole offhand stack tops up food, never the occupant slot");
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
        gym.food.extractItem(0, 2, false);
        gym.food.extractItem(1, 3, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 12));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && player.getMainHandItem().is(Items.WHEAT) && player.getMainHandItem().getCount() == 7,
            "A partial deposit across slots leaves every excess item in the same hand");
        for (int slot = 0; slot < 3; slot++) require(gym.food.getStackInSlot(slot).getCount() == 64, "Partial deposit fills available space");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.PASS,
            "Unsupported items retain their normal interaction");
        gym.food.extractItem(2, 64, false);
        var creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 4));
        require(MachineItemInteractions.interact(new UseOnContext(creative, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && creative.getMainHandItem().getCount() == 4 && gym.food.getStackInSlot(2).getCount() == 4,
            "Creative deposits the whole stack while retaining the held food");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void foodReceiverUsesCurrentAnimalEvenWhilePaused(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var hit = new BlockHitResult(Vec3.atCenterOf(gym.getBlockPos()), Direction.UP, gym.getBlockPos(), false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 8));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && player.getMainHandItem().isEmpty(), "An empty gym accepts any supported animal food");
        var cow = animal(h, gym, EntityType.COW);
        cow.setNoAi(true);
        gym.control("toggle");
        require(gym.training.animal() == null, "Paused gym has no active training claim");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 4));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.FAIL
            && player.getMainHandItem().getCount() == 4 && gym.food.getStackInSlot(0).getCount() == 8,
            "The paused cow rejects chicken food without changing hand or storage");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 6));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && player.getMainHandItem().isEmpty() && gym.food.getStackInSlot(1).getCount() == 6,
            "The paused cow accepts its food");
        cow.discard();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 4));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && gym.food.getStackInSlot(0).getCount() == 12, "Removing the animal restores general food acceptance");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void foodReceiverPrioritizesCourierOccupant(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        animal(h, gym, EntityType.COW).setNoAi(true);
        var pig = EntityType.PIG.create(h.getLevel());
        gym.food.insertItem(3, CritterCourierProcessor.OCCUPANT.stack(CritterCourierProcessor.ENTITIES.capture(pig)), false);
        var installed = gym.food.getStackInSlot(3).copy();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var hit = new BlockHitResult(Vec3.atCenterOf(gym.getBlockPos()), Direction.UP, gym.getBlockPos(), false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 4));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.FAIL
            && player.getMainHandItem().getCount() == 4 && gym.food.getStackInSlot(0).isEmpty(),
            "The captive pig's diet takes priority over a loose cow on the belt");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT, 12));
        require(MachineItemInteractions.interact(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && player.getMainHandItem().isEmpty() && gym.food.getStackInSlot(0).getCount() == 12
            && ItemStack.matches(installed, gym.food.getStackInSlot(3)), "Pig food enters food slots without changing the Courier");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void foodSlotsShareLiveDietRulesWithGuiAndAutomation(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, gym.getBlockPos(), Direction.NORTH);
        require(port != null, "Gym exposes its food insertion capability");
        require(port.insertItem(0, new ItemStack(Items.WHEAT_SEEDS, 8), false).isEmpty(),
            "Empty gym accepts supported food through automation");
        var cow = animal(h, gym, EntityType.COW);
        cow.setNoAi(true);
        gym.control("toggle");
        var player = h.makeMockServerPlayerInLevel();
        var pos = gym.getBlockPos();
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        var menu = new MachineMenu(84, player.getInventory(), gym);
        player.containerMenu = menu;
        try {
            var wheat = new ItemStack(Items.WHEAT, 4);
            var seeds = new ItemStack(Items.WHEAT_SEEDS, 4);
            for (int slot = 0; slot < 3; slot++) {
                var guiSlot = menu.getSlot(menu.menuSlot(slot));
                require(guiSlot.mayPlace(wheat) && !guiSlot.mayPlace(seeds), "Every GUI food slot follows the paused cow's diet");
                require(port.isItemValid(slot, wheat) && !port.isItemValid(slot, seeds), "Hoppers see the same slot filter");
                require(ItemStack.matches(port.insertItem(slot, seeds, true), seeds)
                    && ItemStack.matches(port.insertItem(slot, seeds, false), seeds), "Simulation and insertion both reject wrong food");
            }
            menu.setCarried(seeds.copy());
            menu.clicked(menu.menuSlot(1), 0, ClickType.PICKUP, player);
            require(ItemStack.matches(menu.getCarried(), seeds) && gym.food.getStackInSlot(1).isEmpty(),
                "Rejected GUI insertion keeps the cursor stack");
            menu.setCarried(wheat.copy());
            menu.clicked(menu.menuSlot(1), 0, ClickType.PICKUP, player);
            require(menu.getCarried().isEmpty() && gym.food.getStackInSlot(1).getCount() == 4, "GUI inserts suitable food");
            require(port.insertItem(2, wheat, true).isEmpty() && gym.food.getStackInSlot(2).isEmpty(),
                "Hopper simulation accepts suitable food without changing storage");
            require(port.insertItem(2, wheat, false).isEmpty() && gym.food.getStackInSlot(2).getCount() == 4,
                "Hopper insertion stores suitable food");
            gym.food.insertItem(3, CritterCourierProcessor.OCCUPANT.stack(CritterCourierProcessor.ENTITIES.capture(EntityType.PIG.create(h.getLevel()))), false);
            var carrots = new ItemStack(Items.CARROT);
            for (int slot = 0; slot < 3; slot++) {
                var guiSlot = menu.getSlot(menu.menuSlot(slot));
                require(!guiSlot.mayPlace(wheat) && guiSlot.mayPlace(carrots)
                    && !port.isItemValid(slot, wheat) && port.isItemValid(slot, carrots),
                    "An already-open GUI and cached hopper handler follow a new Courier occupant");
            }
            require(gym.food.getStackInSlot(0).getCount() == 8 && gym.food.getStackInSlot(1).getCount() == 4,
                "Animal changes preserve previously stored food");
            menu.clicked(menu.menuSlot(0), 0, ClickType.PICKUP, player);
            require(menu.getCarried().is(Items.WHEAT_SEEDS) && menu.getCarried().getCount() == 8,
                "Food the new animal cannot eat can still be removed through the GUI");
            gym.food.extractItem(3, 1, false);
            cow.discard();
            require(menu.getSlot(menu.menuSlot(0)).mayPlace(seeds) && port.isItemValid(0, seeds)
                && !port.isItemValid(0, new ItemStack(Items.STONE)), "Removing all animals restores supported-food filtering");
        } finally {
            menu.removed(player);
        }
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

package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.animal;
import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.place;
import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.gym.GymFeeding;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GymFeedingTests {
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

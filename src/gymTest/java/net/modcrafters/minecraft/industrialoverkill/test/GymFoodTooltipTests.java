package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.animal;
import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.place;
import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierData;
import net.modcrafters.minecraft.industrialoverkill.gym.GymAnimalRecipe;
import net.modcrafters.minecraft.industrialoverkill.gym.GymScreenElements;
import zone.moddev.mc.lib.gui.model.Common;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GymFoodTooltipTests {
    private static boolean hasKey(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents text
            && text.getKey().equals(key));
    }

    private static Component foodLine(Item item) {
        return Component.translatable("gym.food.accepted_item", new ItemStack(item).getHoverName());
    }

    @GameTest(template = "gym_empty")
    public static void emptyAndOccupiedSlotsShowCurrentDietWhilePaused(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        var slots = List.of(new GymScreenElements.FoodSlot(Common.empty(), 0),
            new GymScreenElements.FoodSlot(Common.empty(), 1), new GymScreenElements.FoodSlot(Common.empty(), 2));
        for (var slot : slots)
            require(hasKey(slot.tooltip(gym, ItemStack.EMPTY), "gym.food.no_animal"), "Empty gym explains how to see its diet");
        gym.food.insertItem(0, new ItemStack(Items.CARROT), false);
        var cow = animal(h, gym, EntityType.COW);
        cow.setNoAi(true);
        gym.control("toggle");
        gym.food.insertItem(1, new ItemStack(Items.WHEAT), false);
        for (var slot : slots) {
            var lines = slot.tooltip(gym, ItemStack.EMPTY);
            require(lines.contains(foodLine(Items.WHEAT)) && !lines.contains(foodLine(Items.CARROT)),
                "Every food slot lists the paused cow's diet, regardless of its stored item");
        }
        require(hasKey(slots.get(0).tooltip(gym, ItemStack.EMPTY), "gym.food.unsuitable"), "Leftover unsuitable food keeps its warning");
        require(hasKey(slots.get(1).tooltip(gym, ItemStack.EMPTY), "gym.food.total_energy"), "Suitable food keeps its energy details");
        gym.food.insertItem(3, CritterCourierData.stack(CritterCourierData.save(EntityType.HORSE.create(h.getLevel()))), false);
        var horseLines = slots.get(2).tooltip(gym, ItemStack.EMPTY);
        for (var item : List.of(Items.WHEAT, Items.APPLE, Items.SUGAR, Items.HAY_BLOCK, Items.GOLDEN_APPLE, Items.GOLDEN_CARROT))
            require(horseLines.contains(foodLine(item)), "Courier occupant lists every horse food tier: " + item);
        for (var food : GymAnimalRecipe.foodsFor(gym.captive.animal()))
            require(gym.food.isItemValid(0, food), "Every listed food matches insertion filtering");
        gym.food.extractItem(3, 1, false);
        gym.food.insertItem(3, CritterCourierData.stack(CritterCourierData.save(EntityType.ZOMBIE.create(h.getLevel()))), false);
        require(hasKey(slots.get(2).tooltip(gym, ItemStack.EMPTY), "gym.food.none"), "Unsupported Courier does not show the loose cow's diet");
        gym.food.extractItem(3, 1, false);
        cow.discard();
        require(hasKey(slots.get(2).tooltip(gym, ItemStack.EMPTY), "gym.food.no_animal"), "Removing the animal clears its diet");
        h.succeed();
    }
}

package net.modcrafters.minecraft.industrialoverkill.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

import net.modcrafters.minecraft.industrialoverkill.gym.GymAnimalRecipe;

import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.client.recipe.RecipeCatalog;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGymEntity;

public final class GymRecipeCatalog implements RecipeCatalog {
    private static long revision;
    public static void invalidate() { revision++; }
    private final MachineScreen screen;
    private net.minecraft.world.level.Level level;
    private long seenRevision = -1;
    private List<Group> groups = List.of();
    public GymRecipeCatalog(MachineScreen screen) { this.screen = screen; }

    @Override public List<Group> groups() {
        var current = Minecraft.getInstance().level;
        if (current == level && seenRevision == revision) return groups;
        level = current;
        seenRevision = revision;
        groups = recipes().stream().map(entry -> {
            var animal = entry.animal();
            var egg = net.minecraft.world.item.SpawnEggItem.byId(animal.getType());
            var choices = new ArrayList<Choice>();
            for (int tier = 0; tier < entry.tiers().size(); tier += 3) {
                var card = new GymRecipeDisplay.Entry(animal, entry.tiers().subList(tier, Math.min(tier + 3, entry.tiers().size())));
                choices.add(new Choice(Integer.toString(tier), new GymRecipeDisplay().presentation(card,
                    () -> ((AnimalGymEntity) screen.getMenu().blockEntity).getIntensity())));
            }
            return new Group(BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).toString(),
                animal.getType().getDescription(), egg == null ? ItemStack.EMPTY : new ItemStack(egg), choices);
        }).toList();
        return groups;
    }
    @Override public String initialGroup() {
        var gym = (AnimalGymEntity) screen.getMenu().blockEntity;
        var animal = gym.captivePreview();
        if (animal == null && gym.getLevel() != null
            && gym.getLevel().getEntity(gym.getAnimalId()) instanceof net.minecraft.world.entity.Mob live)
            animal = live;
        return animal == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).toString();
    }
    // Informational recipes: the installed animal and supplied food determine production.
    @Override public boolean select(Group group, Choice choice) { return false; }

    /** Discover from synchronized recipes, with identical food priority in both viewers. */
    public static List<GymRecipeDisplay.Entry> recipes() {
        var level = Minecraft.getInstance().level;
        if (level == null)
            return List.of();
        var result = new ArrayList<GymRecipeDisplay.Entry>();
        var types = BuiltInRegistries.ENTITY_TYPE.stream()
            .sorted(
                Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
            .toList();
        var recipes = level.getRecipeManager().getAllRecipesFor(GymAnimalRecipe.TYPE.get())
            .stream()
            .sorted(Comparator.comparing(recipe -> recipe.id().toString()))
            .map(holder -> holder.value()).filter(GymAnimalRecipe::enabled).toList();
        for (var type : types) {
            if (recipes.stream().noneMatch(recipe -> recipe.entity().matches(type)))
                continue;
            if (!(type.create(level) instanceof Animal animal))
                continue;
            animal.setAge(0);
            record Rates(int power, int duration) {
            }
            var groups = new java.util.LinkedHashMap<Rates, List<ItemStack>>();
            for (var recipe : recipes) {
                if (!recipe.entity().matches(type))
                    continue;
                // Match the gym's priority before grouping recipes with identical effects.
                var foods = java.util.Arrays.stream(recipe.food().getItems())
                    .filter(stack -> GymAnimalRecipe.find(animal, stack) == recipe)
                    .map(stack -> stack.copyWithCount(1)).toList();
                if (!foods.isEmpty())
                    groups.computeIfAbsent(
                        new Rates(recipe.energyPerTick(), recipe.foodDurationTicks()),
                        ignored -> new ArrayList<>()).addAll(foods);
            }
            var tiers = groups.entrySet().stream()
                .sorted(Comparator.comparingInt(
                    (java.util.Map.Entry<Rates, List<ItemStack>> group) -> group.getKey().power())
                    .thenComparingInt(group -> group.getKey().duration()))
                .map(group -> new GymRecipeDisplay.FoodTier(group.getKey().power(),
                    group.getKey().duration(), List.copyOf(group.getValue())))
                .toList();
            if (!tiers.isEmpty())
                // Preview only: one detached animal per card, never added to the world or ticked.
                result.add(new GymRecipeDisplay.Entry(animal, tiers));
        }
        return List.copyOf(result);
    }
}

package zone.moddev.mc.industrialoverkill.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.industrialoverkill.gym.AnimalGym;
import zone.moddev.mc.industrialoverkill.gym.GymAnimalRecipe;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

/** Loaded exclusively by JEI; no common code references the optional integration. */
@JeiPlugin
public final class GymJeiPlugin implements IModPlugin {
    private List<GymRecipeCategory.Entry> entries = List.of();
    @Override
    public ResourceLocation getPluginUid() {
        return AnimalGym.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        entries = recipes();
        registration.addRecipeCategories(
            new GymRecipeCategory(registration.getJeiHelpers().getGuiHelper(), entries));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(AnimalGym.ITEM.get()), GymRecipeCategory.TYPE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(GymRecipeCategory.TYPE, entries);
    }

    /** JEI rebuilds these from synchronized recipes on login and datapack reload. */
    public static List<GymRecipeCategory.Entry> recipes() {
        var level = Minecraft.getInstance().level;
        if (level == null)
            return List.of();
        var result = new ArrayList<GymRecipeCategory.Entry>();
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
                .map(group -> new GymRecipeCategory.FoodTier(group.getKey().power(),
                    group.getKey().duration(), List.copyOf(group.getValue())))
                .toList();
            if (!tiers.isEmpty())
                // Preview only: one detached animal per card, never added to the world or ticked.
                result.add(new GymRecipeCategory.Entry(animal, tiers));
        }
        return List.copyOf(result);
    }
}

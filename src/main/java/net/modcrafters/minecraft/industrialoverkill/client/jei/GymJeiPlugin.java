package net.modcrafters.minecraft.industrialoverkill.client.jei;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym;
import net.modcrafters.minecraft.industrialoverkill.client.GymRecipeCatalog;
import net.modcrafters.minecraft.industrialoverkill.client.GymRecipeDisplay;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

/** Loaded exclusively by JEI; no common code references the optional integration. */
@JeiPlugin
public final class GymJeiPlugin implements IModPlugin {
    private List<GymRecipeDisplay.Entry> entries = List.of();
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

    public static List<GymRecipeDisplay.Entry> recipes() { return GymRecipeCatalog.recipes(); }

    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        net.modcrafters.minecraft.industrialoverkill.client.GymClient.recipeViewer = () -> {
            runtime.getRecipesGui().showTypes(List.of(GymRecipeCategory.TYPE));
            return true;
        };
    }
    @Override public void onRuntimeUnavailable() {
        net.modcrafters.minecraft.industrialoverkill.client.GymClient.recipeViewer = () -> false;
    }
}

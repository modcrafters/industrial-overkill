package net.modcrafters.minecraft.industrialoverkill.client.jei;

import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.modcrafters.minecraft.industrialoverkill.client.GeneratorRecipeCatalog;
import net.modcrafters.minecraft.industrialoverkill.client.GeneratorRecipeCatalog.Entry;
import net.modcrafters.minecraft.industrialoverkill.power.OmnivorousGenerator;
import zone.moddev.mc.lib.client.jei.PresentedRecipeCategory;
import zone.moddev.mc.lib.gui.client.recipe.RecipePresentation;

@JeiPlugin
public final class GeneratorJeiPlugin implements IModPlugin {
    public static final RecipeType<Entry> TYPE = new RecipeType<>(OmnivorousGenerator.ID, Entry.class);
    private List<Entry> entries = List.of();

    @Override public ResourceLocation getPluginUid() {
        return OmnivorousGenerator.ID.withPath("generator_jei");
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        entries = GeneratorRecipeCatalog.recipes(Minecraft.getInstance().level);
        var generator = new ItemStack(OmnivorousGenerator.BLOCK.get());
        var icon = registration.getJeiHelpers().getGuiHelper().createDrawableItemStack(generator);
        int width = entries.stream().mapToInt(entry -> entry.view().width()).max().orElse(1);
        int height = entries.stream().mapToInt(entry -> entry.view().height()).max().orElse(1);
        registration.addRecipeCategories(new PresentedRecipeCategory<>(TYPE, generator.getHoverName(), icon, width, height) {
            @Override public RecipePresentation presentation(Entry entry) { return entry.view(); }
            @Override public ResourceLocation getRegistryName(Entry entry) { return entry.id(); }
        });
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(TYPE, entries);
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(OmnivorousGenerator.BLOCK.get()), TYPE);
    }
}

package net.modcrafters.minecraft.industrialoverkill.client.jei;

import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.modcrafters.minecraft.industrialoverkill.client.RockStewTreeRenderer;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewRecipes;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewTree;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** One JEI recipe per complete production route, including retained and consumed source fluids. */
public final class RockStewTreeCategory extends zone.moddev.mc.lib.client.jei.PresentedRecipeCategory<RockStewTreeCategory.Entry> {
    public record Entry(ResourceLocation source, int node, RockStewTree.Row row) {}
    public static final RecipeType<Entry> TYPE = RecipeType.create("industrial_overkill", "rock_stew_tree", Entry.class);

    public RockStewTreeCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("stew.recipes"),
            gui.createDrawableItemStack(new ItemStack(MachineContent.item(RockStewRecipes.id("rock_stew_factory")))), RockStewTreeRenderer.WIDTH, RockStewTreeRenderer.ROW_HEIGHT);
    }

    @Override public zone.moddev.mc.lib.gui.client.recipe.RecipePresentation presentation(Entry entry) {
        return RockStewTreeRenderer.presentation(entry.row());
    }
    @Override public ResourceLocation getRegistryName(Entry entry) {
        return RockStewRecipes.id("tree/" + entry.source().getNamespace() + "/" + entry.source().getPath() + "/" + entry.node());
    }
}

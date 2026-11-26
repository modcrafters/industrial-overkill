package net.modcrafters.minecraft.industrialoverkill.client.jei;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.modcrafters.minecraft.industrialoverkill.client.FarmRecipeView;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmRecipeBooks;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmSupplies;
import net.modcrafters.minecraft.industrialoverkill.waste.FertilizerMakerProcessor;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** Optional JEI integration for the maker's custom processor. */
@JeiPlugin
public final class FertilizerMakerJeiPlugin implements IModPlugin {
    private record Entry(ResourceLocation id, FarmRecipeView view) {}

    private static final RecipeType<Entry> TYPE = RecipeType.create(
        FarmRecipeBooks.FERTILIZER_MAKER.getNamespace(), FarmRecipeBooks.FERTILIZER_MAKER.getPath(), Entry.class);
    private List<Entry> entries = List.of();

    @Override
    public ResourceLocation getPluginUid() {
        return FarmSupplies.id("fertilizer_maker_jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        entries = FertilizerMakerProcessor.recipePreviews().stream()
            .map(recipe -> new Entry(recipe.id(), new FarmRecipeView(recipe.recipe())))
            .filter(entry -> entry.view().available()).toList();
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper(), entries));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(TYPE, entries);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(icon(), TYPE);
    }

    private static ItemStack icon() {
        return new ItemStack(MachineContent.item(FarmRecipeBooks.FERTILIZER_MAKER));
    }

    private static final class Category extends AbstractRecipeCategory<Entry> {
        private Category(IGuiHelper gui, List<Entry> entries) {
            super(TYPE, Component.translatable("block.industrial_overkill.fertilizer_maker"),
                gui.createDrawableItemStack(icon()),
                entries.stream().mapToInt(entry -> entry.view().width()).max().orElse(1),
                entries.stream().mapToInt(entry -> entry.view().height()).max().orElse(1));
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
            for (var cell : entry.view().itemCells())
                builder.addSlot(RecipeIngredientRole.valueOf(cell.role().name()), cell.x(), cell.y())
                    .addItemStacks(cell.stacks())
                    .addRichTooltipCallback((slot, tooltip) -> cell.notes().forEach(tooltip::add));
            for (var cell : entry.view().fluidCells())
                builder.addSlot(RecipeIngredientRole.valueOf(cell.role().name()), cell.x(), cell.y())
                    .setFluidRenderer(cell.stacks().getFirst().getAmount(), false, 16, 16)
                    .addIngredients(NeoForgeTypes.FLUID_STACK, cell.stacks())
                    .addRichTooltipCallback((slot, tooltip) -> cell.notes().forEach(tooltip::add));
        }

        @Override
        public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            entry.view().drawFrame(graphics);
        }

        @Override
        public ResourceLocation getRegistryName(Entry entry) {
            return entry.id();
        }
    }
}

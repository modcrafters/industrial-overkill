package net.modcrafters.minecraft.industrialoverkill.client.jei;

import static net.modcrafters.minecraft.industrialoverkill.client.GymRecipeDisplay.INTENSITY_X;
import static net.modcrafters.minecraft.industrialoverkill.client.GymRecipeDisplay.INTENSITY_WIDTH;
import static net.modcrafters.minecraft.industrialoverkill.client.GymRecipeDisplay.INTENSITY_HEIGHT;

import net.modcrafters.minecraft.industrialoverkill.client.GymRecipeDisplay;
import net.modcrafters.minecraft.industrialoverkill.client.GymRecipeDisplay.Entry;
import net.modcrafters.minecraft.industrialoverkill.client.GymRecipeDisplay.FoodTier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

public final class GymRecipeCategory extends AbstractRecipeCategory<Entry> {
    // Shared by the cards in this category, so comparisons use the same intensity.
    private final GymRecipeDisplay display = new GymRecipeDisplay();
    public static final RecipeType<Entry> TYPE = RecipeType.create("industrial_overkill",
        "gym_animal", Entry.class);
    public GymRecipeCategory(IGuiHelper gui, java.util.List<Entry> entries) {
        super(TYPE, Component.translatable("jei.industrial_overkill.gym.title"),
            gui.createDrawableItemStack(new ItemStack(AnimalGym.ITEM.get())), 220,
            // JEI requires one height for the entire category, including datapack-added tiers.
            Math.max(86, entries.stream().mapToInt(GymRecipeDisplay::height).max()
                .orElse(86)));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        for (int tierIndex = 0; tierIndex < entry.tiers().size(); tierIndex++) {
            var tier = entry.tiers().get(tierIndex);
            var foods = tier.foods();
            // Alternatives, not simultaneous inputs. Very large tags cycle through every item.
            for (int i = 0; i < Math.min(7, foods.size()); i++) {
                builder.addSlot(RecipeIngredientRole.INPUT, 82 + i * 18, 22 + tierIndex * 24)
                    .addItemStacks(GymRecipeDisplay.alternatives(tier, i))
                    .addRichTooltipCallback((slot, tooltip) -> display.foodTooltip(tier).forEach(tooltip::add));
            }
        }
        // Spawn eggs make the animal searchable without implying the egg is consumed.
        var egg = SpawnEggItem.byId(entry.animal().getType());
        if (egg != null)
            builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST)
                .addItemStack(new ItemStack(egg));
    }

    @Override
    public void createRecipeExtras(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder builder,
        Entry entry, IFocusGroup focuses) {
        builder.addTooltipArea(INTENSITY_X, 0, INTENSITY_WIDTH, INTENSITY_HEIGHT)
            .setTooltip(Component.translatable("jei.industrial_overkill.gym.selector_tooltip"));
        builder.addInputHandler(new mezz.jei.api.gui.inputs.IJeiInputHandler() {
            @Override
            public net.minecraft.client.gui.navigation.ScreenRectangle getArea() {
                return new net.minecraft.client.gui.navigation.ScreenRectangle(INTENSITY_X, 0,
                    INTENSITY_WIDTH, INTENSITY_HEIGHT);
            }
            @Override
            public boolean handleInput(double x, double y,
                mezz.jei.api.gui.inputs.IJeiUserInput input) {
                if (input.getKey()
                    .getType() != com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
                    || input.getKey().getValue() != 0 || x < 0 || x >= INTENSITY_WIDTH
                    || y < 0 || y >= INTENSITY_HEIGHT)
                    return false;
                if (!input.isSimulate())
                    display.intensity(display.intensity() % 3 + 1);
                return true;
            }
        });
    }

    public int intensity() { return display.intensity(); }
    public int power(FoodTier tier) { return display.power(tier); }
    public double durationSeconds(FoodTier tier) { return display.durationSeconds(tier); }

    @Override
    public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        display.draw(entry, graphics, mouseX, mouseY, true);
    }

    @Override
    public ResourceLocation getRegistryName(Entry entry) {
        var animal = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
            .getKey(entry.animal().getType());
        // Stable across food rule additions, removals, and renames.
        return AnimalGym.id("gym_animal/" + animal.getNamespace() + "/" + animal.getPath());
    }
}

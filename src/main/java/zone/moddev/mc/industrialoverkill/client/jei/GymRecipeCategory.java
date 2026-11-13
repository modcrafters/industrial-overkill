package zone.moddev.mc.industrialoverkill.client.jei;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import zone.moddev.mc.industrialoverkill.gym.AnimalGym;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

public final class GymRecipeCategory extends AbstractRecipeCategory<GymRecipeCategory.Entry> {
    private static final int INTENSITY_X = 140, INTENSITY_WIDTH = 78, INTENSITY_HEIGHT = 12;
    // Shared by the cards in this category, so comparisons use the same intensity.
    private int intensity = 1;
    private final java.util.Map<Entry, GymPreviewAnimation> animations = new java.util.WeakHashMap<>();
    public record FoodTier(int energyPerTick, int foodDurationTicks,
        java.util.List<ItemStack> foods) {
    }
    public record Entry(Animal animal, java.util.List<FoodTier> tiers) {
    }
    public static final RecipeType<Entry> TYPE = RecipeType.create("industrial_overkill",
        "gym_animal", Entry.class);
    public GymRecipeCategory(IGuiHelper gui, java.util.List<Entry> entries) {
        super(TYPE, Component.translatable("jei.industrial_overkill.gym.title"),
            gui.createDrawableItemStack(new ItemStack(AnimalGym.ITEM.get())), 220,
            // JEI requires one height for the entire category, including datapack-added tiers.
            Math.max(86, entries.stream().mapToInt(entry -> 14 + entry.tiers().size() * 24).max()
                .orElse(86)));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        for (int tierIndex = 0; tierIndex < entry.tiers().size(); tierIndex++) {
            var tier = entry.tiers().get(tierIndex);
            var foods = tier.foods();
            // Alternatives, not simultaneous inputs. Very large tags cycle through every item.
            for (int i = 0; i < Math.min(7, foods.size()); i++) {
                var alternatives = new ArrayList<ItemStack>();
                for (int j = i; j < foods.size(); j += 7)
                    alternatives.add(foods.get(j));
                builder.addSlot(RecipeIngredientRole.INPUT, 82 + i * 18, 22 + tierIndex * 24)
                    .addItemStacks(alternatives)
                    .addRichTooltipCallback((slot, tooltip) -> {
                        tooltip.add(
                            Component.translatable("jei.industrial_overkill.gym.food_intensity",
                                intensity));
                        tooltip.add(Component.translatable("jei.industrial_overkill.gym.power",
                            number(power(tier))));
                        tooltip.add(Component.translatable("jei.industrial_overkill.gym.duration",
                            number(durationSeconds(tier))));
                        // Intensity spends the same ration faster; use exact base values, not
                        // the rounded duration shown above. Long multiplication supports datapacks.
                        tooltip
                            .add(Component.translatable("jei.industrial_overkill.gym.total_energy",
                                number((long) tier.energyPerTick() * tier.foodDurationTicks())));
                    });
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
                    intensity = intensity % 3 + 1;
                return true;
            }
        });
    }

    public int intensity() {
        return intensity;
    }
    public int power(FoodTier tier) {
        return tier.energyPerTick() * intensity;
    }
    public double durationSeconds(FoodTier tier) {
        return tier.foodDurationTicks() / (20.0 * intensity);
    }

    @Override
    public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics g, double mouseX,
        double mouseY) {
        var font = Minecraft.getInstance().font;
        var animal = entry.animal();
        animations.computeIfAbsent(entry, ignored -> new GymPreviewAnimation()).advance(animal,
            intensity);
        g.drawString(font,
            font.plainSubstrByWidth(animal.getType().getDescription().getString(), 100), 0,
            1, 0xff303030, false);
        boolean hover = mouseX >= INTENSITY_X && mouseX < INTENSITY_X + INTENSITY_WIDTH
            && mouseY >= 0 && mouseY < INTENSITY_HEIGHT;
        g.fill(INTENSITY_X, 0, INTENSITY_X + INTENSITY_WIDTH, INTENSITY_HEIGHT,
            hover ? 0xffbcbcbc : 0xffb0b0b0);
        g.renderOutline(INTENSITY_X, 0, INTENSITY_WIDTH, INTENSITY_HEIGHT, 0xff929292);
        var label = Component.translatable("jei.industrial_overkill.gym.selector", intensity);
        float labelScale = Math.min(1, (INTENSITY_WIDTH - 6f) / Math.max(1, font.width(label)));
        g.pose().pushPose();
        g.pose().translate(INTENSITY_X + (INTENSITY_WIDTH - font.width(label) * labelScale) / 2,
            (INTENSITY_HEIGHT - font.lineHeight * labelScale) / 2, 0);
        g.pose().scale(labelScale, labelScale, 1);
        g.drawString(font, label, 0, 0, 0xff303030, false);
        g.pose().popPose();
        g.fill(0, 14, 70, 84, 0xff30343c);
        int scale = (int) (54
            / Math.max(2, Math.max(animal.getBbHeight(), animal.getBbWidth()) * 1.5));
        // JEI translates the recipe pose, but Minecraft's scissor coordinates are absolute.
        var matrix = g.pose().last().pose();
        var min = matrix.transformPosition(new org.joml.Vector3f(0, 14, 0));
        var max = matrix.transformPosition(new org.joml.Vector3f(70, 84, 0));
        g.enableScissor((int) Math.floor(min.x), (int) Math.floor(min.y),
            (int) Math.ceil(max.x), (int) Math.ceil(max.y));
        try {
            animal.yBodyRot = animal.yHeadRot = animal.yHeadRotO = 145;
            animal.setYRot(145);
            var camera = new org.joml.Quaternionf().rotateX(-.15f);
            InventoryScreen.renderEntityInInventory(g, 35, 49, scale,
                new org.joml.Vector3f(0, animal.getBbHeight() / 2 + .05f, 0),
                new org.joml.Quaternionf().rotateZ((float) Math.PI).mul(camera), camera, animal);
        } finally {
            g.disableScissor();
        }
        for (int i = 0; i < entry.tiers().size(); i++) {
            var tier = entry.tiers().get(i);
            line(g, "rates", 82, 14 + i * 24, number(power(tier)), number(durationSeconds(tier)));
        }
    }

    private static void line(GuiGraphics g, String key, int x, int y, Object... args) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable("jei.industrial_overkill.gym." + key, args);
        float scale = Math.min(.8f, (220f - x) / Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, 0xff505050, false);
        g.pose().popPose();
    }
    private static String number(double value) {
        var format = NumberFormat.getNumberInstance(Locale.ROOT);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
    @Override
    public ResourceLocation getRegistryName(Entry entry) {
        var animal = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
            .getKey(entry.animal().getType());
        // Stable across food rule additions, removals, and renames.
        return AnimalGym.id("gym_animal/" + animal.getNamespace() + "/" + animal.getPath());
    }
}

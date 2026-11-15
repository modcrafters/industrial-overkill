package net.modcrafters.minecraft.industrialoverkill.client;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.lib.gui.client.recipe.RecipePresentation;

/** Shared animal cards, ingredient alternatives and rates for the drawer and JEI. */
public final class GymRecipeDisplay {
    public static final int INTENSITY_X = 140, INTENSITY_WIDTH = 78, INTENSITY_HEIGHT = 12;
    private int intensity = 1;
    private final java.util.Map<Entry, GymPreviewAnimation> animations = new java.util.WeakHashMap<>();
    public record FoodTier(int energyPerTick, int foodDurationTicks,
        java.util.List<ItemStack> foods) {
    }
    public record Entry(Animal animal, java.util.List<FoodTier> tiers) {
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

    public void draw(Entry entry, GuiGraphics g, double mouseX, double mouseY, boolean interactive) {
        var font = Minecraft.getInstance().font;
        var animal = entry.animal();
        animations.computeIfAbsent(entry, ignored -> new GymPreviewAnimation()).advance(animal,
            intensity);
        g.drawString(font,
            font.plainSubstrByWidth(animal.getType().getDescription().getString(), 100), 0,
            1, 0xff303030, false);
        boolean hover = mouseX >= INTENSITY_X && mouseX < INTENSITY_X + INTENSITY_WIDTH
            && mouseY >= 0 && mouseY < INTENSITY_HEIGHT;
        if (interactive) g.fill(INTENSITY_X, 0, INTENSITY_X + INTENSITY_WIDTH, INTENSITY_HEIGHT,
            hover ? 0xffbcbcbc : 0xffb0b0b0);
        if (interactive) g.renderOutline(INTENSITY_X, 0, INTENSITY_WIDTH, INTENSITY_HEIGHT, 0xff929292);
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

    public void intensity(int value) { intensity = Math.clamp(value, 1, 3); }

    public static int height(Entry entry) { return Math.max(86, 14 + entry.tiers().size() * 24); }

    public static List<ItemStack> alternatives(FoodTier tier, int column) {
        var result = new ArrayList<ItemStack>();
        for (int index = column; index < tier.foods().size(); index += 7)
            result.add(tier.foods().get(index));
        return List.copyOf(result);
    }

    public List<Component> foodTooltip(FoodTier tier) {
        return List.of(Component.translatable("jei.industrial_overkill.gym.food_intensity", intensity),
            Component.translatable("jei.industrial_overkill.gym.power", number(power(tier))),
            Component.translatable("jei.industrial_overkill.gym.duration", number(durationSeconds(tier))),
            Component.translatable("jei.industrial_overkill.gym.total_energy",
                number((long) tier.energyPerTick() * tier.foodDurationTicks())));
    }

    public RecipePresentation presentation(Entry entry, java.util.function.IntSupplier currentIntensity) {
        return new RecipePresentation() {
            public int width() { return 220; }
            public int height() { return GymRecipeDisplay.height(entry); }
            public List<Item> items() {
                intensity(currentIntensity.getAsInt());
                var items = new ArrayList<Item>();
                long frame = System.currentTimeMillis() / 1000;
                for (int row = 0; row < entry.tiers().size(); row++) {
                    var tier = entry.tiers().get(row);
                    for (int column = 0; column < Math.min(7, tier.foods().size()); column++) {
                        var alternatives = alternatives(tier, column);
                        items.add(new Item(82 + column * 18, 22 + row * 24,
                            alternatives.get((int) (frame % alternatives.size())), Role.INPUT, foodTooltip(tier)));
                    }
                }
                return List.copyOf(items);
            }
            public void drawBackground(GuiGraphics graphics) {
                intensity(currentIntensity.getAsInt());
                GymRecipeDisplay.this.draw(entry, graphics, -1, -1, false);
            }
        };
    }
}

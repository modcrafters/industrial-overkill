package net.modcrafters.minecraft.industrialoverkill.client;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewTree;
import net.neoforged.neoforge.fluids.FluidStack;
import zone.moddev.mc.lib.gui.client.recipe.RecipePresentation;
import zone.moddev.mc.lib.gui.layout.Rect;

/** Independent recipe rows shared by the machine panel and JEI. */
public final class RockStewTreeRenderer {
    private static final ResourceLocation TANK_SLOT = ResourceLocation.parse("mmdlib:slot");
    public static final int TANK_X = 2, TANK_Y = 3, TANK_SIZE = 18;
    public static final int ITEM_X = 48, WIDTH = ITEM_X + 5 * 24, ROW_HEIGHT = RockStewTree.ROW_HEIGHT;
    public record Cell(int row, int column, RockStewTree.Step step) {
        public int x() { return ITEM_X + 1 + column * 24; }
        public int y() { return 4 + row * ROW_HEIGHT; }
        public ItemStack item() { return step.item(); }
    }
    public record Layout(List<RockStewTree.Row> rows, List<Cell> cells) {}
    private RockStewTreeRenderer() {}

    /** One definition of ingredient positions, roles, tooltips and drawing for both viewers. */
    public static RecipePresentation presentation(RockStewTree.Row row) {
        var layout = layout(List.of(row));
        var items = new ArrayList<RecipePresentation.Item>();
        var fluids = new ArrayList<RecipePresentation.Fluid>();
        var tips = new ArrayList<RecipePresentation.Tooltip>();
        for (var cell : layout.cells()) {
            var lines = new ArrayList<Component>();
            lines.add(Component.translatable("stew.control.recipe"));
            lines.add(Component.translatable("stew.operation." + cell.step().operation()));
            if (cell.step().lavaConsumed() > 0) lines.add(Component.translatable("stew.lava_cost", cell.step().lavaConsumed()));
            row.steps().forEach(step -> lines.add(Component.translatable("stew.path_step",
                step.item().getHoverName(), Component.translatable("stew.operation." + step.operation()))));
            items.add(new RecipePresentation.Item(cell.x(), cell.y(), cell.item(),
                cell.column() == row.steps().size() - 1 ? RecipePresentation.Role.OUTPUT : RecipePresentation.Role.RENDER_ONLY, lines));
        }
        addFluid(fluids, Fluids.WATER, row.water());
        addFluid(fluids, Fluids.LAVA, row.lava());
        for (var step : row.steps().stream().skip(1).toList())
            if (step.lavaConsumed() > 0) fluids.add(new RecipePresentation.Fluid(
                new FluidStack(Fluids.LAVA, (int) step.lavaConsumed()), RecipePresentation.Role.INPUT));
        tips.add(new RecipePresentation.Tooltip(new Rect(TANK_X, TANK_Y, TANK_SIZE, TANK_SIZE), fluidTooltip(true, row.water())));
        tips.add(new RecipePresentation.Tooltip(new Rect(TANK_X + TANK_SIZE, TANK_Y, TANK_SIZE, TANK_SIZE), fluidTooltip(false, row.lava())));
        var immutableItems = List.copyOf(items);
        var immutableFluids = List.copyOf(fluids);
        var immutableTips = List.copyOf(tips);
        return new RecipePresentation() {
            public int width() { return ITEM_X + row.steps().size() * 24; }
            public int height() { return ROW_HEIGHT; }
            public List<Item> items() { return immutableItems; }
            public List<RecipePresentation.Fluid> fluids() { return immutableFluids; }
            public List<Tooltip> tooltips() { return immutableTips; }
            public void drawBackground(GuiGraphics graphics) { RockStewTreeRenderer.draw(graphics, layout, 0, 0, false); }
        };
    }

    private static void addFluid(List<RecipePresentation.Fluid> fluids, Fluid fluid, RockStewTree.FluidUse use) {
        if (use.retained() > 0) fluids.add(new RecipePresentation.Fluid(new FluidStack(fluid, (int) use.retained()), RecipePresentation.Role.CATALYST));
        if (use.consumed() > 0) fluids.add(new RecipePresentation.Fluid(new FluidStack(fluid, (int) use.consumed()), RecipePresentation.Role.INPUT));
    }

    public static Layout layout(List<RockStewTree.Row> rows) {
        var cells = new ArrayList<Cell>();
        for (int row = 0; row < rows.size(); row++) {
            var steps = rows.get(row).steps();
            for (int column = 0; column < Math.min(8, steps.size()); column++) {
                int index = steps.size() > 8 && column > 0 ? steps.size() - 8 + column : column;
                cells.add(new Cell(row, column, steps.get(index)));
            }
        }
        return new Layout(List.copyOf(rows), List.copyOf(cells));
    }

    public static int shade(String operation) {
        return switch (operation) {
            case "grind" -> 0xffaaa39a;
            case "smelt" -> 0xffb78b70;
            case "craft" -> 0xff779b8c;
            default -> 0xff768391;
        };
    }

    public static void draw(GuiGraphics graphics, Layout layout, int x, int y, boolean items) {
        for (int row = 0; row < layout.rows().size(); row++) {
            var recipe = layout.rows().get(row);
            tank(graphics, x + TANK_X, y + row * ROW_HEIGHT + TANK_Y, Fluids.WATER, recipe.water());
            tank(graphics, x + TANK_X + TANK_SIZE, y + row * ROW_HEIGHT + TANK_Y, Fluids.LAVA, recipe.lava());
            arrow(graphics, x + 40, y + row * ROW_HEIGHT + 12, false);
        }
        for (var cell : layout.cells()) {
            int cx = x + cell.x(), cy = y + cell.y();
            graphics.fill(cx - 1, cy - 1, cx + 17, cy + 17, shade(cell.step().operation()));
            graphics.renderOutline(cx - 1, cy - 1, 18, 18, 0xff58616a);
            if (cell.column() > 0) arrow(graphics, cx - 8, cy + 8,
                cell.column() == 1 && layout.rows().get(cell.row()).steps().size() > 8);
            if (items) {
                graphics.renderItem(cell.item(), cx, cy);
                graphics.renderItemDecorations(Minecraft.getInstance().font, cell.item(), cx, cy);
            }
        }
    }

    private static void arrow(GuiGraphics graphics, int x, int y, boolean omitted) {
        if (omitted) {
            for (int i = 0; i < 6; i += 2) graphics.fill(x + i, y, x + i + 1, y + 1, 0xff595959);
        } else {
            graphics.fill(x, y, x + 6, y + 1, 0xff595959);
            graphics.fill(x + 4, y - 1, x + 5, y + 2, 0xff595959);
        }
    }

    /** A full ghosted bucket with consumption overlaid from the bottom on the same 1000 ml scale. */
    private static void tank(GuiGraphics graphics, int x, int y, Fluid fluid, RockStewTree.FluidUse use) {
        graphics.blitSprite(TANK_SLOT, x, y, TANK_SIZE, TANK_SIZE);
        fluid(graphics, x + 1, y + 1, 16, 16, fluid, .5f);
        int consumed = use.consumed() == 0 ? 0 : (int) Math.clamp(Math.round(16.0 * use.consumed() / 1000), 1, 16);
        if (consumed > 0) {
            // GuiGraphics scissors use screen coordinates, unlike the translated recipe drawing.
            var pose = graphics.pose().last().pose();
            var topLeft = pose.transformPosition(new org.joml.Vector3f(x + 1, y + 17 - consumed, 0));
            var bottomRight = pose.transformPosition(new org.joml.Vector3f(x + 17, y + 17, 0));
            graphics.enableScissor((int) Math.floor(topLeft.x), (int) Math.floor(topLeft.y),
                (int) Math.ceil(bottomRight.x), (int) Math.ceil(bottomRight.y));
            try {
                // Clip the same full-size sprite so its pattern does not stretch with the amount.
                fluid(graphics, x + 1, y + 1, 16, 16, fluid, 1);
            } finally {
                graphics.disableScissor();
            }
        }
    }

    private static void fluid(GuiGraphics graphics, int x, int y, int width, int height, Fluid fluid, float opacity) {
        if (height <= 0) return;
        graphics.flush();
        boolean blending = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        float[] previous = RenderSystem.getShaderColor().clone();
        var extensions = IClientFluidTypeExtensions.of(fluid);
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(extensions.getStillTexture());
        int tint = extensions.getTintColor();
        try {
            graphics.setColor(((tint >>> 16) & 255) / 255f, ((tint >>> 8) & 255) / 255f,
                (tint & 255) / 255f, opacity);
            RenderSystem.enableBlend();
            graphics.blit(x, y, 0, width, height, sprite);
            graphics.flush();
        } finally {
            graphics.setColor(previous[0], previous[1], previous[2], previous[3]);
            if (!blending) RenderSystem.disableBlend();
        }
    }

    public static List<Component> fluidTooltip(boolean water, RockStewTree.FluidUse use) {
        return List.of(Component.translatable(water ? "block.minecraft.water" : "block.minecraft.lava"),
            Component.translatable("stew.fluid.consumed", use.consumed()));
    }

    public static List<Component> tooltip(Layout layout, int x, int y) {
        int row = y / ROW_HEIGHT;
        if (y < 0 || row >= layout.rows().size()) return List.of();
        var recipe = layout.rows().get(row);
        int tankY = y % ROW_HEIGHT;
        if (tankY >= TANK_Y && tankY < TANK_Y + TANK_SIZE) {
            if (x >= TANK_X && x < TANK_X + TANK_SIZE) return fluidTooltip(true, recipe.water());
            if (x >= TANK_X + TANK_SIZE && x < TANK_X + 2 * TANK_SIZE) return fluidTooltip(false, recipe.lava());
        }
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("stew.control.recipe"));
        for (var cell : layout.cells()) if (cell.row() == row && x >= cell.x() - 1 && x < cell.x() + 17) {
            lines.add(cell.item().getHoverName());
            lines.add(Component.translatable("stew.operation." + cell.step().operation()));
            if (cell.step().lavaConsumed() > 0) lines.add(Component.translatable("stew.lava_cost", cell.step().lavaConsumed()));
        }
        for (var step : recipe.steps()) lines.add(Component.translatable("stew.path_step",
            step.item().getHoverName(), Component.translatable("stew.operation." + step.operation())));
        return lines;
    }
}

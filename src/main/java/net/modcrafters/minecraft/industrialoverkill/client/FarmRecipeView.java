package net.modcrafters.minecraft.industrialoverkill.client;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import zone.moddev.mc.lib.gui.client.recipe.RecipePresentation;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;

/** The same ingredient positions and quantities for the drawer and JEI. */
public final class FarmRecipeView implements RecipePresentation {
    private static final int PITCH = 20;
    private static final int INPUT_COLUMNS = 3;
    private static final int OUTPUT_COLUMNS = 4;
    private static final int OUTPUT_X = 76;
    private static final int WIDTH = 155;
    private static final ResourceLocation SLOT = ResourceLocation.parse("mmdlib:slot");
    private static final ResourceLocation ARROW = ResourceLocation.parse("mmdlib:progress_arrow");

    public record ItemCell(int x, int y, Role role, List<ItemStack> stacks, List<Component> notes) {}
    public record FluidCell(int x, int y, Role role, List<FluidStack> stacks, List<Component> notes) {}

    private final List<ItemCell> itemCells;
    private final List<FluidCell> fluidCells;
    private final int rows;
    private final Component duration;
    private final Component energy;
    private final List<FormattedCharSequence> footer;

    public FarmRecipeView(MachineRecipe recipe) {
        this(recipe, Component.empty());
    }

    public FarmRecipeView(MachineRecipe recipe, Component detail) {
        footer = detail.getString().isEmpty() ? List.of() : Minecraft.getInstance().font.split(detail, WIDTH - 4);
        var items = new ArrayList<ItemCell>();
        var fluids = new ArrayList<FluidCell>();
        int inputs = 0;
        long totalEnergy = 0;
        for (var group : recipe.inputs().values()) for (var part : group) {
            if (part instanceof MachineIngredient.EnergyPart power) {
                totalEnergy += power.amount();
                continue;
            }
            int x = 2 + inputs % INPUT_COLUMNS * PITCH;
            int y = 2 + inputs++ / INPUT_COLUMNS * PITCH;
            Role role = part.consumed() ? Role.INPUT : Role.CATALYST;
            var notes = part.consumed() ? List.<Component>of()
                : List.<Component>of(Component.translatable("jei.mmdlib.machine.not_consumed"));
            switch (part) {
                case MachineIngredient.ItemPart item -> items.add(new ItemCell(x, y, role,
                    Arrays.stream(item.items().getItems()).map(stack -> stack.copyWithCount(item.count())).toList(), notes));
                case MachineIngredient.FluidPart fluid -> fluids.add(new FluidCell(x, y, role,
                    fluid.fluids().stream().map(holder -> new FluidStack(holder, fluid.millibuckets())).toList(), notes));
                case MachineIngredient.EnergyPart ignored -> throw new IllegalStateException("Unexpected energy cell");
            }
        }
        int outputs = 0;
        for (var group : recipe.outputs().values()) for (var product : group) {
            int x = OUTPUT_X + outputs % OUTPUT_COLUMNS * PITCH;
            int y = 2 + outputs++ / OUTPUT_COLUMNS * PITCH;
            var notes = product.chance() < 1
                ? List.<Component>of(Component.translatable("jei.mmdlib.machine.chance", number(product.chance() * 100.0)))
                : List.<Component>of();
            switch (product) {
                case MachineProduct.ItemProduct item -> items.add(new ItemCell(x, y, Role.OUTPUT, List.of(item.stack()), notes));
                case MachineProduct.FluidProduct productFluid -> {
                    var amount = productFluid.fluid();
                    fluids.add(new FluidCell(x, y, Role.OUTPUT,
                        List.of(new FluidStack(amount.fluid(), amount.amount(), amount.components())), notes));
                }
            }
        }
        rows = Math.max(1, Math.max(Math.ceilDiv(inputs, INPUT_COLUMNS), Math.ceilDiv(outputs, OUTPUT_COLUMNS)));
        itemCells = List.copyOf(items);
        fluidCells = List.copyOf(fluids);
        duration = Component.translatable("jei.mmdlib.machine.time", number(recipe.time() / 20.0));
        energy = totalEnergy == 0 ? Component.empty() : Component.translatable("jei.mmdlib.machine.energy", number(totalEnergy));
    }

    public List<ItemCell> itemCells() { return itemCells; }
    public List<FluidCell> fluidCells() { return fluidCells; }
    @Override public List<Fluid> fluids() {
        return fluidCells.stream().flatMap(cell -> cell.stacks().stream().map(stack -> new Fluid(stack, cell.role()))).toList();
    }
    public boolean available() {
        return itemCells.stream().noneMatch(cell -> cell.stacks().isEmpty())
            && fluidCells.stream().noneMatch(cell -> cell.stacks().isEmpty());
    }
    @Override public int width() { return WIDTH; }
    @Override public int height() { return rows * PITCH + 13 + footer.size() * 10; }

    @Override
    public List<Item> items() {
        return itemCells.stream().filter(cell -> !cell.stacks().isEmpty())
            .map(cell -> new Item(cell.x(), cell.y(), current(cell.stacks()), cell.role(), cell.notes())).toList();
    }

    @Override
    public List<Tooltip> tooltips() {
        return fluidCells.stream().filter(cell -> !cell.stacks().isEmpty()).map(cell -> {
            var fluid = current(cell.stacks());
            var lines = new ArrayList<Component>();
            lines.add(Component.translatable("screen.mmdlib.tank.contents", fluid.getAmount(), fluid.getHoverName()));
            lines.addAll(cell.notes());
            return new Tooltip(new Rect(cell.x(), cell.y(), 16, 16), lines);
        }).toList();
    }

    @Override
    public void drawBackground(GuiGraphics graphics) {
        drawFrame(graphics);
        var font = Minecraft.getInstance().font;
        for (var cell : fluidCells) {
            if (cell.stacks().isEmpty()) continue;
            var fluid = current(cell.stacks());
            RecipeFluidRenderer.draw(graphics, cell.x(), cell.y(), 16, 16, fluid.getFluid(), 1);
            String amount = Integer.toString(fluid.getAmount());
            graphics.drawString(font, amount, cell.x() + 16 - font.width(amount), cell.y() + 8, 0xffffffff, true);
        }
    }

    public void drawFrame(GuiGraphics graphics) {
        itemCells.forEach(cell -> graphics.blitSprite(SLOT, cell.x() - 1, cell.y() - 1, 18, 18));
        fluidCells.forEach(cell -> graphics.blitSprite(SLOT, cell.x() - 1, cell.y() - 1, 18, 18));
        graphics.blitSprite(ARROW, 62, 4, 12, 12);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, duration, 2, rows * PITCH + 2, 0xff333333, false);
        graphics.drawString(font, energy, OUTPUT_X, rows * PITCH + 2, 0xff333333, false);
        for (int i = 0; i < footer.size(); i++)
            graphics.drawString(font, footer.get(i), 2, rows * PITCH + 12 + i * 10, 0xff333333, false);
    }

    private static <T> T current(List<T> stacks) {
        var level = Minecraft.getInstance().level;
        return stacks.get(level == null ? 0 : Math.floorMod(level.getGameTime() / 20, stacks.size()));
    }

    private static String number(double amount) {
        var format = NumberFormat.getNumberInstance(Locale.ROOT);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }
}

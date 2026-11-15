package net.modcrafters.minecraft.industrialoverkill.test;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.client.jei.MachineRecipeCategory;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewRecipes;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;

/** Isolated JEI layout check, including a partial-fluid example for visual verification. */
@JeiPlugin
public final class RockStewJeiClientSmoke implements IModPlugin {
    private static IJeiRuntime runtime;
    private static boolean treeShown;
    private boolean started, shown, finished;
    private int ticks;
    private final long deadline = System.currentTimeMillis() + 180000;

    public RockStewJeiClientSmoke() {
        if (Boolean.getBoolean("industrialoverkill.rockStewJeiSmoke"))
            NeoForge.EVENT_BUS.addListener(this::tick);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.parse("industrial_overkill:rock_stew_jei_test");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RockStewJeiClientSmoke.runtime = runtime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished) return;
        var mc = Minecraft.getInstance();
        try {
            check(System.currentTimeMillis() < deadline, "JEI check timed out");
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                mc.createWorldOpenFlows().createFreshLevel("stew-jei-" + System.currentTimeMillis(),
                    new LevelSettings("Stone Stew JEI", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(1, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            if (runtime == null || mc.level == null || mc.player == null || ++ticks < 40) return;
            ticks = 0;
            if (shown) {
                Screenshot.grab(mc.gameDirectory, "rock-stew-jei.png", mc.getMainRenderTarget(), ignored -> {});
                var grinding = MachineRecipeCategory.recipeType(RockStewRecipes.GRINDING);
                check(runtime.getRecipeManager().createRecipeLookup(grinding).get().count() == 2,
                    "Both grinding recipes registered");
                finished = true;
                MMDLib.LOGGER.info("ROCK_STEW_JEI_PASS: source and grinding categories, fluid roles, amounts and layouts");
                mc.stop();
                return;
            }
            var type = RockStewRecipes.SOURCES;
            var jeiType = MachineRecipeCategory.recipeType(type);
            var manager = runtime.getRecipeManager();
            var category = manager.getRecipeCategory(jeiType);
            var recipes = new ArrayList<>(manager.createRecipeLookup(jeiType).get().toList());
            check(recipes.size() == RockStewTests.RECIPES.size(), "All Stone Stew recipes registered in JEI");
            for (var recipe : recipes) {
                var layout = manager.createRecipeLayoutDrawable(category, recipe,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
                var slots = layout.getRecipeSlotsView();
                check(slots.getSlotViews(RecipeIngredientRole.RENDER_ONLY).isEmpty(), "No ghost sample");
                int reusable = 2;
                check(slots.getSlotViews(RecipeIngredientRole.CATALYST).size() == reusable, "Reusable fluid catalysts");
                for (var catalyst : slots.getSlotViews(RecipeIngredientRole.CATALYST)) {
                    @SuppressWarnings("deprecation")
                    var tooltip = ((mezz.jei.api.gui.ingredient.IRecipeSlotDrawable) catalyst).getTooltip();
                    check(tooltip.stream().anyMatch(line -> line.getString().equals("Requires 1,000 mB")),
                        "Reusable fluid tooltip retains the full required amount");
                    check(tooltip.stream().anyMatch(line -> line.getString().equals("Not consumed")),
                        "Amount and non-consumption are both shown");
                }
                check(slots.getSlotViews(RecipeIngredientRole.INPUT).size() == recipe.value().recipe().inputs().values().stream().flatMap(List::stream)
                    .filter(part -> part instanceof zone.moddev.mc.lib.machine.ingredient.MachineIngredient.FluidPart fluid && fluid.consumed()).count(), "Only consumed fluids are inputs");
                check(slots.getSlotViews(RecipeIngredientRole.OUTPUT).size() == 1, "One product");
            }
            var example = MachineRecipe.CODEC.codec().parse(mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString("""
                    {"inputs":{"input":[{"items":{"item":"minecraft:stone"}}],
                    "water":[{"fluids":"minecraft:water","amount":250,"consumed":false}],
                    "lava":[{"fluids":"minecraft:lava","amount":100}]},
                    "outputs":{"output":[{"fluid":{"id":"minecraft:water","amount":1250}}]},"time":40}
                    """)).getOrThrow();
            var partial = new RecipeHolder<>(ResourceLocation.parse("industrial_overkill:jei_partial_fluids"),
                new MachineRecipeEntry(type, example));
            var partialLayout = manager.createRecipeLayoutDrawable(category, partial,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            @SuppressWarnings("deprecation")
            var partialTooltip = ((mezz.jei.api.gui.ingredient.IRecipeSlotDrawable) partialLayout.getRecipeSlotsView()
                .getSlotViews(RecipeIngredientRole.CATALYST).getFirst()).getTooltip();
            check(partialTooltip.stream().anyMatch(line -> line.getString().equals("Requires 250 mB")),
                "Tooltip shows the required amount rather than the rounded tank scale");
            recipes.add(partial);
            runtime.getRecipesGui().showRecipes(category, recipes, List.of());
            shown = true;
        } catch (Throwable failure) {
            finished = true;
            MMDLib.LOGGER.error("ROCK_STEW_JEI_FAIL", failure);
            mc.stop();
        }
    }

    /** Exercises the registered handler behind JEI's native + button against the open machine menu. */
    @SuppressWarnings("removal")
    public static boolean selectTreeFromMachine() {
        if (runtime == null) return false;
        var type = net.modcrafters.minecraft.industrialoverkill.client.jei.RockStewTreeCategory.TYPE;
        var manager = runtime.getRecipeManager();
        var category = manager.getRecipeCategory(type);
        var entry = manager.createRecipeLookup(type).get()
            .filter(page -> page.source().equals(RockStewRecipes.id("rock_stew/stone")) && page.node() == 0).findFirst().orElseThrow();
        if (!treeShown) {
            var obsidian = manager.createRecipeLookup(type).get()
                .filter(page -> page.source().equals(RockStewRecipes.id("rock_stew/obsidian")) && page.node() == 0)
                .findFirst().orElseThrow();
            runtime.getRecipesGui().showRecipes(category, List.of(entry, obsidian), List.of());
            treeShown = true;
            return false;
        }
        var parent = runtime.getRecipesGui().getParentScreen().orElse(null);
        check(parent instanceof zone.moddev.mc.lib.gui.client.MachineScreen, "JEI retains the machine as its parent");
        var layout = manager.createRecipeLayoutDrawable(category, entry,
            runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        check(category.getHeight() == net.modcrafters.minecraft.industrialoverkill.client.RockStewTreeRenderer.ROW_HEIGHT,
            "Each JEI recipe occupies one row");
        check(layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).size() == 1,
            "Only the destination is the recipe output");
        var shared = ((net.modcrafters.minecraft.industrialoverkill.client.jei.RockStewTreeCategory) category).presentation(entry);
        check(shared.items().getFirst().x() == 49 && shared.items().getFirst().stack().is(net.minecraft.world.item.Items.STONE),
            "JEI delegates to the same native-scale item positions and stacks as the drawer");
        check(entry.row().lava().retained() == 1000 && entry.row().lava().consumed() == 100,
            "JEI uses the same retained and consumed source-fluid amounts");
        check(shared.tooltips().get(0).lines().stream().map(net.minecraft.network.chat.Component::getString).toList()
            .equals(List.of("Water", "0 ml consumed")), "Water recipe tooltip shows only consumption");
        check(shared.tooltips().get(1).lines().stream().map(net.minecraft.network.chat.Component::getString).toList()
            .equals(List.of("Lava", "100 ml consumed")), "Lava recipe tooltip omits retained and total requirements");
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "rock-stew-jei-fluids.png", mc.getMainRenderTarget(), ignored -> {});
        layout.setPosition(0, 0);
        layout.tick(); // JEI creates recipe extras lazily on the first draw/tick.
        var menu = ((zone.moddev.mc.lib.gui.client.MachineScreen) parent).getMenu();
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
        check(handler.getRecipeType().equals(type), "Production paths register a native JEI Apply handler");
        var player = Minecraft.getInstance().player;
        var target = menu.bound.screen().find("stage_processor_0").orElseThrow();
        check(!menu.elementPending(target), "No request pending before Apply availability check");
        check(handler.transferRecipe(menu, entry, layout.getRecipeSlotsView(), player, false, false) == null,
            "JEI enables Apply without requiring recipe ingredients in player inventory");
        check(!menu.elementPending(target), "Checking Apply availability does not submit a selection");
        check(handler.transferRecipe(menu, entry, layout.getRecipeSlotsView(), player, false, true) == null,
            "JEI Apply submits the selected path");
        return true;
    }

    public static void returnToMachine() {
        Minecraft.getInstance().setScreen(runtime.getRecipesGui().getParentScreen().orElseThrow());
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

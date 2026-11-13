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
import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;

/** Isolated JEI layout check, including a partial-fluid example for visual verification. */
@JeiPlugin
public final class RockStewJeiClientSmoke implements IModPlugin {
    private IJeiRuntime runtime;
    private boolean started, shown, finished;
    private int ticks;
    private int transferStage;
    private zone.moddev.mc.lib.gui.client.MachineScreen transferScreen;
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
        this.runtime = runtime;
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
                checkGhostTransfer();
                return;
            }
            var type = MachineContent.recipeType(ResourceLocation.parse("industrial_overkill:rock_stew"));
            var jeiType = MachineRecipeCategory.recipeType(type);
            var manager = runtime.getRecipeManager();
            var category = manager.getRecipeCategory(jeiType);
            var recipes = new ArrayList<>(manager.createRecipeLookup(jeiType).get().toList());
            check(recipes.size() == RockStewTests.RECIPES.size(), "All Stone Stew recipes registered in JEI");
            for (var recipe : recipes) {
                var layout = manager.createRecipeLayoutDrawable(category, recipe,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
                var slots = layout.getRecipeSlotsView();
                check(slots.getSlotViews(RecipeIngredientRole.RENDER_ONLY).size() == 1, "One ghost sample");
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
                check(slots.getSlotViews(RecipeIngredientRole.INPUT).size() == (recipe.value().recipe().inputs().containsKey("reagent") ? 1 : 0), "Only consumed fluids are inputs");
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

    @SuppressWarnings("removal")
    private void checkGhostTransfer() {
        var mc = Minecraft.getInstance();
        var pos = new net.minecraft.core.BlockPos(0, 100, 0);
        if (transferStage == 0) {
            Screenshot.grab(mc.gameDirectory, "rock-stew-jei-ghosts.png", mc.getMainRenderTarget(), ignored -> {});
            mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var level = player.serverLevel();
                for (int x = -3; x <= 3; x++)
                    for (int z = -3; z <= 3; z++)
                        level.setBlock(new net.minecraft.core.BlockPos(x, 99, z),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos, net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(RockStewTests.ID).defaultBlockState(), 3);
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.connection.teleport(2.5, 100, 2.5, 0, 0);
            }).join();
            transferStage = 1;
            return;
        }
        if (transferStage == 1) {
            if (mc.level.getBlockEntity(pos) == null) return;
            mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                check(zone.moddev.mc.lib.gui.runtime.ScreenRuntime.open(player, player.serverLevel().getBlockEntity(pos)),
                    "Open machine after its block entity reaches the client");
            }).join();
            transferStage = 2;
            return;
        }
        if (transferStage == 2) {
            if (!(mc.screen instanceof zone.moddev.mc.lib.gui.client.MachineScreen screen)) return;
            transferScreen = screen;
        }
        var menu = transferScreen.getMenu();
        var ghost = menu.elements.stream()
            .filter(zone.moddev.mc.lib.gui.model.elements.SlotElement.class::isInstance)
            .map(zone.moddev.mc.lib.gui.model.elements.SlotElement.class::cast)
            .filter(zone.moddev.mc.lib.gui.model.elements.SlotElement::ghost).findFirst().orElseThrow();
        var type = MachineRecipeCategory.recipeType(MachineContent.recipeType(ResourceLocation.parse("industrial_overkill:rock_stew")));
        var manager = runtime.getRecipeManager();
        var category = manager.getRecipeCategory(type);
        var recipe = manager.createRecipeLookup(type).get()
            .filter(holder -> holder.id().getPath().equals("rock_stew/basalt")).findFirst().orElseThrow();
        var layout = manager.createRecipeLayoutDrawable(category, recipe,
            runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
        if (transferStage == 2) {
            check(mc.player.getInventory().isEmpty(), "Selection needs no sample item in inventory");
            check(handler.transferRecipe(menu, recipe, layout.getRecipeSlotsView(), mc.player, false, false) == null,
                "JEI plus button enabled with empty inventory, tanks and energy");
            check(menu.slot(ghost).getItem().is(net.minecraft.world.item.Items.COBBLESTONE),
                "Transfer validation does not alter the ghost");
            runtime.getRecipesGui().showRecipes(category, List.of(recipe), List.of());
            transferStage = 3;
            return;
        }
        if (transferStage == 3) {
            Screenshot.grab(mc.gameDirectory, "rock-stew-jei-select-button.png", mc.getMainRenderTarget(), ignored -> {});
            check(handler.transferRecipe(menu, recipe, layout.getRecipeSlotsView(), mc.player, true, true) == null,
                "JEI transfer selects the ghost while recipes cover the machine screen");
            transferStage = 4;
            return;
        }
        if (!menu.slot(ghost).getItem().is(net.minecraft.world.item.Items.BASALT)) return;
        mc.getSingleplayerServer().submit(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            var machine = (zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity) player.serverLevel().getBlockEntity(pos);
            check(machine.screenGhost("input", 0).is(net.minecraft.world.item.Items.BASALT)
                && machine.screenGhost("input", 0).getCount() == 1, "Server accepted exactly one ghost sample");
            check(player.getInventory().isEmpty() && machine.energy.stored() == 0, "Selection creates no items or energy");
            for (int tank = 0; tank < 3; tank++)
                check(machine.fluids.getFluidInTank(tank).isEmpty(), "Selection does not fill tanks");
            for (int slot = 0; slot < machine.items.getSlots(); slot++)
                check(machine.items.getStackInSlot(slot).isEmpty(), "Selection does not create output");
        }).join();
        finished = true;
        MMDLib.LOGGER.info("ROCK_STEW_JEI_PASS: ghost roles, amounts, layouts and JEI recipe selection synchronized to server");
        mc.stop();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

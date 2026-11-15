package net.modcrafters.minecraft.industrialoverkill.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT)
public final class RockStewClientSmoke {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static final long DEADLINE = System.currentTimeMillis() + 180000;
    private static boolean started, placed;
    private static volatile boolean finished;
    private static int stage, frames;

    private static boolean active() {
        return Boolean.getBoolean("industrialoverkill.rockStewSmoke") && !finished;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void server(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable error) { fail(error); }
        });
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!active()) return;
        var mc = Minecraft.getInstance();
        try {
            check(System.currentTimeMillis() < DEADLINE, "Timed out at stage " + stage);
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                mc.createWorldOpenFlows().createFreshLevel("rock-stew-" + System.currentTimeMillis(),
                    new LevelSettings("Rock Stew Factory", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            if (mc.level == null || mc.player == null || placed) return;
            placed = true;
            server(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var level = player.serverLevel();
                level.getChunkAt(POS);
                level.setDayTime(1000);
                for (int x = -5; x <= 5; x++)
                    for (int z = -5; z <= 5; z++)
                        level.setBlock(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(POS, BuiltInRegistries.BLOCK.get(RockStewTests.ID).defaultBlockState(), 3);
                var machine = (DefinedMachineEntity) level.getBlockEntity(POS);
                player.setGameMode(GameType.SURVIVAL);
                RockStewTests.fillStaticTanks(machine, player);
                machine.energy.setStored(32000);
                net.modcrafters.minecraft.industrialoverkill.stew.RockStewProcessor.from(machine).select(
                    net.modcrafters.minecraft.industrialoverkill.stew.RockStewRecipes.DEFAULT,
                    new net.minecraft.world.item.ItemStack(Items.GLASS_PANE));
                player.connection.teleport(2.8, 100.8, -3.0, 33, 27);
            });
        } catch (Throwable error) { fail(error); }
    }

    @SubscribeEvent
    public static void hoverDrawer(net.neoforged.neoforge.client.event.ScreenEvent.Render.Pre event) {
        if (!active() || stage != 30 || !(event.getScreen() instanceof MachineScreen screen)) return;
        var drawer = (zone.moddev.mc.lib.gui.client.recipe.RecipeDrawer) screen.drawers().entries().stream()
            .filter(d -> d.id().equals(net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen.RECIPE_DRAWER_ID))
            .findFirst().orElseThrow();
        var content = zone.moddev.mc.lib.gui.layout.RecipeDrawerLayout.content(drawer.bodyBounds());
        // Render through the real screen pass, with a deterministic hover over the first recipe.
        event.setCanceled(true);
        screen.render(event.getGuiGraphics(),
            content.x() + net.modcrafters.minecraft.industrialoverkill.client.RockStewTreeRenderer.ITEM_X + 8,
            content.y() + 8, event.getPartialTick());
    }

    @SubscribeEvent
    public static void render(RenderFrameEvent.Post event) {
        if (!active()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(POS) instanceof DefinedMachineEntity)) return;
        try {
            if (++frames < 60) return;
            if (stage == 0) {
                mc.options.hideGui = true;
                stage = 1;
                frames = 0;
            } else if (stage == 1) {
                var model = mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(POS));
                check(model != mc.getModelManager().getMissingModel(), "Block model baked");
                check(!model.getParticleIcon().contents().name().getPath().equals("missingno"), "Texture resolved");
                var machine = (DefinedMachineEntity) mc.level.getBlockEntity(POS);
                var variables = machine.getModelData().get(
                    zone.moddev.mc.lib.machine.neoforge.MachineVisualState.VARIABLES);
                // Wait for the tracking snapshot; no menu has been opened yet.
                if (variables == null || !Boolean.TRUE.equals(variables.get("processors.rock_stew.running"))) return;
                var blockState = machine.getBlockState();
                for (var facing : Direction.Plane.HORIZONTAL) {
                    var rotatedState = blockState.setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
                    var rotatedModel = mc.getBlockRenderer().getBlockModel(rotatedState);
                    for (int lamps = 0; lamps <= 7; lamps++) for (boolean paused : new boolean[]{false, true}) {
                        var snapshot = net.neoforged.neoforge.client.model.data.ModelData.builder()
                            .with(zone.moddev.mc.lib.machine.neoforge.MachineVisualState.VARIABLES,
                                java.util.Map.<String, Object>of("processors.rock_stew.running", true,
                                    "processors.rock_stew.paused", paused, "processors.rock_stew.progress_lights_1", lamps,
                                    "processors.rock_stew.progress_lights_2", (lamps + 1) % 8, "processors.rock_stew.progress_lights_3", (lamps + 2) % 8,
                                    "processors.rock_stew.progress_lights_4", (lamps + 3) % 8, "processors.rock_stew.progress_lights_5", (lamps + 4) % 8)).build();
                        var data = rotatedModel.getModelData(mc.level, POS, rotatedState, snapshot);
                        for (var side : Direction.values()) {
                            var base = rotatedModel.getQuads(rotatedState, side, net.minecraft.util.RandomSource.create(0), data, null);
                            String expected = side == facing ? "rock_stew_front" : side == Direction.UP ? "rock_stew_top"
                                : side == Direction.DOWN ? "rock_stew_bottom" : "rock_stew_side";
                            check(base.size() == 1 && base.getFirst().getSprite().contents().name().getPath().equals("block/" + expected),
                                "Overlay retains the original base face: " + facing + "/" + side);
                        }
                        var overlays = rotatedModel.getQuads(rotatedState, null, net.minecraft.util.RandomSource.create(0), data, null);
                        check(overlays.size() == (paused ? 5 : 7), "Independent progress and activity overlays coexist");
                        var stageLights = overlays.stream().filter(quad -> quad.getSprite().contents().name().getPath().equals("block/rock_stew_proc_lights")).toList();
                        check(stageLights.size() == 5, "All five stage overlays are present");
                        var actualFrames = new java.util.ArrayList<Integer>();
                        for (var light : stageLights) {
                            check(light.getDirection() == facing, "Lamps follow the machine facing");
                            float minV = Float.POSITIVE_INFINITY;
                            for (int vertex = 0; vertex < 4; vertex++) {
                                int offset = vertex * net.neoforged.neoforge.client.model.IQuadTransformer.STRIDE;
                                minV = Math.min(minV, light.getSprite().getVOffset(Float.intBitsToFloat(light.getVertices()[offset
                                    + net.neoforged.neoforge.client.model.IQuadTransformer.UV0 + 1])));
                                check(light.getVertices()[offset + net.neoforged.neoforge.client.model.IQuadTransformer.UV2]
                                    == net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, "Lamp overlay is emissive");
                            }
                            actualFrames.add(Math.round(minV * 8));
                        }
                        final int firstFrame = lamps;
                        var expectedFrames = java.util.stream.IntStream.range(0, 5).map(i -> (firstFrame + i) % 8).sorted().boxed().toList();
                        check(actualFrames.stream().sorted().toList().equals(expectedFrames), "Each stage selects its own frame, including while paused");
                    }
                }
                shot("rock-stew-factory-in-game.png");
                mc.options.hideGui = false;
                stage = 2;
                frames = 0;
                server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var serverMachine = (DefinedMachineEntity) player.serverLevel().getBlockEntity(POS);
                    var processor = net.modcrafters.minecraft.industrialoverkill.stew.RockStewProcessor.from(serverMachine);
                    serverMachine.items.restoreStack(3, new net.minecraft.world.item.ItemStack(Items.GLASS, 8));
                    serverMachine.serverTick();
                    check(processor.chain().cycle(4).plan().isPresent(), "Prepare active work for the reported reload failure");
                    serverMachine.items.restoreStack(3, new net.minecraft.world.item.ItemStack(Items.GLASS, 2));
                    var saved = serverMachine.saveWithoutMetadata(player.registryAccess());
                    serverMachine.loadWithComponents(saved, player.registryAccess());
                    check(!processor.problem().isEmpty(), "Reproduce the recovery lock before clicking the real screen");
                    check(ScreenRuntime.open(player, serverMachine), "Open factory screen");
                });
            } else if (stage == 2 && mc.screen instanceof MachineScreen screen) {
                var menu = screen.getMenu();
                check(menu.bound.screen().id().equals(RockStewTests.ID), "Correct factory screen");
                for (int tank = 0; tank < 2; tank++) {
                    var gauge = menu.bound.screen().find("tank_" + tank).orElseThrow();
                    check(menu.value(gauge, false) instanceof net.neoforged.neoforge.fluids.FluidStack fluid
                        && fluid.getAmount() == 1000 && fluid.getFluid() == (tank == 0 ? Fluids.WATER : Fluids.LAVA),
                        "Catalyst gauge synchronized");
                }
                check(menu.bound.screen().find("tank_2").isEmpty(), "Exactly two tanks");
                var water = screen.bounds(menu.bound.screen().find("tank_0").orElseThrow());
                var lava = screen.bounds(menu.bound.screen().find("tank_1").orElseThrow());
                for (int tank = 0; tank < 2; tank++) {
                    var gauge = menu.bound.screen().find("tank_" + tank).orElseThrow();
                    var bounds = screen.bounds(gauge);
                    check(screen.tooltipLines(gauge, bounds.x() + 5, bounds.y() + 5).stream()
                        .anyMatch(lineText -> lineText.getString().equals("1000 ml gets reserved for rocks")),
                        "Main tank tooltip explains the reusable reserve");
                }
                check(water.height() > water.width() && lava.height() > lava.width()
                    && water.y() == lava.y() && water.right() <= lava.x(), "Vertical tanks sit side by side");
                var energy = menu.bound.screen().find("energy").orElseThrow();
                var line = menu.bound.screen().find("processing_line").orElseThrow();
                check(line.actions().isEmpty(), "Processing line has no scrolling action");
                check(menu.elementStates().get(line, net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen.Line.COUNT) == 5,
                    "All five products of the pane route are visible together");
                int previousX = -1, outputY = -1;
                var operationItems = new net.minecraft.world.item.Item[]{Items.COBBLESTONE, Items.GRINDSTONE,
                    Items.GRINDSTONE, Items.FURNACE, Items.CRAFTING_TABLE};
                for (int i = 0; i < 5; i++) {
                    var output = screen.bounds(menu.bound.screen().find("stage_item_" + i).orElseThrow());
                    var processor = menu.bound.screen().find("stage_processor_" + i).orElseThrow();
                    check(menu.value(processor, false) instanceof net.minecraft.world.item.ItemStack icon && icon.is(operationItems[i]),
                        "Processor uses the correct Minecraft item icon");
                    var glyph = screen.bounds(processor);
                    var stock = screen.bounds(menu.bound.screen().find("stage_stock_" + i).orElseThrow());
                    check(output.x() > previousX && (outputY == -1 || output.y() == outputY), "Five aligned output columns");
                    check(lava.right() < output.x(), "Both tanks sit left of processing");
                    if (previousX >= 0) check(output.x() - previousX <= 24, "Processor columns stay compact");
                    check(glyph.y() > output.y() && stock.y() > glyph.y(), "Output, processor and stock order");
                    check(processor.actions().contains(
                        net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen.SELECT), "Every processor opens recipes and accepts selections");
                    previousX = output.x(); outputY = output.y();
                }
                check(screen.bounds(energy).x() > previousX, "Energy on the right");
                check(water.height() == 54 && lava.height() == 54 && screen.bounds(energy).height() == 54,
                    "Fluid and energy gauges use the standard three-slot height");
                check(menu.bound.screen().find("mix").isEmpty(), "Combining sprite removed");
                check(java.math.BigInteger.valueOf(100000).equals(menu.value(energy, true)), "Energy capacity synchronized");
                check(!net.modcrafters.minecraft.industrialoverkill.stew.RockStewRecipes.SOURCES
                    .recipes(mc.level.getRecipeManager()).isEmpty(), "Source recipes synchronized");
                shot("rock-stew-factory-screen.png");
                var stock = screen.bounds(menu.bound.screen().find("stage_stock_0").orElseThrow());
                check(screen.mouseClicked(stock.x() + 8, stock.y() + 8, 0), "Cycle stage stock setting");
                check(menu.bound.screen().find("recipe_book").isEmpty()
                    && menu.bound.screen().find("previous_stage").isEmpty()
                    && menu.bound.screen().find("next_stage").isEmpty(), "No redundant button row");
                var source = screen.bounds(menu.bound.screen().find("stage_processor_2").orElseThrow());
                check(screen.mouseClicked(source.x() + 8, source.y() + 8, 0), "Open recipes from an intermediate processor");
                if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
                    check(mc.screen != screen, "Processor opens JEI when its runtime is available");
                    mc.screen.onClose();
                    check(mc.screen == screen, "Closing JEI returns to the machine");
                    var recipeTab = screen.drawers().entries().stream().filter(d -> d.id().equals(
                        net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen.RECIPE_DRAWER_ID)).findFirst().orElseThrow();
                    var bounds = recipeTab.tabBounds();
                    check(screen.mouseClicked(bounds.x() + 8, bounds.y() + 8, 0), "Recipe-book tab opens drawer even with JEI installed");
                }
                stage = 3;
                frames = 0;
            } else if (stage == 3 && mc.screen instanceof MachineScreen screen) {
                var menu = screen.getMenu();
                var drawer = (zone.moddev.mc.lib.gui.client.recipe.RecipeDrawer) screen.drawers().entries().stream()
                    .filter(d -> d.id().equals(net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen.RECIPE_DRAWER_ID))
                    .findFirst().orElseThrow();
                check(drawer.open(), "Recipe drawer opens through its tab or the no-JEI processor fallback");
                var info = screen.drawers().entries().stream().filter(d -> d instanceof zone.moddev.mc.lib.gui.client.PopoutDrawer)
                    .findFirst().orElseThrow();
                var infoTab = info.tabBounds();
                check(screen.mouseClicked(infoTab.x() + 8, infoTab.y() + 8, 0), "Info tab stays clickable while recipe drawer is open");
                check(info.open() && !drawer.open(), "Info tab switches drawers");
                var recipeTab = drawer.tabBounds();
                check(screen.mouseClicked(recipeTab.x() + 8, recipeTab.y() + 8, 0) && drawer.open(), "Recipe-book tab reopens drawer");
                check(screen.mouseClicked(recipeTab.x() + 8, recipeTab.y() + 8, 0) && !drawer.open(), "Recipe-book tab toggles closed");
                check(screen.mouseClicked(recipeTab.x() + 8, recipeTab.y() + 8, 0) && drawer.open(), "Recipe-book tab toggles open");
                check(screen.visible(menu.bound.screen().find("processing_line").orElseThrow()), "Drawer keeps processing controls visible");
                check(menu.bound.screen().find("stew_panel").isEmpty(), "Recipes no longer replace the main view");
                drawer.restore(new zone.moddev.mc.lib.gui.client.Drawer.Selection("industrial_overkill:rock_stew/stone", 0));
                var choices = drawer.visibleRecipes();
                check(!choices.isEmpty(), "Drawer lists synced recipes");
                var presentation = choices.getFirst().presentation();
                check(presentation.items().size() == 1 && presentation.items().getFirst().stack().is(Items.STONE), "First row is the source-only path");
                check(presentation.fluids().stream().anyMatch(f -> f.stack().getFluid() == Fluids.LAVA
                    && f.stack().getAmount() == 1000 && f.role() == zone.moddev.mc.lib.gui.client.recipe.RecipePresentation.Role.CATALYST), "Ghost catalyst indexed separately");
                check(presentation.fluids().stream().anyMatch(f -> f.stack().getFluid() == Fluids.LAVA
                    && f.stack().getAmount() == 100 && f.role() == zone.moddev.mc.lib.gui.client.recipe.RecipePresentation.Role.INPUT), "Consumed fluid indexed separately");
                var body = drawer.bodyBounds();
                check(body.x() >= 4 && body.right() <= screen.width - 4, "Recipe drawer fits the GUI viewport");
                check(screen.exclusions().contains(body), "JEI excludes the open drawer");
                var rail = zone.moddev.mc.lib.gui.layout.SideDrawerLayout.rail(drawer.tabBounds(), body);
                check(screen.exclusions().contains(rail), "Recipe drawer includes the full tab rail in JEI exclusions");
                check(drawer.covers(rail.x() + 1, rail.bottom() - 1), "Tab rail blocks underlying machine hover");
                mc.getSingleplayerServer().submit(() -> {
                    var processor = net.modcrafters.minecraft.industrialoverkill.stew.RockStewProcessor
                        .from(mc.getSingleplayerServer().overworld().getBlockEntity(POS));
                    check(processor.problem().isEmpty() && processor.stockMode(0) == 1 && processor.chain().target(0) == 1,
                        "Real stock click clears recovery and changes None to One Run on the server");
                }).join();
                shot("rock-stew-recipe-drawer.png");
                stage = 30;
                frames = 0;
            } else if (stage == 30 && mc.screen instanceof MachineScreen screen) {
                shot("rock-stew-drawer-tooltip.png");
                var drawer = (zone.moddev.mc.lib.gui.client.recipe.RecipeDrawer) screen.drawers().entries().stream()
                    .filter(d -> d.id().equals(net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen.RECIPE_DRAWER_ID))
                    .findFirst().orElseThrow();
                drawer.restore(new zone.moddev.mc.lib.gui.client.Drawer.Selection("industrial_overkill:rock_stew/stone", 0));
                var body = drawer.bodyBounds();
                check(screen.mouseClicked(body.x() + 62, body.y() + 32, 0), "Choose source-only path from drawer");
                check(!drawer.open(), "Submitted path closes the drawer");
                stage = 4;
                frames = 0;
            } else if (stage == 4) {
                if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
                    if (!RockStewJeiClientSmoke.selectTreeFromMachine()) return;
                    RockStewJeiClientSmoke.returnToMachine();
                }
                mc.getSingleplayerServer().submit(() -> {
                    var machine = mc.getSingleplayerServer().overworld().getBlockEntity(POS);
                    var processor = net.modcrafters.minecraft.industrialoverkill.stew.RockStewProcessor.from(machine);
                    check(processor.chain().target(0) == 64, "New source-only path uses its final-output Stack default");
                    check(!processor.switching() && processor.route().destination().is(Items.STONE),
                        "Recipe selection replaces the old route immediately");
                }).join();
                stage = 5;
                frames = 0;
            } else if (stage == 5 && mc.screen instanceof MachineScreen screen) {
                var menu = screen.getMenu();
                for (int i = 1; i < 5; i++) {
                    var control = (net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen.Control)
                        menu.bound.screen().find("stage_stock_" + i).orElseThrow();
                    check(screen.visible(control) && !control.enabled(menu.elementStates()), "Unused stock buttons are visible and disabled");
                    var glyph = screen.bounds(menu.bound.screen().find("stage_processor_" + i).orElseThrow());
                    // The vanilla container may consume an otherwise unhandled click.
                    // Verify navigation state after the server has had time to respond.
                    screen.mouseClicked(glyph.x() + 8, glyph.y() + 8, 0);
                    check(mc.screen == screen, "Unused processor cannot open JEI");
                }
                stage = 6;
                frames = 0;
            } else if (stage == 6 && mc.screen instanceof MachineScreen screen) {
                check(screen.visible(screen.getMenu().bound.screen().find("processing_line").orElseThrow()),
                    "Unused processor clicks leave the processing view open after server synchronization");
                shot("rock-stew-disabled-processors.png");
                var button = screen.bounds(screen.getMenu().bound.screen().find("stage_stock_0").orElseThrow());
                screen.mouseClicked(button.x() + 8, button.y() + 8, 0);
                screen.mouseReleased(button.x() + 8, button.y() + 8, 0);
                stage = 7;
                frames = 0;
            } else if ((stage == 7 || stage == 8) && mc.screen instanceof MachineScreen screen) {
                int expected = stage == 7 ? 0 : 1;
                mc.getSingleplayerServer().submit(() -> {
                    var processor = net.modcrafters.minecraft.industrialoverkill.stew.RockStewProcessor
                        .from(mc.getSingleplayerServer().overworld().getBlockEntity(POS));
                    check(processor.stockMode(0) == expected, "Exactly one stock transition per client click");
                    check(processor.chain().target(0) == expected, "Source-only target follows None or One Run");
                }).join();
                var control = screen.getMenu().bound.screen().find("stage_stock_0").orElseThrow();
                check(((Number) screen.getMenu().value(control, false)).intValue() == expected,
                    "Displayed stock icon agrees with the server mode");
                if (stage == 7) {
                    var button = screen.bounds(control);
                    screen.mouseClicked(button.x() + 8, button.y() + 8, 0);
                    screen.mouseReleased(button.x() + 8, button.y() + 8, 0);
                    stage = 8;
                    frames = 0;
                    return;
                }
                shot("rock-stew-one-run.png");
                finished = true;
                MMDLib.LOGGER.info("ROCK_STEW_CLIENT_PASS: model, two left-side tanks, compact stage columns, stock click and recipe selection");
                mc.stop();
            }
        } catch (Throwable error) { fail(error); }
    }

    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
    }

    private static void fail(Throwable error) {
        finished = true;
        MMDLib.LOGGER.error("ROCK_STEW_CLIENT_FAIL stage=" + stage, error);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }
}

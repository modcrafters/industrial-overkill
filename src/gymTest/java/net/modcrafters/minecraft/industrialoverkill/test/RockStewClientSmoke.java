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
import net.minecraft.world.item.ItemStack;
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
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

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
                player.connection.teleport(2.8, 100.8, -3.0, 33, 27);
            });
        } catch (Throwable error) { fail(error); }
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
                var inactive = net.neoforged.neoforge.client.model.data.ModelData.builder()
                    .with(zone.moddev.mc.lib.machine.neoforge.MachineVisualState.VARIABLES,
                        java.util.Map.<String, Object>of("processors.rock_stew.running", false)).build();
                for (var facing : Direction.Plane.HORIZONTAL) {
                    var rotatedState = blockState.setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
                    var rotatedModel = mc.getBlockRenderer().getBlockModel(rotatedState);
                    var runningData = rotatedModel.getModelData(mc.level, POS, rotatedState, machine.getModelData());
                    var idleData = rotatedModel.getModelData(mc.level, POS, rotatedState, inactive);
                    for (var face : Direction.values()) {
                        var runningFace = rotatedModel.getQuads(rotatedState, face,
                            net.minecraft.util.RandomSource.create(0), runningData, null);
                        var idleFace = rotatedModel.getQuads(rotatedState, face,
                            net.minecraft.util.RandomSource.create(0), idleData, null);
                        String expected = face == facing ? "rock_stew_front_active"
                            : face == Direction.UP ? "rock_stew_top_active"
                            : face == Direction.DOWN ? "rock_stew_bottom" : "rock_stew_side";
                        check(runningFace.getFirst().getSprite().contents().name().equals(
                            ResourceLocation.parse("industrial_overkill:block/" + expected)),
                            "Conditional face texture follows machine facing " + facing + "/" + face);
                        check(idleFace.getFirst().getSprite().contents().name().equals(
                            ResourceLocation.parse("industrial_overkill:block/" + expected.replace("_active", ""))),
                            "Unmatched condition restores the base face texture " + facing + "/" + face);
                        if (face == facing || face == Direction.UP) {
                            String maskName = face == facing ? "rock_stew_front_emissive" : "rock_stew_top_emissive";
                            var mask = mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                                .apply(ResourceLocation.parse("industrial_overkill:block/" + maskName));
                            checkMaskedFace(runningFace, idleFace, mask);
                        } else {
                            check(runningFace.equals(idleFace), "Unswitched faces retain the original quads");
                        }
                    }
                }
                shot("rock-stew-factory-in-game.png");
                mc.options.hideGui = false;
                stage = 2;
                frames = 0;
                server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    check(ScreenRuntime.open(player, player.serverLevel().getBlockEntity(POS)), "Open factory screen");
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
                var reagent = menu.bound.screen().find("tank_2").orElseThrow();
                check(menu.value(reagent, false) instanceof net.neoforged.neoforge.fluids.FluidStack fluid
                    && fluid.isEmpty(), "Cobblestone leaves reagent tank empty");
                var water = menu.bound.screen().find("tank_0").orElseThrow();
                var lava = menu.bound.screen().find("tank_1").orElseThrow();
                check(screen.bounds(water).x() < screen.bounds(lava).x()
                    && screen.bounds(lava).x() < screen.bounds(ghostSlot(screen)).x()
                    && screen.bounds(ghostSlot(screen)).x() < screen.bounds(reagent).x(), "Static tanks precede ghost; reagent follows production");
                check(menu.slots.stream().anyMatch(s -> s.getItem().is(Items.COBBLESTONE)), "Output synchronized");
                var energy = menu.bound.screen().find("energy").orElseThrow();
                var progress = menu.bound.screen().find("generated_progress").orElseThrow();
                var out0 = screen.bounds(menu.bound.screen().find("slot_0").orElseThrow());
                var out1 = screen.bounds(menu.bound.screen().find("slot_1").orElseThrow());
                var out2 = screen.bounds(menu.bound.screen().find("slot_2").orElseThrow());
                var out3 = screen.bounds(menu.bound.screen().find("slot_3").orElseThrow());
                check(out0.y() == out1.y() && out2.y() == out3.y()
                    && out0.x() == out2.x() && out1.x() == out3.x()
                    && out0.x() < out1.x() && out0.y() < out2.y(), "Outputs form a 2x2 grid");
                check(screen.bounds(ghostSlot(screen)).x() < screen.bounds(progress).x()
                    && screen.bounds(progress).x() < out0.x() && out1.x() < screen.bounds(reagent).x()
                    && screen.bounds(reagent).x() < screen.bounds(energy).x(), "Arrow, outputs, reagent and energy follow requested order");
                check(java.math.BigInteger.valueOf(100000).equals(menu.value(energy, true)), "Energy capacity synchronized");
                check(!MachineContent.recipeType(ResourceLocation.parse("industrial_overkill:rock_stew"))
                    .recipes(mc.level.getRecipeManager()).isEmpty(), "Machine recipe synchronized");
                shot("rock-stew-factory-screen.png");
                var ghost = ghostSlot(screen);
                var bounds = screen.bounds(ghost);
                check(bounds.width() == 18 && bounds.height() == 18, "Ghost input occupies one normal slot");
                check(menu.slot(ghost).getItem().is(Items.COBBLESTONE), "Default ghost sample synchronized");
                dropGhost(screen, ghost, Items.STONE);
                stage = 3;
                frames = 0;
            } else if (stage == 3 && mc.screen instanceof MachineScreen screen) {
                var ghost = ghostSlot(screen);
                if (!screen.getMenu().slot(ghost).getItem().is(Items.STONE)) return;
                shot("rock-stew-ghost-input.png");
                dropGhost(screen, ghost, Items.BASALT);
                stage = 4;
                frames = 0;
            } else if (stage == 4 && mc.screen instanceof MachineScreen screen) {
                var ghost = ghostSlot(screen);
                if (!screen.getMenu().slot(ghost).getItem().is(Items.BASALT)) return;
                var bounds = screen.bounds(ghost);
                check(screen.mouseClicked(bounds.x() + 8, bounds.y() + 8, 1), "Clear ghost input");
                stage = 5;
                frames = 0;
            } else if (stage == 5 && mc.screen instanceof MachineScreen screen) {
                if (!screen.getMenu().slot(ghostSlot(screen)).getItem().isEmpty()) return;
                finished = true;
                MMDLib.LOGGER.info("ROCK_STEW_CLIENT_PASS: model, fluids, output, declared ghost input, ghost drops and clear");
                mc.stop();
            }
        } catch (Throwable error) { fail(error); }
    }

    private static zone.moddev.mc.lib.gui.model.elements.SlotElement ghostSlot(MachineScreen screen) {
        return screen.getMenu().elements.stream()
            .filter(zone.moddev.mc.lib.gui.model.elements.SlotElement.class::isInstance)
            .map(zone.moddev.mc.lib.gui.model.elements.SlotElement.class::cast)
            .filter(zone.moddev.mc.lib.gui.model.elements.SlotElement::ghost).findFirst().orElseThrow();
    }

    private static void dropGhost(MachineScreen screen, zone.moddev.mc.lib.gui.model.elements.SlotElement ghost,
        net.minecraft.world.item.Item item) {
        var sample = new ItemStack(item, 64);
        var drop = screen.presentation().capability(ghost,
            zone.moddev.mc.lib.gui.client.IngredientDropHandler.class).orElseThrow();
        var targets = drop.targets(zone.moddev.mc.lib.gui.client.IngredientTypes.ITEM, sample);
        check(targets.size() == 1 && targets.getFirst().accept().test(sample), "Normal ghost-slot JEI drop accepted");
        check(sample.getCount() == 64, "Sample is not consumed");
    }

    private static void checkMaskedFace(
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> running,
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> idle,
        net.minecraft.client.renderer.texture.TextureAtlasSprite mask) {
        int stride = net.neoforged.neoforge.client.model.IQuadTransformer.STRIDE;
        int uv = net.neoforged.neoforge.client.model.IQuadTransformer.UV0;
        int light = net.neoforged.neoforge.client.model.IQuadTransformer.UV2;
        double area = 0;
        int glowing = 0, ordinary = 0;
        for (var quad : running) {
            float u0 = Float.POSITIVE_INFINITY, v0 = u0;
            float u1 = Float.NEGATIVE_INFINITY, v1 = u1;
            for (int vertex = 0; vertex < 4; vertex++) {
                float u = quad.getSprite().getUOffset(Float.intBitsToFloat(quad.getVertices()[vertex * stride + uv]));
                float v = quad.getSprite().getVOffset(Float.intBitsToFloat(quad.getVertices()[vertex * stride + uv + 1]));
                u0 = Math.min(u0, u); u1 = Math.max(u1, u);
                v0 = Math.min(v0, v); v1 = Math.max(v1, v);
            }
            area += (u1 - u0) * (v1 - v0);
            int x = Math.clamp((int) ((u0 + u1) * 0.5f * mask.contents().width()), 0, mask.contents().width() - 1);
            int y = Math.clamp((int) ((v0 + v1) * 0.5f * mask.contents().height()), 0, mask.contents().height() - 1);
            boolean expectedGlow = (mask.contents().getOriginalImage().getPixelRGBA(x, y) & 255) >= 128;
            if (expectedGlow) {
                glowing++;
                check(!quad.isShade() && !quad.hasAmbientOcclusion(), "Only mask-selected pixels disable shading");
                for (int vertex = 0; vertex < 4; vertex++)
                    check(quad.getVertices()[vertex * stride + light] == net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                        "Mask-selected pixels render at full brightness");
            } else {
                ordinary++;
                check(quad.isShade() == idle.getFirst().isShade()
                    && quad.hasAmbientOcclusion() == idle.getFirst().hasAmbientOcclusion(),
                    "Unmasked metal keeps ordinary lighting");
            }
        }
        var base = idle.getFirst();
        float minU = Float.POSITIVE_INFINITY, minV = minU;
        float maxU = Float.NEGATIVE_INFINITY, maxV = maxU;
        for (int vertex = 0; vertex < 4; vertex++) {
            float u = base.getSprite().getUOffset(Float.intBitsToFloat(base.getVertices()[vertex * stride + uv]));
            float v = base.getSprite().getVOffset(Float.intBitsToFloat(base.getVertices()[vertex * stride + uv + 1]));
            minU = Math.min(minU, u); maxU = Math.max(maxU, u);
            minV = Math.min(minV, v); maxV = Math.max(maxV, v);
        }
        check(Math.abs(area - (maxU - minU) * (maxV - minV)) < 0.0001,
            "Mask partitions the face without overlapping glow geometry or losing area");
        check(glowing > 0 && ordinary > 0, "Each switched face contains both cyan and normally lit metal");
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

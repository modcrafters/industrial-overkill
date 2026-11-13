package net.modcrafters.minecraft.industrialoverkill.test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGymEntity;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;
import zone.moddev.mc.lib.MMDLib;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

/** Repeatable integrated-client benchmark; fixtures and instrumentation never ship in the mod. */
@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT)
public final class GymPerformance {
    private static final String[] CASES = {"empty", "three_loose", "three_captive"};
    private static final List<AnimalGymEntity> GYMS = new ArrayList<>();
    private static final List<Animal> ANIMALS = new ArrayList<>();
    private static final List<Double> TICKS = new ArrayList<>(), FRAMES = new ArrayList<>();
    private static final List<String> RESULTS = new ArrayList<>();
    private static volatile boolean finished, ready, measuring;
    private static boolean started, placed;
    private static int phase, ticks;
    private static long tickStart, lastFrame;
    private static Recording recording;
    private static final long DEADLINE = System.currentTimeMillis() + 240000;

    private static boolean active() {
        return Boolean.getBoolean("industrialoverkill.gymPerformance") && !finished;
    }

    @SubscribeEvent
    public static void client(ClientTickEvent.Post event) {
        if (!active())
            return;
        var mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() > DEADLINE)
                throw new AssertionError("Benchmark timeout");
            if (!started && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                started = true;
                mc.createWorldOpenFlows().createFreshLevel("perf-" + System.currentTimeMillis(),
                    new LevelSettings("Gym performance", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1, false, false),
                    registries -> registries
                        .registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    mc.screen);
            }
            if (mc.level == null || mc.player == null || placed)
                return;
            placed = true;
            mc.options.hideGui = true;
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.start();
            RESULTS.add(
                "scenario,ticks,frames,tick_mean_ms,tick_p95_ms,frame_mean_ms,frame_p95_ms,frames_over_50ms");
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var level = player.serverLevel();
                    level.setDayTime(1000);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,
                        level.getServer());
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,
                        level.getServer());
                    for (int x = -12; x <= 12; x++)
                        for (int z = -12; z <= 12; z++)
                            level.setBlock(new BlockPos(x, 99, z),
                                Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    player.connection.teleport(4.5, 100, -6, 25, 10);
                    ready = true;
                } catch (Throwable error) {
                    fail(error);
                }
            });
        } catch (Throwable error) {
            fail(error);
        }
    }

    @SubscribeEvent
    public static void before(ServerTickEvent.Pre event) {
        if (!active() || !ready)
            return;
        tickStart = System.nanoTime();
        for (var gym : GYMS)
            gym.energy.setStored(0); // Constant energy consumer.
    }

    @SubscribeEvent
    public static void after(ServerTickEvent.Post event) {
        if (!active() || !ready)
            return;
        try {
            ++ticks;
            if (ticks == 90) {
                var mc = Minecraft.getInstance();
                var name = "performance-" + CASES[phase] + ".png";
                mc.execute(() -> net.minecraft.client.Screenshot.grab(mc.gameDirectory, name,
                    mc.getMainRenderTarget(), message -> {
                    }));
            }
            if (ticks == 100) {
                synchronized (FRAMES) {
                    FRAMES.clear();
                    lastFrame = 0;
                    measuring = true;
                }
                TICKS.clear();
            }
            if (ticks > 100) {
                for (var gym : GYMS)
                    if (gym.getGeneration() != 16)
                        throw new AssertionError("Benchmark gym stopped working");
                TICKS.add((System.nanoTime() - tickStart) / 1e6);
            }
            if (ticks < 300)
                return;
            synchronized (FRAMES) {
                measuring = false;
                var row = String.format(java.util.Locale.ROOT, "%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%d",
                    CASES[phase], TICKS.size(), FRAMES.size(), mean(TICKS), p95(TICKS),
                    mean(FRAMES), p95(FRAMES),
                    FRAMES.stream().filter(value -> value > 50).count());
                RESULTS.add(row);
                MMDLib.LOGGER.info("GYM_PERFORMANCE: {}", row);
            }
            if (++phase == CASES.length) {
                ready = false;
                var mc = Minecraft.getInstance();
                Files.write(mc.gameDirectory.toPath().resolve("results.csv"), RESULTS);
                recording.stop();
                recording.dump(mc.gameDirectory.toPath().resolve("profile.jfr"));
                recording.close();
                finished = true;
                MMDLib.LOGGER.info("GYM_PERFORMANCE_PASS");
                mc.execute(mc::stop);
                return;
            }
            configure();
            ticks = 0;
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static void configure() {
        var mc = Minecraft.getInstance();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        var level = player.serverLevel();
        if (phase == 1) {
            for (int i = 0; i < 3; i++) {
                var pos = new BlockPos(0, 100, i * 3);
                player.connection.teleport(4.5, 100, -6, 0, 10);
                player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(AnimalGym.ITEM.get()));
                net.neoforged.neoforge.common.CommonHooks
                    .onPlaceItemIntoWorld(new UseOnContext(player,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
                var gym = (AnimalGymEntity) level.getBlockEntity(pos);
                if (gym == null)
                    throw new AssertionError("Place benchmark gym");
                gym.food.insertItem(0, new ItemStack(Items.WHEAT, 64), false);
                var cow = EntityType.COW.create(level);
                var center = gym.beltCenter();
                cow.setPos(center.x, center.y, center.z);
                level.addFreshEntity(cow);
                GYMS.add(gym);
                ANIMALS.add(cow);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.connection.teleport(4.5, 100, -6, 25, 10);
        } else {
            for (int i = 0; i < GYMS.size(); i++) {
                var gym = GYMS.get(i);
                gym.training.reset();
                var cow = ANIMALS.get(i);
                var stack = CritterCourierProcessor.OCCUPANT.stack(CritterCourierProcessor.ENTITIES.capture(cow));
                cow.discard();
                gym.food.insertItem(3, stack, false);
            }
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!active())
            return;
        synchronized (FRAMES) {
            if (!measuring)
                return;
            long now = System.nanoTime();
            if (lastFrame != 0)
                FRAMES.add((now - lastFrame) / 1e6);
            lastFrame = now;
        }
    }

    private static double mean(List<Double> samples) {
        return samples.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
    private static double p95(List<Double> samples) {
        var sorted = samples.stream().sorted().toList();
        return sorted.isEmpty()
            ? 0
            : sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * .95)));
    }
    private static void fail(Throwable error) {
        finished = true;
        MMDLib.LOGGER.error("GYM_PERFORMANCE_FAIL", error);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }
}

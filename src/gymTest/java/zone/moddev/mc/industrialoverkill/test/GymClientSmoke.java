package zone.moddev.mc.industrialoverkill.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

import zone.moddev.mc.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.industrialoverkill.client.GymAnimalAnimation;
import zone.moddev.mc.industrialoverkill.client.GymBiteAnimation;
import zone.moddev.mc.industrialoverkill.client.GymFoodDisplay;
import zone.moddev.mc.industrialoverkill.gym.AnimalGym;
import zone.moddev.mc.industrialoverkill.gym.AnimalGymEntity;
import zone.moddev.mc.industrialoverkill.gym.GymAnimalFit;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.client.Drawer;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.client.RedstoneDrawer;
import zone.moddev.mc.lib.gui.client.SideConfigDrawer;
import zone.moddev.mc.lib.gui.layout.AutomationLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.RedstoneLayout;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT)
public final class GymClientSmoke {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static final long DEADLINE = System.currentTimeMillis() + 180000;
    private static boolean started, placed, opened, nightRequested;
    private static int nightFrames;
    private static boolean sawMealContact, sawMealChewing, sawMealRefill;
    private static volatile boolean finished, removed;
    private static int frames, stage, configStep;
    private static float walkPhase, initialStride;
    private static int gazeSamples;
    private static boolean sawApproachGaze, sawChewingGaze;
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void verifyGaze(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("industrialoverkill.gymSmoke") || finished || opened || frames < 20
            || mc.level == null || !(mc.level.getBlockEntity(POS) instanceof AnimalGymEntity gym)
            || gym.getGeneration() <= 0
            || !(mc.level.getEntity(gym.getAnimalId()) instanceof Animal runner))
            return;
        float age = gym.feeding.age(0);
        if (gym.displayFood().isEmpty() && age >= 36)
            return;
        try {
            assertLookingAtFood(gym, runner, false);
            gazeSamples++;
            sawApproachGaze |= age > 0 && age < 6;
            sawChewingGaze |= age >= 6 && age < 36;
        } catch (Throwable failure) {
            fail(failure);
        }
    }
    private static java.util.UUID animal;
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("industrialoverkill.gymSmoke") || finished)
            return;
        var mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() > DEADLINE)
                throw new AssertionError("Timed out at stage " + stage + ": " + mc.screen);
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                mc.createWorldOpenFlows().createFreshLevel("gym-" + System.currentTimeMillis(),
                    new LevelSettings("Animal Gym", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    mc.screen);
            }
            if (mc.player == null || mc.level == null)
                return;
            if (!placed) {
                placed = true;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers()
                            .getFirst();
                        var level = player.serverLevel();
                        level.getChunkAt(POS);
                        level.setDayTime(1000);
                        for (int x = -8; x <= 12; x++)
                            for (int z = -8; z <= 8; z++)
                                level.setBlock(new BlockPos(x, 99, z),
                                    Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                        player.connection.teleport(5.5, 100, -5.5, 0, 8);
                        player.setGameMode(GameType.SURVIVAL);
                        player.setItemInHand(InteractionHand.MAIN_HAND,
                            new ItemStack(AnimalGym.ITEM.get(), 2));
                        var use = new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(POS), Direction.UP, POS, false));
                        if (!net.neoforged.neoforge.common.CommonHooks.onPlaceItemIntoWorld(use)
                            .consumesAction() || player.getMainHandItem().getCount() != 1)
                            throw new AssertionError("Survival placement");
                        var gym = (AnimalGymEntity) level.getBlockEntity(POS);
                        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 32), false);
                        var cow = EntityType.COW.create(level);
                        cow.getAttribute(Attributes.SCALE).setBaseValue(2);
                        cow.refreshDimensions();
                        var center = gym.beltCenter();
                        cow.setPos(center.x, center.y, center.z);
                        level.addFreshEntity(cow);
                        animal = cow.getUUID();
                        if (Boolean.getBoolean("industrialoverkill.gymDemandSmoke")) {
                            cow.setNoAi(true); // Keep the unrestrained test cow on the belt during rests.
                            gym.energy.setStored(31984);
                            GymDemandTests.factory(gym);
                        }
                        player.connection.teleport(2.7, 100, -3.7, 38, 10);
                    } catch (Throwable failure) {
                        fail(failure);
                    }
                });
            }
            if (removed && mc.level.getBlockState(POS).isAir()) {
                finished = true;
                MMDLib.LOGGER.info(
                    "ANIMAL_GYM_CLIENT_PASS: gym UI, configuration, intensity, pause/resume and teardown (GUI-only mode: {})",
                    Boolean.getBoolean("industrialoverkill.gymUiOnly"));
                mc.stop();
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }
    @SubscribeEvent
    public static void render(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("industrialoverkill.gymSmoke") || finished || removed)
            return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(POS) instanceof AnimalGymEntity gym))
            return;
        try {
            if (Boolean.getBoolean("industrialoverkill.gymDemandSmoke")) {
                opened = true;
                GymDemandClientSmoke.render(gym);
                return;
            }
            if (!opened && Boolean.getBoolean("industrialoverkill.gymUiOnly")) {
                if (gym.getGeneration() <= 0)
                    return;
                initialStride = GymAnimalAnimation.stride(1);
                opened = true;
                frames = 0;
                mc.options.hideGui = false;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers()
                            .getFirst();
                        if (!ScreenRuntime.open(player, player.serverLevel().getBlockEntity(POS),
                            POS.west(2)))
                            throw new AssertionError("Part menu opening");
                    } catch (Throwable failure) {
                        fail(failure);
                    }
                });
                return;
            }
            if (!opened) {
                // Keep physical mouse movement from changing automated reference shots.
                mc.mouseHandler.releaseMouse();
                mc.player.setYRot(38);
                mc.player.setXRot(10);
                mc.player.yRotO = 38;
                mc.player.xRotO = 10;
                mc.options.hideGui = true;
                if (gym.getGeneration() <= 0)
                    return;
                frames++;
                recordEating(gym);
                if (!(mc.level.getEntity(gym.getAnimalId()) instanceof Animal runner))
                    return;
                if (frames == 20) {
                    if (GymAnimalAnimation.workingGym(runner) != gym
                        || runner.walkAnimation.speed() < .1f)
                        throw new AssertionError("Working animal has no walking animation");
                    if (runner.getBbWidth() > GymAnimalFit.MAX_WIDTH + .001
                        || runner.getBbHeight() > GymAnimalFit.MAX_HEIGHT + .001)
                        throw new AssertionError("Fitted dimensions did not synchronize to client");
                    walkPhase = runner.walkAnimation.position();
                    initialStride = runner.walkAnimation.speed();
                    shot("animal-gym-walk-a.png");
                }
                if (frames < 40)
                    return;
                if (runner.walkAnimation.position() <= walkPhase + 1
                    || runner.position().distanceTo(gym.beltCenter()) > .05
                    || Math.abs(net.minecraft.util.Mth
                        .wrapDegrees(runner.getYRot() - gym.trainingFacing().toYRot())) > 1)
                    throw new AssertionError("Runner must animate in place facing the controller");
                if (!nightRequested) {
                    if (!sawMealRefill)
                        return;
                    if (GymBiteAnimation.reach(runner, 0) < .09f)
                        return;
                    shot("animal-gym-bite.png");
                    shot("animal-gym-world.png");
                    if (!gym.displayFood().is(Items.WHEAT) || gym.displayFood().getCount() != 1)
                        throw new AssertionError("Working gym food display did not synchronize");
                    // Gaze is checked in verifyGaze at tick end. Network updates between
                    // ticks can change food state before the live head has been posed again.
                    if (gazeSamples < 10 || !sawApproachGaze || !sawChewingGaze)
                        throw new AssertionError(
                            "Gaze regression must cover running, approach and chewing");
                    MMDLib.LOGGER.info(
                        "GYM_GAZE_CLIENT_PASS: {} same-tick samples across running and eating",
                        gazeSamples);
                    nightRequested = true;
                    mc.getSingleplayerServer()
                        .execute(() -> mc.getSingleplayerServer().overworld().setDayTime(18000));
                    return;
                }
                if (++nightFrames < 40 || mc.level.getDayTime() % 24000 < 17000)
                    return;
                if (mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                    POS.west().above()) < 12)
                    throw new AssertionError("Working lamps must illuminate the animal space");
                for (String name : java.util.List.of("gym_belt_lamp", "gym_belt_end_lamp",
                    "gym_belt_lamp_off", "gym_belt_end_lamp_off")) {
                    var model = mc.getModelManager()
                        .getModel(net.minecraft.client.resources.model.ModelResourceLocation
                            .standalone(AnimalGym.id("block/" + name)));
                    var quads = model.getQuads(null, null,
                        net.minecraft.util.RandomSource.create(42));
                    if (model == mc.getModelManager().getMissingModel() || quads.size() != 6)
                        throw new AssertionError("Lamp glass must bake all six faces: " + name);
                }
                shot("animal-gym-lights-on-night.png");
                mc.options.hideGui = false;
                opened = true;
                frames = 0;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers()
                            .getFirst();
                        if (!ScreenRuntime.open(player, player.serverLevel().getBlockEntity(POS),
                            POS.west(2)))
                            throw new AssertionError("Part menu opening");
                    } catch (Throwable failure) {
                        fail(failure);
                    }
                });
                return;
            }
            if (stage == 4) {
                mc.options.hideGui = true;
                if (++frames < 40)
                    return;
                var released = mc.level.entitiesForRendering();
                for (var entity : released)
                    if (entity instanceof Animal former && former.getUUID().equals(animal)
                        && (GymAnimalAnimation.workingGym(former) != null
                            || Math.abs(former.getScale() - 2) > .001))
                        throw new AssertionError(
                            "Released animal still receives treadmill animation");
                shot("animal-gym-model.png");
                if (!gym.displayFood().isEmpty())
                    throw new AssertionError("Stopped gym still displays food");
                stage = 40;
                frames = 0;
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    player.connection.teleport(-4.5, 100, 2.8, -120, 8);
                });
                return;
            }
            if (stage == 40) {
                if (++frames < 40)
                    return;
                shot("animal-gym-rear-support.png");
                stage = 41;
                frames = 0;
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    player.serverLevel().setDayTime(18000);
                    player.connection.teleport(2.7, 100, -3.7, 38, 10);
                });
                return;
            }
            if (stage == 41) {
                if (++frames < 40)
                    return;
                if (mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                    POS.west().above()) >= 12)
                    throw new AssertionError(
                        "Stopped lamps must stop illuminating the animal space");
                shot("animal-gym-lights-off-night.png");
                stage = 5;
                mc.options.hideGui = false;
                mc.getSingleplayerServer().execute(() -> {
                    mc.getSingleplayerServer().overworld().removeBlock(POS.west(2), false);
                    removed = true;
                });
                return;
            }
            if (stage == 10) {
                if (++frames < 10)
                    return;
                if (mc.screen == null || mc.screen instanceof MachineScreen)
                    throw new AssertionError("Recipe button did not open JEI");
                shot("animal-gym-recipes.png");
                mc.screen.onClose();
                stage = 1;
                frames = 0;
                return;
            }
            if (!(mc.screen instanceof MachineScreen screen))
                return;
            if (++frames < 30)
                return;
            if (!screen.getMenu().stillValid(mc.player))
                throw new AssertionError("Invalid menu anchor");
            if (stage == 0) {
                if (!configuration(screen, gym))
                    return;
                var food = screen.getMenu().nativeSlots().entries().stream()
                    .filter(e -> e.element() != null
                        && e.element().common().id().orElse("").equals("food0"))
                    .findFirst().orElseThrow();
                if (!food.slot().getItem().is(Items.WHEAT)
                    || !(mc.level.getEntity(gym.getAnimalId()) instanceof Animal))
                    throw new AssertionError("Food or portrait not synchronized");
                var definition = screen.getMenu().bound.screen();
                var portraitBounds = screen.layout
                    .bounds(definition.find("portrait").orElseThrow());
                var foodBounds = screen.layout.bounds(food.element());
                var trapBounds = screen.layout.bounds(definition.find("trap").orElseThrow());
                var intensityBounds = screen.layout
                    .bounds(definition.find("intensity").orElseThrow());
                var toggleBounds = screen.layout.bounds(definition.find("toggle").orElseThrow());
                var toolbarBounds = screen.layout.region(
                    zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement.TOOLBAR)
                    .orElseThrow();
                if (toggleBounds.x() < toolbarBounds.right()
                    || toggleBounds.y() + toggleBounds.height() / 2 != toolbarBounds.y()
                        + toolbarBounds.height() / 2)
                    throw new AssertionError(
                        "Run control must align with the right of the colour toolbar");
                if (trapBounds.right() >= portraitBounds.x()
                    || foodBounds.y() < portraitBounds.bottom()
                    || foodBounds.x() != portraitBounds.x()
                    || intensityBounds.x() <= foodBounds.right())
                    throw new AssertionError("Trap/portrait/food/intensity layout");
                for (String removed : java.util.List.of("release", "speed", "strength", "health",
                    "fitness"))
                    if (definition.find(removed).isPresent())
                        throw new AssertionError("Old control still present: " + removed);
                if (!gym.getStatusText().getString().startsWith("Generating power")
                    || gym.getStatusText().getString().contains("Training:"))
                    throw new AssertionError(
                        "Status must describe generation without a training mode");
                var statusElement = definition.find("status").orElseThrow();
                var statusText = (net.minecraft.network.chat.Component) screen.getMenu()
                    .value(statusElement, false);
                if (!statusText.getString().matches("(?s).*Power left: [^\n]* FE\nTime left: [^\n]*s.*")
                    || gym.getPowerLeft() <= 0 || gym.getFoodTicksLeft() <= 0)
                    throw new AssertionError("Remaining food power must synchronize to the client and menu");
                var statusBounds = screen.layout.bounds(statusElement);
                if (mc.font.split(statusText, statusBounds.width()).size() * 11 > statusBounds.height())
                    throw new AssertionError("Power left must fit within the status panel");
                assertFoodTooltip(screen, 1, 16, "15");
                var recipes = definition.find("recipes").orElseThrow();
                boolean jei = net.neoforged.fml.ModList.get().isLoaded("jei");
                if (screen.visible(recipes) != jei)
                    throw new AssertionError(
                        "Recipe button visibility must follow JEI availability");
                shot("animal-gym-menu.png");
                if (jei) {
                    click(screen, "recipes");
                    stage = 10;
                    frames = 0;
                } else
                    stage = 1;
            } else if (stage == 1) {
                click(screen, "intensity");
                stage = 2;
            } else if (stage == 2 && gym.training.intensity() == 2) {
                if (!(mc.level.getEntity(gym.getAnimalId()) instanceof Animal runner)
                    || runner.walkAnimation.speed() <= initialStride)
                    return;
                assertFoodTooltip(screen, 2, 32, "7.5");
                click(screen, "toggle");
                stage = 3;
            } else if (stage == 3 && !gym.training.enabled() && gym.getGeneration() == 0) {
                shot("animal-gym-paused-menu.png");
                click(screen, "toggle");
                stage = 6;
            } else if (stage == 6 && gym.training.enabled() && gym.getGeneration() > 0) {
                click(screen, "toggle");
                stage = 7;
            } else if (stage == 7 && !gym.training.enabled() && gym.getGeneration() == 0) {
                stage = 4;
                frames = 0;
                mc.player.closeContainer();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var level = mc.getSingleplayerServer().overworld();
                        var cow = (Animal) level.getEntity(animal);
                        if (cow == null || !cow.isAlive() || cow.isNoAi()
                            || Math.abs(cow.getScale() - 2) > .001)
                            throw new AssertionError("Animal not safely released");
                        cow.teleportTo(0, 100, 6);
                    } catch (Throwable failure) {
                        fail(failure);
                    }
                });
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }
    private static void recordEating(AnimalGymEntity gym) {
        float age = gym.feeding.age(0);
        if (!sawMealContact && age >= 6 && age < 12) {
            if (!gym.displayFood().isEmpty() || gym.feeding.crumbs().isEmpty())
                throw new AssertionError(
                    "Bite contact must hide the serving and expose crumb data");
            if (!(gym.getLevel().getEntity(gym.getAnimalId()) instanceof Mob runner)
                || GymBiteAnimation.reach(runner, 0) <= .05f)
                throw new AssertionError("No real eating lunge at contact");
            sawMealContact = true;
            shot("animal-gym-eating-contact.png");
        }
        if (sawMealContact && !sawMealChewing && age >= 20 && age < 32) {
            if (!gym.displayFood().isEmpty() || gym.feeding.crumbs().isEmpty())
                throw new AssertionError("Chewing state/crumbs disappeared early");
            if (gym.getLevel().getEntity(gym.getAnimalId()) instanceof Mob runner
                && (GymBiteAnimation.reach(runner, 0) != 0 || runner.walkAnimation.speed() < .1f))
                throw new AssertionError("Eating must settle straight back into running");
            sawMealChewing = true;
            shot("animal-gym-eating-crumbs.png");
        }
        if (sawMealChewing && !sawMealRefill && age >= 56 && !gym.displayFood().isEmpty()) {
            if (gym.feeding.serving().getCount() != 1 || !gym.feeding.crumbs().isEmpty())
                throw new AssertionError("Refill must own one paid serving and end the effect");
            sawMealRefill = true;
            shot("animal-gym-food-refilled.png");
            MMDLib.LOGGER.info(
                "GYM_EATING_CLIENT_PASS: synchronized lunge/contact, 1.5-second chewing, immediate running recovery and delayed paid refill");
        }
    }
    private static boolean configuration(MachineScreen screen, AnimalGymEntity gym) {
        var mc = Minecraft.getInstance();
        var menu = screen.getMenu();
        var sides = menu.components().get(BuiltinMenuComponents.AUTOMATION);
        var redstone = menu.components().get(BuiltinMenuComponents.REDSTONE);
        if (sides == null || redstone == null)
            throw new AssertionError("Both configuration drawers must exist");
        if (sides.pending() || redstone.pending())
            return false;
        Drawer sideDrawer = screen.drawers().entries().stream()
            .filter(SideConfigDrawer.class::isInstance).findFirst().orElseThrow();
        Drawer redDrawer = screen.drawers().entries().stream()
            .filter(RedstoneDrawer.class::isInstance).findFirst().orElseThrow();
        int right = RelativeFace.RIGHT.ordinal(), top = RelativeFace.TOP.ordinal();
        switch (configStep) {
            case 0 -> clickRect(screen, sideDrawer.tabBounds(), 0);
            case 1 -> {
                if (!sides.open())
                    return false;
                if (sides.addonsEnabled() || sides.faceAvailable(0, right)
                    || sides.faceAvailable(1, right) || sides.faceAvailable(0, top)
                    || sides.faceAvailable(1, top))
                    throw new AssertionError("Both groups block RIGHT and have no addons");
                clickRect(screen, face(sideDrawer, AutomationLayout.face(right)), 0);
                clickRect(screen, face(sideDrawer, AutomationLayout.face(top)), 0);
                if (sides.pending())
                    throw new AssertionError("Unavailable side click sent an action");
                shot("animal-gym-side-config.png");
                clickRect(screen, face(sideDrawer, AutomationLayout.face(0)), 0);
            }
            case 2 -> {
                if (sides.mode(0, 0) != IoMode.DISABLED)
                    throw new AssertionError("Available food side edit did not sync");
                clickRect(screen, face(sideDrawer, AutomationLayout.face(0)), 1);
            }
            case 3 -> {
                if (sides.mode(0, 0) != IoMode.INPUT)
                    throw new AssertionError("Reverse side edit did not sync");
                sides.send("select", 1, -1, "", 0, "");
            }
            case 4 -> {
                if (sides.selected() != 1 || sides.mode(1, 0) != IoMode.OUTPUT)
                    throw new AssertionError("Energy group selection/output");
                shot("animal-gym-energy-config.png");
                clickRect(screen, redDrawer.tabBounds(), 0);
            }
            case 5 -> {
                if (!redstone.open() || sides.open() || redstone.faceAvailable(right)
                    || redstone.faceAvailable(top))
                    throw new AssertionError("Redstone restriction/exclusion not synchronized");
                clickRect(screen, face(redDrawer, RedstoneLayout.face(right)), 0);
                clickRect(screen, face(redDrawer, RedstoneLayout.face(top)), 0);
                if (redstone.pending() || redstone.selected() == right)
                    throw new AssertionError("Unavailable redstone face selected");
                shot("animal-gym-redstone-config.png");
                clickRect(screen, face(redDrawer, RedstoneLayout.face(0)), 0);
            }
            case 6 -> {
                if (redstone.mode(0) != RedstoneControl.Mode.READ)
                    throw new AssertionError("Read mode did not synchronize");
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld()
                    .setBlock(POS.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3));
            }
            case 7 -> {
                if (gym.getGeneration() != 0 || !redstone.paused())
                    return false;
                if (!gym.displayFood().isEmpty())
                    throw new AssertionError("Redstone pause kept bait visible");
                shot("animal-gym-redstone-paused.png");
                mc.getSingleplayerServer().execute(
                    () -> mc.getSingleplayerServer().overworld().removeBlock(POS.north(), false));
            }
            case 8 -> {
                if (gym.getGeneration() <= 0 || redstone.paused())
                    return false;
                mc.getSingleplayerServer()
                    .execute(() -> ((AnimalGymEntity) mc.getSingleplayerServer().overworld()
                        .getBlockEntity(POS)).screenRedstone()
                        .disabledFaces(java.util.Set.of(RelativeFace.values())));
            }
            case 9 -> {
                for (int f = 0; f < 6; f++)
                    if (redstone.faceAvailable(f))
                        return false;
                clickRect(screen, face(redDrawer, RedstoneLayout.face(0)), 0);
                if (redstone.pending())
                    throw new AssertionError("All-unavailable drawer accepted edit");
                shot("animal-gym-redstone-all-unavailable.png");
                mc.getSingleplayerServer()
                    .execute(() -> ((AnimalGymEntity) mc.getSingleplayerServer().overworld()
                        .getBlockEntity(POS)).screenRedstone()
                        .disabledFaces(AnimalGym.UNAVAILABLE_FACES));
            }
            case 10 -> {
                if (!redstone.faceAvailable(0))
                    return false;
                if (redstone.mode(0) != RedstoneControl.Mode.IGNORE)
                    throw new AssertionError("Re-enable restored old mode");
                clickRect(screen, redDrawer.tabBounds(), 0);
            }
            default -> {
                MMDLib.LOGGER.info(
                    "GYM_CONFIGURATION_CLIENT_PASS: live side edits, blocked faces, energy group, redstone pause/resume, changing policy and all-unavailable recovery");
                return true;
            }
        }
        configStep++;
        frames = 0;
        return false;
    }
    private static Rect face(Drawer drawer, Rect face) {
        var body = drawer.exclusions().get(1);
        return face.offset(body.x(), body.y());
    }
    private static void clickRect(MachineScreen screen, Rect rect, int button) {
        if (!screen.mouseClicked(rect.x() + 5, rect.y() + 5, button))
            throw new AssertionError("Drawer click not handled");
        screen.mouseReleased(rect.x() + 5, rect.y() + 5, button);
    }
    private static void click(MachineScreen screen, String id) {
        var element = screen.getMenu().elements.stream()
            .filter(e -> e.common().id().orElse("").equals(id)).findFirst().orElseThrow();
        var bounds = screen.bounds(element);
        if (!screen.mouseClicked(bounds.x() + 3, bounds.y() + 3, 0))
            throw new AssertionError("Click not handled: " + id);
        screen.mouseReleased(bounds.x() + 3, bounds.y() + 3, 0);
    }
    private static void assertFoodTooltip(MachineScreen screen, int intensity, int power,
        String duration) {
        var food = screen.getMenu().bound.screen().find("food0").orElseThrow();
        var bounds = screen.bounds(food);
        var rows = screen.tooltipLines(food, bounds.x(), bounds.y()).stream()
            .map(Component -> Component.getString()).toList();
        for (var expected : java.util.List.of("At intensity " + intensity,
            "Power: " + power + " FE/t",
            "Food lasts: " + duration + " s", "Total per food: 4,800 FE"))
            if (!rows.contains(expected))
                throw new AssertionError("Cow food tooltip missing '" + expected + "': " + rows);
    }
    static void assertLookingAtFood(AnimalGymEntity gym, Mob animal, boolean captive) {
        var dimensions = animal.getDimensions(animal.getPose());
        float scale = captive
            ? Math.min(1, Math.min(.8f / dimensions.width(), 1.25f / dimensions.height()))
            : 1;
        var eyes = animal.position().add(0, animal.getEyeHeight() * scale, 0);
        // Detached captives are posed at render time; live animals are posed at tick end.
        float partialTick = captive
            ? Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)
            : 0;
        var target = GymFoodDisplay.position(gym, partialTick).subtract(eyes).normalize();
        var gaze = Vec3.directionFromRotation(
            animal.getXRot() - GymBiteAnimation.pitchOffset(animal, partialTick),
            animal.getYHeadRot());
        if (gaze.dot(target) < .9999
            || Math.abs(net.minecraft.util.Mth
                .wrapDegrees(animal.yBodyRot - gym.trainingFacing().toYRot())) > 1)
            throw new AssertionError(
                "Runner must look at the bait while its body stays aligned with the belt: dot="
                    + gaze.dot(target) + ", pitch=" + animal.getXRot() + ", head="
                    + animal.getYHeadRot()
                    + ", body=" + animal.yBodyRot + ", tick=" + animal.level().getGameTime()
                    + ", partial=" + partialTick + ", age=" + gym.feeding.age(partialTick)
                    + ", bite="
                    + GymBiteAnimation.pitchOffset(animal, partialTick)
                    + ", target=" + GymFoodDisplay.position(gym, partialTick) + ", eyes=" + eyes);
    }
    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
        });
    }
    private static void fail(Throwable failure) {
        finished = true;
        MMDLib.LOGGER.error("ANIMAL_GYM_CLIENT_FAIL", failure);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }
}

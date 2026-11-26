package net.modcrafters.minecraft.industrialoverkill.test;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Panda;
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
import net.neoforged.neoforge.common.CommonHooks;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGymEntity;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT)
public final class CritterCourierClientSmoke {
    private static final BlockPos GYM = new BlockPos(0, 100, 0), TRAP = new BlockPos(2, 100, 0);
    private static final long DEADLINE = System.currentTimeMillis() + 180000;
    private static boolean started, placed;
    private static volatile boolean finished;
    private static int stage, frames;
    private static UUID occupant;
    private static int petIndex;
    private static boolean petRenderedStanding;
    private static boolean courierPortraitRendered;
    private static boolean courierItemRendered;
    @SubscribeEvent
    public static void courierPortrait(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        if (active() && stage == 2 && Minecraft.getInstance().screen instanceof MachineScreen
            && event.getEntity().getUUID().equals(occupant)) courierPortraitRendered = true;
        if (active() && (stage == 31 || stage == 5) && event.getEntity().getUUID().equals(occupant)) {
            var calls = StackWalker.getInstance().walk(frames -> frames
                .map(StackWalker.StackFrame::getClassName).toList());
            boolean item = calls.contains("zone.moddev.mc.lib.machine.neoforge.client.MachineEntityDisplays$ItemDisplay");
            boolean inventory = calls.contains("net.minecraft.client.gui.GuiGraphics");
            if (item && inventory == (stage == 5))
                courierItemRendered = true;
        }
    }
    private static float petFirstStride = -1;
    @SubscribeEvent
    public static void petRender(
        net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        if (!active() || stage < 20 || stage > 22)
            return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(GYM) instanceof AnimalGymEntity gym)
            || event.getEntity() != gym.captivePreview())
            return;
        // The GUI portrait reuses this mob after the treadmill's temporary pose has been
        // restored. Assert the world renderer's override only, not the normal inventory pose.
        // Keep render-context introspection in this test instead of adding production flags.
        if (!StackWalker.getInstance().walk(stack -> stack.anyMatch(frame -> frame.getClassName()
            .equals("net.modcrafters.minecraft.industrialoverkill.client.CagedMobRenderer"))))
            return;
        try {
            var pet = (Animal) event.getEntity();
            if (pet instanceof TamableAnimal tame)
                check(tame.isOrderedToSit(), "Preview retains original sit command");
            if (gym.getGeneration() > 0) {
                check(standing(pet) && pet.walkAnimation.speed() > .1f,
                    "Running pet renders standing with animated legs");
                petRenderedStanding = true;
                if (petFirstStride < 0)
                    petFirstStride = pet.walkAnimation.position();
            } else {
                check(originalPose(pet), "Paused animal renders in its original resting pose");
                if (pet instanceof Panda panda)
                    check(panda.getSitAmount(.5f) == 1, "Paused panda has a fully seated model");
            }
        } catch (Throwable error) {
            fail(error);
        }
    }
    private static boolean active() {
        return Boolean.getBoolean("industrialoverkill.courierSmoke") && !finished;
    }
    private static void check(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
    private static void checkBreakParticles(Minecraft mc) {
        var model = mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(TRAP));
        var sprite = model.getParticleIcon().contents().name();
        check(sprite.equals(AnimalGym.id("block/gym_panel")),
            "Courier break particles use the steel panel texture, got " + sprite);
    }
    private static ServerPlayer player() {
        return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers()
            .getFirst();
    }
    private static void server(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                fail(error);
            }
        });
    }
    private static void place(ServerPlayer player, BlockPos pos) {
        check(CommonHooks.onPlaceItemIntoWorld(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))).consumesAction(),
            "Item placement");
    }
    private static void occupiedHand(ServerPlayer player) {
        int found = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (CritterCourierProcessor.OCCUPANT.hasContent(player.getInventory().getItem(i))) {
                check(found < 0, "Duplicate occupied traps");
                found = i;
            }
        check(found >= 0, "Occupied trap returned to player");
        var stack = player.getInventory().removeItemNoUpdate(found);
        check(CritterCourierProcessor.OCCUPANT.read(stack).uuid().orElseThrow().equals(occupant),
            "Captured identity preserved");
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!active())
            return;
        var mc = Minecraft.getInstance();
        try {
            check(System.currentTimeMillis() < DEADLINE, "Timed out at stage " + stage);
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                mc.createWorldOpenFlows().createFreshLevel("trap-" + System.currentTimeMillis(),
                    new LevelSettings("Critter Courier", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    mc.screen);
            }
            if (mc.level == null || mc.player == null || placed)
                return;
            placed = true;
            server(() -> {
                var player = player();
                var level = player.serverLevel();
                level.getChunkAt(GYM);
                level.setDayTime(1000);
                for (int x = -8; x <= 12; x++)
                    for (int z = -8; z <= 8; z++)
                        level.setBlock(new BlockPos(x, 99, z),
                            Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                player.connection.teleport(5.5, 100, -3.3, 0, 10);
                player.setGameMode(GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(AnimalGym.ITEM.get()));
                place(player, GYM);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MachineContent.item(CritterCourierProcessor.ID)));
                place(player, TRAP);
                player.connection.teleport(5.5, 100, -3.3, 38, 12);
            });
        } catch (Throwable error) {
            fail(error);
        }
    }
    @SubscribeEvent
    public static void render(RenderFrameEvent.Post event) {
        if (!active())
            return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(GYM) instanceof AnimalGymEntity gym))
            return;
        var trap = CritterCourierProcessor.from(mc.level.getBlockEntity(TRAP));
        try {
            if (++frames < 30)
                return;
            if (stage == 0 && trap != null && !trap.occupied()) {
                mc.options.hideGui = true;
                if (frames < 40)
                    return;
                checkBreakParticles(mc);
                shot("critter-courier-armed.png");
                next(101);
                server(() -> player().connection.teleport(4.5, 101.5, -2.5, 34, 39));
            } else if (stage == 101 && trap != null && !trap.occupied()) {
                shot("critter-courier-armed-above.png");
                next(1);
                server(() -> {
                    var chicken = EntityType.CHICKEN.create(player().serverLevel());
                    chicken.setCustomName(Component.literal("Peep"));
                    chicken.setPos(2.5, 100.1875, .5);
                    occupant = chicken.getUUID();
                    player().serverLevel().addFreshEntity(chicken);
                });
            } else if (stage == 1 && trap != null && trap.occupied()) {
                checkBreakParticles(mc);
                shot("critter-courier-occupied-above.png");
                next(103);
                server(() -> player().connection.teleport(.2, 100, 3.5, -143, 15));
            } else if (stage == 103 && trap != null && trap.occupied()) {
                shot("critter-courier-back-sides.png");
                next(102);
                server(() -> player().connection.teleport(5.5, 100, -3.3, 38, 12));
            } else if (stage == 102 && trap != null && trap.occupied()) {
                shot("critter-courier-occupied.png");
                mc.options.hideGui = false;
                next(2);
                server(() -> check(
                    ScreenRuntime.open(player(), player().serverLevel().getBlockEntity(TRAP), TRAP),
                    "Open trap GUI"));
            } else if (stage == 2 && mc.screen instanceof MachineScreen screen) {
                check(CritterCourierProcessor.from(screen.getMenu().blockEntity) != null, "Trap GUI host");
                check(screen.getMenu().slots.isEmpty(),
                    "Compact trap screen has no player or machine slots");
                check(screen.getMenu().layout().drawers().isEmpty(),
                    "Compact trap screen has no drawers");
                var portrait = screen.getMenu().elements.stream()
                    .filter(e -> e.common().id().orElse("").equals("portrait")).findFirst()
                    .orElseThrow();
                check(portrait instanceof zone.moddev.mc.lib.gui.model.elements.EntityPortraitElement,
                    "Courier uses the standard MMDLib entity portrait");
                var portraitData = screen.getMenu().elementStates().get(portrait,
                    zone.moddev.mc.lib.gui.model.elements.EntityPortraitElement.ENTITY);
                check(portraitData.uuid().orElseThrow().equals(occupant),
                    "Portrait receives the stored occupant through element synchronization");
                check(courierPortraitRendered, "The standard portrait renders the captured living entity");
                var policies = zone.moddev.mc.lib.gui.client.EntityPreviewPolicies.forHost(
                    (zone.moddev.mc.lib.gui.runtime.ScreenHost) screen.getMenu().blockEntity);
                var source = EntityType.PANDA.create(mc.level);
                source.sit(true);
                var restored = zone.moddev.mc.lib.gui.client.EntityPreviewPolicies.create(
                    CritterCourierProcessor.ENTITIES.capture(source), mc.level,
                    policies.policy(((zone.moddev.mc.lib.gui.model.elements.EntityPortraitElement) portrait).variable()));
                check(restored instanceof Panda panda && panda.isSitting() && panda.sitAmount == 1,
                    "Standard portrait resolves the occupant's paired pose-restoration policy");
                var details = screen.getMenu().elements.stream()
                    .filter(e -> e.common().id().orElse("").equals("status")).findFirst()
                    .orElseThrow();
                var release = screen.getMenu().elements.stream()
                    .filter(e -> e.common().id().orElse("").equals("courier_release")).findFirst()
                    .orElseThrow();
                check(screen.bounds(portrait).right() <= screen.bounds(details).x()
                    && screen.bounds(details).right() <= screen.bounds(release).x(),
                    "Portrait, details and controls appear left to right");
                check(details instanceof zone.moddev.mc.lib.gui.model.elements.LabelElement,
                    "Courier details use the standard label");
                check(screen.getMenu().value(details, false) instanceof Component text && text.getString().contains("Health:"),
                    "Stored health lines reach the standard label through screen synchronization");
                shot("critter-courier-screen.png");
                click(screen, "courier_pickup");
                next(3);
            } else if (stage == 3 && trap == null) {
                courierItemRendered = false;
                next(31);
                server(() -> {
                    var player = player();
                    occupiedHand(player);
                    ((AnimalGymEntity) player.serverLevel().getBlockEntity(GYM)).food
                        .insertItem(0, new ItemStack(Items.WHEAT_SEEDS, 32), false);
                    player.connection.teleport(2.2, 100, -2.7, 38, 10);
                });
            } else if (stage == 31 && CritterCourierProcessor.OCCUPANT.hasContent(mc.player.getMainHandItem())) {
                check(courierItemRendered, "Held Courier renders its captured animal");
                check(mc.getItemRenderer().getModel(mc.player.getMainHandItem(), mc.level, mc.player, 0)
                    .isCustomRenderer(), "Occupied model override retains the item renderer");
                for (var entity : mc.level.entitiesForRendering())
                    check(!entity.getUUID().equals(occupant), "Item preview never joins the client world");
                shot("critter-courier-held.png");
                check(mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(GYM), Direction.NORTH, GYM, false))
                    .consumesAction(),
                    "Client right-click predicts gym insertion");
                next(4);
            } else if (stage == 4 && gym.captivePreview() != null && gym.getGeneration() > 0) {
                mc.options.hideGui = true;
                if (frames < 40)
                    return;
                check(gym.captivePreview().getUUID().equals(occupant),
                    "Contained preview identity");
                for (var entity : mc.level.entitiesForRendering())
                    check(!entity.getUUID().equals(occupant), "No loose duplicate on client");
                if (net.modcrafters.minecraft.industrialoverkill.client.GymBiteAnimation
                    .reach(gym.captivePreview(), 0) < .09f)
                    return;
                shot("critter-courier-gym.png");
                GymClientSmoke.assertLookingAtFood(gym, gym.captivePreview(), true);
                mc.options.hideGui = false;
                courierItemRendered = false;
                next(5);
                server(() -> check(
                    ScreenRuntime.open(player(), player().serverLevel().getBlockEntity(GYM), GYM),
                    "Open gym GUI"));
            } else if (stage == 5 && mc.screen instanceof MachineScreen screen) {
                check(courierItemRendered, "Inventory Courier renders its captured animal");
                var slot = screen.getMenu().nativeSlots().entries().stream().filter(
                    e -> e.element() != null && e.element().common().id().orElse("").equals("trap"))
                    .findFirst().orElseThrow();
                check(CritterCourierProcessor.OCCUPANT.hasContent(slot.slot().getItem()),
                    "Occupied trap is in real synchronized gym slot");
                shot("critter-courier-gym-screen.png");
                click(screen, "toggle");
                next(6);
            } else if (stage == 6 && !gym.training.enabled() && gym.getGeneration() == 0
                && mc.screen instanceof MachineScreen screen) {
                check(gym.captivePreview() != null, "Paused gym retains visible captive");
                var slot = screen.getMenu().nativeSlots().entries().stream().filter(
                    e -> e.element() != null && e.element().common().id().orElse("").equals("trap"))
                    .findFirst().orElseThrow().slot();
                mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
                next(7);
            } else if (stage == 7 && gym.captivePreview() == null) {
                mc.player.closeContainer();
                next(8);
                server(() -> {
                    var player = player();
                    occupiedHand(player);
                    place(player, TRAP);
                });
            } else if (stage == 8 && trap != null && trap.occupied()) {
                next(9);
                server(() -> check(
                    ScreenRuntime.open(player(), player().serverLevel().getBlockEntity(TRAP), TRAP),
                    "Reopen carried trap"));
            } else if (stage == 9 && mc.screen instanceof MachineScreen screen) {
                click(screen, "courier_release");
                next(10);
            } else if (stage == 10 && trap != null && !trap.occupied()) {
                next(11);
                server(() -> {
                    var entity = player().serverLevel().getEntity(occupant);
                    check(entity instanceof Animal, "Release restored the original animal");
                    var animal = (Animal) entity;
                    check(
                        animal.getName().getString().equals("Peep")
                            && !animal.getPersistentData().contains("industrial_overkill:fitness"),
                        "Identity survives the round trip without accumulating fitness");
                    Minecraft.getInstance().execute(() -> {
                        mc.player.closeContainer();
                        petIndex = 0;
                        next(19);
                        server(CritterCourierClientSmoke::installRestingAnimal);
                    });
                });
            } else if (stage == 19
                && gym.captivePreview() instanceof Animal
                && gym.getGeneration() > 0) {
                mc.options.hideGui = true;
                petRenderedStanding = false;
                petFirstStride = -1;
                next(20);
            } else if ((stage == 20 || stage == 22) && gym.getGeneration() > 0) {
                var pet = (Animal) gym.captivePreview();
                check(petRenderedStanding && pet.walkAnimation.position() > petFirstStride + 1,
                    "Sitting pet stands and advances its running stride");
                check(originalPose(pet),
                    "Render override is restored after each frame");
                if (stage == 20) {
                    shot("gym-" + petName() + "-running.png");
                    next(21);
                    server(() -> ((AnimalGymEntity) player().serverLevel().getBlockEntity(GYM))
                        .control("toggle"));
                } else {
                    next(23);
                    server(() -> {
                        var serverGym = (AnimalGymEntity) player().serverLevel()
                            .getBlockEntity(GYM);
                        serverGym.control("release");
                        var carried = serverGym.food.extractItem(3, 1, false);
                        var data = CritterCourierProcessor.OCCUPANT.read(carried).data();
                        check(data.getBoolean("Sleeping") == (petIndex == 2)
                            && data.getBoolean(petIndex == 4
                                ? "industrial_overkill:panda_sitting"
                                : "Sitting") == (petIndex != 2),
                            "Ejected item retains sleeping/sitting state");
                        player().serverLevel().removeBlock(TRAP, false);
                        player().serverLevel().setBlock(TRAP,
                            MachineContent.block(CritterCourierProcessor.ID).defaultBlockState(), 3);
                        var cage = CritterCourierProcessor.from(player().serverLevel().getBlockEntity(TRAP));
                        cage.occupant(CritterCourierProcessor.OCCUPANT.read(carried));
                        check(cage.release().accepted(), "Trained pet released");
                        var released = (Animal) player().serverLevel().getEntity(occupant);
                        check(released != null && originalPose(released),
                            "Release preserves resting state");
                        if (released instanceof TamableAnimal tame)
                            check(player().getUUID().equals(tame.getOwnerUUID()),
                                "Release preserves owner");
                        else if (released instanceof Fox fox)
                            check(CritterCourierProcessor.ENTITIES.capture(fox).data().getList("Trusted", 11)
                                .contains(net.minecraft.nbt.NbtUtils.createUUID(player().getUUID()))
                                && fox
                                    .getVariant() == (petIndex == 2 ? Fox.Type.SNOW : Fox.Type.RED),
                                "Release preserves fox trust and variant");
                        if (released instanceof Panda panda)
                            check(
                                panda.getMainGene() == Panda.Gene.BROWN
                                    && panda.getHiddenGene() == Panda.Gene.BROWN,
                                "Release preserves panda genes");
                        released.discard();
                        if (++petIndex < 5) {
                            installRestingAnimal();
                            mc.execute(() -> next(19));
                        } else {
                            finished = true;
                            MMDLib.LOGGER.info(
                                "CRITTER_COURIER_CLIENT_PASS: capture, carry, training, resting cat/dog/fox/panda running poses, pause/resume and restored sitting/sleeping state on release");
                            mc.execute(mc::stop);
                        }
                    });
                }
            } else if (stage == 21 && gym.getGeneration() == 0) {
                var pet = (Animal) gym.captivePreview();
                check(originalPose(pet) && pet.walkAnimation.speed() == 0,
                    "Paused pet sits without treadmill animation");
                shot("gym-" + petName() + "-paused.png");
                petRenderedStanding = false;
                petFirstStride = -1;
                next(22);
                server(() -> ((AnimalGymEntity) player().serverLevel().getBlockEntity(GYM))
                    .control("toggle"));
            }
        } catch (Throwable error) {
            fail(error);
        }
    }
    private static String petName() {
        return switch (petIndex) {
            case 0 -> "cat";
            case 1 -> "dog";
            case 2 -> "sleeping-fox";
            case 3 -> "sitting-fox";
            default -> "panda";
        };
    }
    private static boolean standing(Animal animal) {
        if (animal instanceof Panda panda)
            return !panda.isSitting() && panda.getSitAmount(.5f) == 0;
        return animal instanceof Fox fox
            ? !fox.isSleeping() && !fox.isSitting() && !fox.isCrouching()
            : animal instanceof TamableAnimal tame && !tame.isInSittingPose();
    }
    private static boolean originalPose(Animal animal) {
        if (animal instanceof Panda panda)
            return panda.isSitting();
        return animal instanceof Fox fox
            ? fox.isSleeping() == (petIndex == 2) && fox.isSitting() == (petIndex == 3)
            : animal instanceof TamableAnimal tame && tame.isInSittingPose()
                && tame.isOrderedToSit();
    }
    private static void installRestingAnimal() {
        var level = player().serverLevel();
        var gym = (AnimalGymEntity) level.getBlockEntity(GYM);
        level.removeBlock(TRAP, false);
        level.setBlock(TRAP, MachineContent.block(CritterCourierProcessor.ID).defaultBlockState(), 3);
        var cage = CritterCourierProcessor.from(level.getBlockEntity(TRAP));
        var pet = (Animal) (petIndex == 0
            ? EntityType.CAT
            : petIndex == 1 ? EntityType.WOLF : petIndex == 4 ? EntityType.PANDA : EntityType.FOX)
            .create(level);
        if (pet instanceof TamableAnimal tame) {
            tame.setTame(true, true);
            tame.setOwnerUUID(player().getUUID());
            tame.setOrderedToSit(true);
            tame.setInSittingPose(true);
        } else if (pet instanceof Panda panda) {
            panda.setMainGene(Panda.Gene.BROWN);
            panda.setHiddenGene(Panda.Gene.BROWN);
            panda.sit(true);
        } else {
            var data = CritterCourierProcessor.ENTITIES.capture(pet).data();
            data.putBoolean("Sleeping", petIndex == 2);
            data.putBoolean("Sitting", petIndex == 3);
            data.putString("Type", petIndex == 2 ? "snow" : "red");
            var trust = new net.minecraft.nbt.ListTag();
            trust.add(net.minecraft.nbt.NbtUtils.createUUID(player().getUUID()));
            data.put("Trusted", trust);
            pet.load(data);
        }
        pet.setPos(TRAP.getX() + .5, TRAP.getY() + .1875, TRAP.getZ() + .5);
        occupant = pet.getUUID();
        level.addFreshEntity(pet);
        check(cage.capture(pet), "Capture sitting pet");
        for (int slot = 0; slot < 4; slot++)
            gym.food.extractItem(slot, 64, false);
        gym.food.insertItem(3, cage.take(), false);
        level.removeBlock(TRAP, false);
        gym.food.insertItem(0, new ItemStack(switch (petIndex) {
            case 0 -> Items.COD;
            case 1 -> Items.BEEF;
            case 2 -> Items.SWEET_BERRIES;
            case 3 -> Items.GLOW_BERRIES;
            default -> Items.BAMBOO;
        }, 32), false);
        gym.energy.setStored(0);
        if (!gym.training.enabled())
            gym.control("toggle");
        player().connection.teleport(2.2, 100, -2.7, 38, 10);
    }
    private static void next(int value) {
        stage = value;
        frames = 0;
    }
    private static void click(MachineScreen screen, String id) {
        var element = screen.getMenu().elements.stream()
            .filter(e -> e.common().id().orElse("").equals(id)).findFirst().orElseThrow();
        var bounds = screen.bounds(element);
        check(screen.mouseClicked(bounds.x() + 3, bounds.y() + 3, 0), "GUI button " + id);
        screen.mouseReleased(bounds.x() + 3, bounds.y() + 3, 0);
    }
    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
        });
    }
    private static void fail(Throwable error) {
        finished = true;
        MMDLib.LOGGER.error("CRITTER_COURIER_CLIENT_FAIL stage=" + stage, error);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }
}

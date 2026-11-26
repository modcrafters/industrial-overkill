package net.modcrafters.minecraft.industrialoverkill.courier;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;

import zone.moddev.mc.lib.api.entity.EntityPlacement;
import zone.moddev.mc.lib.api.entity.EntityPreviewCache;
import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.api.entity.EntitySnapshotPolicy;
import zone.moddev.mc.lib.machine.feature.PortableStoredProcessor;
import zone.moddev.mc.lib.machine.feature.PortableVariable;
import zone.moddev.mc.lib.machine.feature.ProcessorAction;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.ProcessorTimer;
import zone.moddev.mc.lib.machine.feature.Processors;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** Capture, transport and release policy for the XML-defined Critter Courier. */
public final class CritterCourierProcessor extends PortableStoredProcessor {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
        IndustrialOverkill.MOD_ID, "critter_courier");
    public static final TagKey<EntityType<?>> CAPTURE_BLACKLIST = TagKey.create(
        Registries.ENTITY_TYPE, ID.withPath("critter_courier_blacklist"));
    public static final EntitySnapshotPolicy<Mob> ENTITIES = new EntitySnapshotPolicy<>(Mob.class,
        mob -> mob.isAlive() && !mob.isPassenger() && !mob.isVehicle(),
        (mob, data) -> {
            // Vanilla panda NBT keeps genes but omits the resting pose.
            if (mob instanceof Panda panda)
                data.putBoolean("industrial_overkill:panda_sitting", panda.isSitting());
            // The lead stays in the world after transport.
            data.remove("leash");
            data.remove("Leash");
        },
        (mob, data) -> {
            if (mob instanceof Panda panda && data.contains("industrial_overkill:panda_sitting")) {
                panda.sit(data.getBoolean("industrial_overkill:panda_sitting"));
                panda.sitAmount = panda.sitAmountO = panda.isSitting() ? 1 : 0;
            }
        });
    public static final PortableVariable<EntitySnapshot> OCCUPANT = PortableVariable.entity(
        () -> MachineContent.item(ID), "courier", "occupant", ENTITIES);
    private final EntityPreviewCache<Mob> previews = new EntityPreviewCache<>(ENTITIES, 1);
    private final ProcessorState.Key<EntitySnapshot> occupant = OCCUPANT.declare(variables).synced().define();
    private final ProcessorState.Key<Boolean> occupied = variables.bool("occupied", false).visual()
        .derived(List.of(occupant), state -> !state.get(occupant).isEmpty());
    private final ProcessorState.Key<List<Component>> details = variables.components("details", List.of()).synced()
        .derived(List.of(occupant), state -> describeOccupant(state.get(occupant)));
    private final ProcessorTimer cooldown = variables.timer("cooldown", 40, ProcessorTimer.Policy.IDLE);
    private final Map<ResourceLocation, ProcessorAction> actions = Map.of(
        ID.withPath("release"), new ProcessorAction((s, c) -> occupied() && !taken(),
            (s, c) -> release()));

    public static CritterCourierProcessor from(Object host) {
        return Processors.find(host, OCCUPANT.processor(), CritterCourierProcessor.class).orElse(null);
    }
    @Override
    protected Map<ResourceLocation, ProcessorAction> customActions() { return actions; }
    public boolean occupied() { return variables.get(occupied); }
    public EntitySnapshot occupant() { return variables.get(occupant); }
    public BlockPos getBlockPos() { return machine.position(); }

    private Mob preview(EntitySnapshot snapshot) {
        return previews.get(machine == null ? null : machine.level(), snapshot);
    }
    private List<Component> describeOccupant(EntitySnapshot snapshot) {
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.translatable(snapshot.isEmpty() ? "courier.armed" : "courier.occupied"));
        if (snapshot.isEmpty()) {
            lines.add(Component.translatable("courier.waiting"));
            return lines;
        }
        var entity = preview(snapshot);
        if (entity == null) {
            lines.add(snapshot.type().map(EntityType::getDescription)
                .orElse(Component.translatable("courier.unknown")));
            return lines;
        }
        lines.add(entity.getDisplayName());
        if (entity.hasCustomName()) lines.add(entity.getType().getDescription());
        lines.add(Component.translatable("courier.health",
            String.format(java.util.Locale.ROOT, "%.1f", entity.getHealth()),
            String.format(java.util.Locale.ROOT, "%.1f", entity.getMaxHealth())));
        if (entity instanceof net.minecraft.world.entity.AgeableMob ageable)
            lines.add(Component.translatable(ageable.isBaby() ? "courier.age.baby" : "courier.age.adult"));
        return lines;
    }
    public void occupant(EntitySnapshot snapshot) {
        variables.update(() -> variables.set(occupant, snapshot));
    }
    public boolean canStart(ProcessorState state, ProcessorMachine context) {
        return !occupied() && !taken() && cooldown.ready();
    }
    public ProcessState process(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        for (var candidate : context.level().getEntitiesOfClass(Mob.class, new AABB(context.position()).inflate(0, .5, 0)))
            if (capture(candidate)) break;
        return ProcessState.IDLE;
    }
    public boolean capture(Mob candidate) {
        if (!(machine.level() instanceof ServerLevel) || !canStart(variables, machine)) return false;
        var pos = machine.position();
        double x = candidate.getX() - pos.getX(), z = candidate.getZ() - pos.getZ(), y = candidate.getY() - pos.getY();
        if (x < 0 || x >= 1 || z < 0 || z >= 1 || y < .1 || y > .7) return false;
        var saved = snapshotForCapture(candidate);
        if (saved.isEmpty()) return false;
        variables.update(() -> {
            variables.set(occupant, saved);
            removeCaptured(candidate);
        });
        machine.level().playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1, .8f);
        return true;
    }

    public static EntitySnapshot snapshotForCapture(Mob candidate) {
        return candidate.getType().is(CAPTURE_BLACKLIST) ? EntitySnapshot.EMPTY : ENTITIES.capture(candidate);
    }

    public static void removeCaptured(Mob candidate) {
        if (candidate.isLeashed()) candidate.dropLeash(true, true);
        candidate.discard();
    }

    public static boolean isEmptyCourier(ItemStack stack) {
        return OCCUPANT.matches(stack) && OCCUPANT.read(stack).isEmpty();
    }
    public ProcessorAction.Result release() {
        if (!(machine.level() instanceof ServerLevel server) || !occupied() || taken()) return ProcessorAction.Result.unavailable();
        var entity = ENTITIES.createDetached(occupant(), server).orElse(null);
        var front = machine.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        var result = entity == null ? EntityPlacement.Result.INVALID_ENTITY
            : EntityPlacement.place(entity, server, EntityPlacement.around(entity, machine.position(),
                List.of(front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite())));
        if (result != EntityPlacement.Result.SUCCESS) {
            return ProcessorAction.Result.failure(Component.translatable(
                result == EntityPlacement.Result.NO_SPACE || result == EntityPlacement.Result.SPAWN_REJECTED
                    ? "courier.no_space" : "courier.release_failed"));
        }
        variables.update(() -> { cooldown.start(40); variables.set(occupant, EntitySnapshot.EMPTY); });
        server.playSound(null, machine.position(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1, 1);
        return ProcessorAction.Result.success();
    }
    public void tooltip(ItemStack stack, List<Component> lines) {
        var snapshot = OCCUPANT.read(stack);
        var species = snapshot.type().map(EntityType::getDescription)
            .orElse(Component.translatable("courier.unknown"));
        var name = snapshot.customName().<Component>map(custom ->
            Component.translatable("courier.named", custom, species)).orElse(species);
        lines.add(snapshot.isEmpty() ? Component.translatable("courier.armed")
            : Component.translatable("courier.contains", name));
    }
}

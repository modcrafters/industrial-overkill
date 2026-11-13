package net.modcrafters.minecraft.industrialoverkill.courier;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGymEntity;
import zone.moddev.mc.lib.api.entity.EntityPlacement;
import zone.moddev.mc.lib.api.entity.EntityPreviewCache;
import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.machine.feature.PortableStoredProcessor;
import zone.moddev.mc.lib.machine.feature.ProcessorAction;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.ProcessorTimer;
import zone.moddev.mc.lib.machine.feature.Processors;
import zone.moddev.mc.lib.machine.neoforge.MultiblockMembers;

/** Capture, transport and release policy for the XML-defined Critter Courier. */
public final class CritterCourierProcessor extends PortableStoredProcessor {
    private final EntityPreviewCache<Mob> previews = new EntityPreviewCache<>(1, CritterCourierData::load);
    private final ProcessorState.Key<EntitySnapshot> occupant = variables.entity("occupant")
        .synced().portable().define();
    private final ProcessorState.Key<Boolean> occupied = variables.bool("occupied", false).visual()
        .derived(List.of(occupant), state -> !state.get(occupant).isEmpty());
    private final ProcessorState.Key<List<Component>> details = variables.components("details", List.of()).synced()
        .derived(List.of(occupant), state -> describeOccupant(state.get(occupant)));
    private final ProcessorTimer cooldown = variables.timer("cooldown", 40, ProcessorTimer.Policy.IDLE);
    private final Map<ResourceLocation, ProcessorAction> actions = Map.of(
        CritterCourierData.ID.withPath("release"), new ProcessorAction((s, c) -> occupied() && !taken(),
            (s, c) -> release(c.player()) ? ProcessorAction.Result.success() : ProcessorAction.Result.unavailable()));

    public static CritterCourierProcessor from(Object host) {
        return Processors.find(host, "courier", CritterCourierProcessor.class).orElse(null);
    }
    @Override
    protected Map<ResourceLocation, ProcessorAction> customActions() { return actions; }
    public boolean occupied() { return variables.get(occupied); }
    public CompoundTag mobData() { return variables.get(occupant).data(); }
    public BlockPos getBlockPos() { return machine.position(); }

    public Mob previewMob() {
        return preview(variables.get(occupant));
    }
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
            lines.add(CritterCourierData.name(snapshot.data()));
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
    public void setMob(CompoundTag data) {
        variables.update(() -> variables.set(occupant, new EntitySnapshot(data)));
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
        if (!(machine.level() instanceof ServerLevel) || !canStart(variables, machine)
            || !CritterCourierData.capturable(candidate)) return false;
        var pos = machine.position();
        double x = candidate.getX() - pos.getX(), z = candidate.getZ() - pos.getZ(), y = candidate.getY() - pos.getY();
        if (x < 0 || x >= 1 || z < 0 || z >= 1 || y < .1 || y > .7) return false;
        var saved = CritterCourierData.save(candidate);
        if (saved.isEmpty()) return false;
        variables.update(() -> {
            setMob(saved);
            if (candidate.isLeashed()) candidate.dropLeash(true, true);
            candidate.discard();
        });
        machine.level().playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1, .8f);
        return true;
    }
    public boolean release(Player player) {
        if (!(machine.level() instanceof ServerLevel server) || !occupied() || taken()) return false;
        var entity = CritterCourierData.load(variables.get(occupant), server);
        var front = machine.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        var result = entity == null ? EntityPlacement.Result.INVALID_ENTITY
            : EntityPlacement.place(entity, server, EntityPlacement.around(entity, machine.position(),
                List.of(front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite())));
        if (result != EntityPlacement.Result.SUCCESS) {
            player.displayClientMessage(Component.translatable(
                result == EntityPlacement.Result.NO_SPACE || result == EntityPlacement.Result.SPAWN_REJECTED
                    ? "courier.no_space" : "courier.release_failed"), true);
            return false;
        }
        variables.update(() -> { cooldown.start(40); variables.set(occupant, EntitySnapshot.EMPTY); });
        server.playSound(null, machine.position(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1, 1);
        return true;
    }
    public InteractionResult useItem(UseOnContext context) {
        if (!CritterCourierData.occupied(context.getItemInHand())) return InteractionResult.PASS;
        if (context.getLevel().isClientSide() && context.getLevel().getBlockState(context.getClickedPos())
            .getBlock() instanceof net.modcrafters.minecraft.industrialoverkill.gym.GymBlock) return InteractionResult.SUCCESS;
        var structure = MultiblockMembers.resolve(context.getLevel(), context.getClickedPos());
        if (structure == null || !(structure.owner() instanceof AnimalGymEntity gym)) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!structure.ready() || !gym.food.getStackInSlot(3).isEmpty()) return InteractionResult.FAIL;
        gym.training.reset();
        if (!gym.food.insertItem(3, context.getItemInHand().copyWithCount(1), false).isEmpty()) return InteractionResult.FAIL;
        context.getItemInHand().shrink(1);
        gym.synchronize();
        return InteractionResult.CONSUME;
    }
    public void tooltip(ItemStack stack, List<Component> lines) {
        var data = CritterCourierData.read(stack);
        lines.add(data.isEmpty() ? Component.translatable("courier.armed")
            : Component.translatable("courier.contains", CritterCourierData.name(data)));
    }
}

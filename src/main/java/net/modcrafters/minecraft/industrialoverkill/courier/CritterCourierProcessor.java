package net.modcrafters.minecraft.industrialoverkill.courier;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
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
import net.minecraft.world.phys.Vec3;

import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGymEntity;
import zone.moddev.mc.lib.machine.feature.PortableProcessor;
import zone.moddev.mc.lib.machine.feature.ProcessorAction;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MultiblockMembers;
import zone.moddev.mc.lib.gui.model.EntityPortraitData;

/** Capture, transport and release policy for the XML-defined Critter Courier. */
public final class CritterCourierProcessor extends StoredProcessor implements PortableProcessor {
    private final ProcessorState.Key<EntityPortraitData> occupant = variables.define("occupant", EntityPortraitData.CODEC,
        EntityPortraitData.EMPTY, java.util.function.UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<Boolean> occupied = variables.define("occupied", Codec.BOOL, false);
    private final ProcessorState.Key<Boolean> taken = variables.define("taken", Codec.BOOL, false);
    private final ProcessorState.Key<List<Component>> details = variables.define("details",
        net.minecraft.network.chat.ComponentSerialization.CODEC.listOf(),
        List.<Component>of(Component.translatable("courier.armed"), Component.translatable("courier.waiting")),
        lines -> lines.stream().<Component>map(Component::copy).toList(), false, true);
    private final ProcessorState.Key<Integer> cooldown = variables.define("cooldown", Codec.intRange(0, 40),
        0, java.util.function.UnaryOperator.identity(), false, false);
    private CompoundTag previewData;
    private Mob preview;
    private final Map<ResourceLocation, ProcessorAction> actions = Map.of(
        AnimalGym.id("release"), new ProcessorAction((s, c) -> occupied() && !s.get(taken),
            (s, c) -> release(c.player()) ? ProcessorAction.Result.success() : ProcessorAction.Result.unavailable()),
        AnimalGym.id("pickup"), new ProcessorAction((s, c) -> !s.get(taken), (s, c) -> {
            pickUp(c.player());
            return ProcessorAction.Result.success();
        }));

    public static CritterCourierProcessor from(Object host) {
        return host instanceof DefinedMachineEntity machine
            ? machine.screenFeatures().findProcessor("courier")
                .filter(CritterCourierProcessor.class::isInstance).map(CritterCourierProcessor.class::cast).orElse(null)
            : null;
    }
    public Map<ResourceLocation, ProcessorAction> actions() { return actions; }
    public boolean occupied() { return variables.get(occupied); }
    public CompoundTag mobData() { return variables.get(occupant).data(); }
    public BlockPos getBlockPos() { return machine.position(); }

    public Mob previewMob() {
        var data = mobData();
        if (machine != null && machine.level() != null && !data.equals(previewData)) {
            previewData = data;
            preview = CritterCourierData.load(data, machine.level());
        }
        return preview;
    }
    private List<Component> describeOccupant() {
        var entity = previewMob();
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.translatable(occupied() ? "courier.occupied" : "courier.armed"));
        if (!occupied()) { lines.add(Component.translatable("courier.waiting")); return lines; }
        if (entity == null) { lines.add(CritterCourierData.name(mobData())); return lines; }
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
        variables.update(() -> {
            variables.set(occupant, new EntityPortraitData(data));
            variables.set(occupied, !data.isEmpty());
            variables.set(details, describeOccupant());
        });
    }
    public boolean canStart(ProcessorState state, ProcessorMachine context) {
        return !occupied() && !state.get(taken) && state.get(cooldown) == 0;
    }
    protected void idle(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        if (state.get(cooldown) > 0) state.set(cooldown, Math.max(0, state.get(cooldown) - deltaTicks));
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
    public ItemStack take() {
        if (variables.get(taken)) return ItemStack.EMPTY;
        var stack = CritterCourierData.stack(mobData());
        variables.update(() -> {
            variables.set(taken, true);
            setMob(new CompoundTag());
        });
        return stack;
    }
    public void pickUp(Player player) {
        if (machine.level().isClientSide() || variables.get(taken)) return;
        var stack = take();
        machine.remove();
        player.getInventory().placeItemBackInInventory(stack);
        player.closeContainer();
    }
    public boolean release(Player player) {
        if (!(machine.level() instanceof ServerLevel server) || !occupied() || variables.get(taken)) return false;
        var entity = CritterCourierData.load(mobData(), server);
        if (entity == null || !CritterCourierData.uuidAvailable(entity, server)) {
            player.displayClientMessage(Component.translatable("courier.release_failed"), true);
            return false;
        }
        var pos = machine.position();
        var front = machine.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        double distance = Math.max(1, .55 + entity.getBbWidth() / 2.0);
        for (var side : List.of(front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite())) {
            entity.moveTo(pos.getX() + .5 + side.getStepX() * distance, pos.getY(),
                pos.getZ() + .5 + side.getStepZ() * distance, side.toYRot(), 0);
            entity.setDeltaMovement(Vec3.ZERO);
            var box = entity.getBoundingBox();
            if (!server.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ)) || !server.noCollision(entity)
                || !server.getWorldBorder().isWithinBounds(box)) continue;
            if (!server.addFreshEntity(entity)) break;
            variables.update(() -> { variables.set(cooldown, 40); setMob(new CompoundTag()); });
            server.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1, 1);
            return true;
        }
        player.displayClientMessage(Component.translatable("courier.no_space"), true);
        return false;
    }
    public void placed(ItemStack stack) { setMob(CritterCourierData.read(stack)); }
    public boolean hasContent(ItemStack stack) { return CritterCourierData.occupied(stack); }
    public void creativeDestroy() { if (!occupied()) take(); }
    public InteractionResult use(Player player, boolean secondary) {
        if (!secondary) return InteractionResult.PASS;
        if (!machine.level().isClientSide()) invoke(AnimalGym.id("pickup"), player);
        return InteractionResult.sidedSuccess(machine.level().isClientSide());
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

package zone.moddev.mc.industrialoverkill.trap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import zone.moddev.mc.lib.machine.neoforge.BaseMachine;

public final class MobTrapEntity extends BaseMachine {
    private final zone.moddev.mc.lib.api.capabilities.ItemStorage items = new zone.moddev.mc.lib.api.capabilities.ItemStorage(
        0);
    @Override
    public zone.moddev.mc.lib.api.capabilities.ItemStorage screenItems() {
        return items;
    }
    @Override
    public boolean screenAutomationAddons() {
        return false;
    }
    private CompoundTag mob = new CompoundTag();
    private Mob preview;
    private boolean previewResolved;
    private boolean taken;
    private int cooldown;
    public MobTrapEntity(BlockPos pos, BlockState state) {
        super(MobTrap.TYPE.get(), pos, state);
    }
    public boolean occupied() {
        return !mob.isEmpty();
    }
    public CompoundTag mobData() {
        return mob.copy();
    }
    public Mob previewMob() {
        if (!previewResolved && level != null) {
            previewResolved = true;
            preview = MobTrapData.load(mob, level);
        }
        return preview;
    }
    public Component getStatusText() {
        var entity = previewMob();
        var text = Component.translatable(occupied() ? "trap.occupied" : "trap.armed").append("\n");
        if (!occupied())
            return text.append(Component.translatable("trap.waiting"));
        if (entity == null)
            return text.append(MobTrapData.name(mob));
        text.append(entity.getDisplayName());
        if (entity.hasCustomName())
            text.append("\n").append(entity.getType().getDescription());
        text.append("\n").append(Component.translatable("trap.health",
            String.format(java.util.Locale.ROOT, "%.1f", entity.getHealth()),
            String.format(java.util.Locale.ROOT, "%.1f", entity.getMaxHealth())));
        if (entity instanceof net.minecraft.world.entity.AgeableMob ageable)
            text.append("\n").append(
                Component.translatable(ageable.isBaby() ? "trap.age.baby" : "trap.age.adult"));
        return text;
    }
    public int getAnimalId() {
        return -1;
    }
    public void setMob(CompoundTag data) {
        mob = data.copy();
        preview = null;
        previewResolved = false;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.setBlock(worldPosition,
                getBlockState().setValue(MobTrapBlock.OCCUPIED, occupied()), 3);
            syncToClient();
        }
    }
    public boolean capture(Mob candidate) {
        if (!(level instanceof ServerLevel) || taken || occupied() || cooldown > 0
            || !MobTrapData.capturable(candidate))
            return false;
        double x = candidate.getX() - worldPosition.getX(),
            z = candidate.getZ() - worldPosition.getZ();
        double y = candidate.getY() - worldPosition.getY();
        if (x < 0 || x >= 1 || z < 0 || z >= 1 || y < .1 || y > .7)
            return false;
        var saved = MobTrapData.save(candidate);
        if (saved.isEmpty())
            return false;
        setMob(saved); // Establish ownership before removing the world entity.
        if (candidate.isLeashed())
            candidate.dropLeash(true, true);
        candidate.discard();
        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1,
            .8f);
        return true;
    }
    @Override
    protected void beforeMachineTick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!occupied() && !taken)
            for (var candidate : level.getEntitiesOfClass(Mob.class,
                new AABB(worldPosition).inflate(0, .5, 0)))
                if (capture(candidate))
                    break;
    }
    public ItemStack take() {
        if (taken)
            return ItemStack.EMPTY;
        var result = MobTrapData.stack(mob);
        taken = true;
        mob = new CompoundTag();
        preview = null;
        previewResolved = false;
        setChanged();
        return result;
    }
    public void pickUp(Player player) {
        if (level == null || level.isClientSide || taken)
            return;
        var stack = take();
        level.removeBlock(worldPosition, false);
        player.getInventory().placeItemBackInInventory(stack);
        player.closeContainer();
    }
    public boolean release(Player player) {
        if (!(level instanceof ServerLevel server) || !occupied() || taken)
            return false;
        var entity = MobTrapData.load(mob, server);
        if (entity == null || !MobTrapData.uuidAvailable(entity, server)) {
            player.displayClientMessage(Component.translatable("trap.release_failed"), true);
            return false;
        }
        var front = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Wide animals need enough clearance to avoid overlapping the cage itself.
        double distance = Math.max(1, .55 + entity.getBbWidth() / 2.0);
        for (var side : new Direction[]{front, front.getClockWise(), front.getCounterClockWise(),
            front.getOpposite()}) {
            entity.moveTo(worldPosition.getX() + .5 + side.getStepX() * distance,
                worldPosition.getY(), worldPosition.getZ() + .5 + side.getStepZ() * distance,
                side.toYRot(), 0);
            entity.setDeltaMovement(Vec3.ZERO);
            var box = entity.getBoundingBox();
            if (!server.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                || !server.noCollision(entity) || !server.getWorldBorder().isWithinBounds(box))
                continue;
            if (!server.addFreshEntity(entity))
                break;
            cooldown = 40;
            setMob(new CompoundTag());
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS,
                1, 1);
            return true;
        }
        player.displayClientMessage(Component.translatable("trap.no_space"), true);
        return false;
    }
    @Override
    protected void recoverResources(net.minecraft.world.level.Level world, BlockPos pos,
        CompoundTag payload) {
        var stack = new ItemStack(zone.moddev.mc.industrialoverkill.gym.AnimalGym.RECOVERY.get());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(payload.copy()));
        net.minecraft.world.Containers.dropItemStack(world, pos.getX() + .5, pos.getY() + .5,
            pos.getZ() + .5, stack);
    }
    @Override
    protected void saveMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("mob", mob.copy());
        tag.putInt("cooldown", cooldown);
    }
    @Override
    protected void loadMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        mob = tag.getCompound("mob").copy();
        preview = null;
        previewResolved = false;
        cooldown = tag.getInt("cooldown");
        taken = false;
    }
    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("mob", mob.copy());
    }
    @Override
    protected void loadClientData(CompoundTag tag, HolderLookup.Provider registries) {
        mob = tag.getCompound("mob").copy();
        preview = null;
        previewResolved = false;
    }
}

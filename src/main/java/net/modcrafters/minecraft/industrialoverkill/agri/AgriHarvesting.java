package net.modcrafters.minecraft.industrialoverkill.agri;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.modcrafters.minecraft.industrialoverkill.api.agri.AgriHarvestHandler;
import net.modcrafters.minecraft.industrialoverkill.api.agri.AgriHarvestHandler.Harvest;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Plans all loot destinations before removing a mature crop. */
final class AgriHarvesting {
    private static final TagKey<Block> COLUMNS = TagKey.create(Registries.BLOCK,
        ResourceLocation.parse("industrial_overkill:agri_brain_column_crops"));

    private AgriHarvesting() {}

    @Nullable
    static Harvest inspect(ServerLevel level, BlockPos base) {
        var state = level.getBlockState(base);
        if (state.isAir()) return null;
        var handler = level.getCapability(AgriHarvestHandler.CAPABILITY, base, null);
        if (handler != null) {
            var harvest = handler.inspect(level, base, state);
            if (harvest == null) return null;
            var target = harvest.target();
            if (target.getX() != base.getX() || target.getZ() != base.getZ() || target.getY() < base.getY()
                || level.isOutsideBuildHeight(target) || level.getBlockState(target).isAir()
                || level.getBlockState(target).equals(harvest.replacement())) return null;
            return harvest;
        }
        if (state.is(COLUMNS)) {
            // Take the top first so cutting a support cannot spill unaccounted drops into the world.
            var top = base;
            while (top.getY() + 1 < level.getMaxBuildHeight() && level.getBlockState(top.above()).is(state.getBlock()))
                top = top.above();
            return top.equals(base) ? null : Harvest.cut(top);
        }
        return mature(state) ? Harvest.cut(base) : null;
    }

    private static boolean mature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        if (state.is(Blocks.PITCHER_CROP))
            return state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER
                && state.getValue(PitcherCropBlock.AGE) == PitcherCropBlock.MAX_AGE;
        return state.is(Blocks.TORCHFLOWER) || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN);
    }

    static boolean harvest(DefinedMachineEntity machine, BlockPos base) {
        if (!(machine.getLevel() instanceof ServerLevel level) || machine.isRemoved()
            || !level.hasChunkAt(base) || level.isOutsideBuildHeight(base)
            || !level.getWorldBorder().isWithinBounds(base)
            || level.captureBlockSnapshots || level.restoringBlockSnapshots) return false;
        var harvest = inspect(level, base);
        if (harvest == null) return false;
        var pos = harvest.target();
        var state = level.getBlockState(pos);
        var player = FakePlayerFactory.get(level, AgriTilling.PROFILE);
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return false;
        var oldPosition = player.position();
        var oldHand = player.getMainHandItem();
        try {
            player.setPos(Vec3.atCenterOf(machine.getBlockPos()));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled()
                || !level.getBlockState(pos).equals(state) || machine.isRemoved()) return false;
            var current = inspect(level, base);
            if (current == null || !current.target().equals(pos) || !current.replacement().equals(harvest.replacement())) return false;

            // Roll loot exactly once for this attempt, then simulate the entire batch.
            var drops = current.drops().roll(level, pos, state, player);
            var seeds = new Buffer(machine.items.inventory("seeds"));
            var output = new Buffer(machine.items.inventory("output"));
            for (var drop : drops) {
                var remaining = drop.copy();
                if (current.clearsBase(base) && AgriPlanting.accepts(remaining)) seeds.insert(remaining, true);
                output.insert(remaining, false);
                if (!remaining.isEmpty()) return false;
            }
            if (!level.setBlock(pos, current.replacement(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS))
                return false;
            seeds.commit();
            output.commit();
            if (current.replacement().isAir()) {
                level.levelEvent(2001, pos, Block.getId(state));
                level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
            } else {
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, current.replacement()));
            }
            return true;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
            player.setPos(oldPosition);
        }
    }

    /** Detached contents make capacity checks cumulative across every drop. */
    private static final class Buffer {
        private final ItemStorage storage;
        private final List<ItemStack> contents = new ArrayList<>();

        Buffer(ItemStorage storage) {
            this.storage = storage;
            for (int slot = 0; slot < storage.getSlots(); slot++) contents.add(storage.getStackInSlot(slot).copy());
        }

        void insert(ItemStack remaining, boolean input) {
            // Merge first: returning a seed should preserve existing slot assignments.
            for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                for (int slot = 0; slot < contents.size() && !remaining.isEmpty(); slot++) {
                    var held = contents.get(slot);
                    if (held.isEmpty() != (pass == 1) || input && !storage.isItemValid(slot, remaining)) continue;
                    if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, remaining)) continue;
                    int room = Math.min(storage.getSlotLimit(slot), remaining.getMaxStackSize()) - held.getCount();
                    int moved = Math.min(Math.max(0, room), remaining.getCount());
                    if (moved == 0) continue;
                    if (held.isEmpty()) contents.set(slot, remaining.copyWithCount(moved));
                    else held.grow(moved);
                    remaining.shrink(moved);
                }
            }
        }

        void commit() {
            for (int slot = 0; slot < contents.size(); slot++) {
                var next = contents.get(slot);
                if (!ItemStack.matches(storage.getStackInSlot(slot), next)) storage.setStackInSlot(slot, next);
            }
        }
    }
}

package net.modcrafters.minecraft.industrialoverkill.agri;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Plans all loot destinations before removing a mature crop. */
final class AgriHarvesting {
    private AgriHarvesting() {}

    static boolean mature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        if (state.is(Blocks.PITCHER_CROP))
            return state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER
                && state.getValue(PitcherCropBlock.AGE) == PitcherCropBlock.MAX_AGE;
        return state.is(Blocks.TORCHFLOWER) || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN);
    }

    static boolean harvest(DefinedMachineEntity machine, BlockPos pos) {
        if (!(machine.getLevel() instanceof ServerLevel level) || machine.isRemoved()
            || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
            || !level.getWorldBorder().isWithinBounds(pos)
            || level.captureBlockSnapshots || level.restoringBlockSnapshots) return false;
        var state = level.getBlockState(pos);
        if (!mature(state)) return false;
        var player = FakePlayerFactory.get(level, AgriTilling.PROFILE);
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return false;
        var oldPosition = player.position();
        var oldHand = player.getMainHandItem();
        try {
            player.setPos(Vec3.atCenterOf(machine.getBlockPos()));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled()
                || !level.getBlockState(pos).equals(state) || machine.isRemoved()) return false;

            // Roll loot exactly once for this attempt, then simulate the entire batch.
            var drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, ItemStack.EMPTY);
            var seeds = new Buffer(machine.items.inventory("seeds"));
            var output = new Buffer(machine.items.inventory("output"));
            for (var drop : drops) {
                var remaining = drop.copy();
                if (AgriPlanting.accepts(remaining)) seeds.insert(remaining, true);
                output.insert(remaining, false);
                if (!remaining.isEmpty()) return false;
            }
            if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS))
                return false;
            seeds.commit();
            output.commit();
            level.levelEvent(2001, pos, Block.getId(state));
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
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

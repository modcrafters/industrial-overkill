package net.modcrafters.minecraft.industrialoverkill.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.modcrafters.minecraft.industrialoverkill.mixin.PitcherCropGrowth;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmSupplies;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

final class AgriFertilizing {
    static final int OFF = 0;
    static final int SMART = 1;
    static final int FINISH_ONLY = 2;
    static final int PELLETS_ONLY = 3;

    enum Dose {
        REGULAR("fertilizer", 1), FILTERED("filtered_fertilizer", 2), PELLET("pellets", 2);

        final String slot;
        final int stages;

        Dose(String slot, int stages) {
            this.slot = slot;
            this.stages = stages;
        }

        boolean stocked(DefinedMachineEntity machine) {
            var stack = machine.items.inventory(slot).getStackInSlot(0);
            return switch (this) {
                case REGULAR -> stack.is(FarmSupplies.FERTILIZER.get());
                case FILTERED -> stack.is(FarmSupplies.FILTERED_FERTILIZER.get());
                case PELLET -> stack.is(FarmSupplies.SLOW_RELEASE_PELLET.get());
            };
        }
    }

    private AgriFertilizing() {}

    static Dose choose(DefinedMachineEntity machine, ServerLevel level, BlockPos pos, BlockState crop, int strategy) {
        if (strategy == OFF || strategy == PELLETS_ONLY || AgriPellets.active(level, pos)) return null;
        int remaining = remaining(crop);
        if (remaining <= 0) return null;
        if (strategy == FINISH_ONLY) {
            if (crop.getBlock() instanceof StemBlock || remaining > 2) return null;
            var dose = remaining == 2 ? Dose.FILTERED : Dose.REGULAR;
            return dose.stocked(machine) ? dose : null;
        }
        if (remaining == 2 && Dose.FILTERED.stocked(machine)) return Dose.FILTERED;
        if (Dose.REGULAR.stocked(machine)) return Dose.REGULAR;
        return remaining > 1 && Dose.FILTERED.stocked(machine) ? Dose.FILTERED : null;
    }

    static boolean use(DefinedMachineEntity machine, ServerLevel level, BlockPos pos, Dose dose) {
        if (!dose.stocked(machine) || (dose == Dose.PELLET && AgriPellets.active(level, pos))) return false;
        var contents = machine.items.inventory(dose.slot);
        var stack = contents.getStackInSlot(0).copy();
        if (!advance(level, pos, dose.stages)) return false;
        stack.shrink(1);
        contents.setStackInSlot(0, stack);
        if (dose == Dose.PELLET && remaining(level.getBlockState(pos)) > 0) AgriPellets.start(level, pos);
        level.levelEvent(1505, pos, 8);
        return true;
    }

    public static int remaining(BlockState state) {
        return switch (state.getBlock()) {
            case CropBlock crop -> crop.getMaxAge() - crop.getAge(state);
            case PitcherCropBlock ignored when state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER ->
                PitcherCropBlock.MAX_AGE - state.getValue(PitcherCropBlock.AGE);
            case StemBlock ignored -> StemBlock.MAX_AGE - state.getValue(StemBlock.AGE);
            default -> -1;
        };
    }

    static boolean canFinish(BlockState crop, Dose dose) {
        return dose != null && !(crop.getBlock() instanceof StemBlock) && remaining(crop) <= dose.stages;
    }

    static boolean advance(ServerLevel level, BlockPos pos, int stages) {
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
            || level.captureBlockSnapshots || level.restoringBlockSnapshots) return false;
        var state = level.getBlockState(pos);
        int remaining = remaining(state);
        if (remaining <= 0) return false;
        var player = FakePlayerFactory.get(level, AgriTilling.PROFILE);
        if (!player.mayBuild() || !level.mayInteract(player, pos)
            || !CommonHooks.canCropGrow(level, pos, state, true) || level.getBlockState(pos) != state) return false;
        int growth = Math.min(stages, remaining);
        boolean changed = switch (state.getBlock()) {
            case CropBlock crop -> level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + growth), Block.UPDATE_CLIENTS);
            case StemBlock ignored -> level.setBlock(pos,
                state.setValue(StemBlock.AGE, state.getValue(StemBlock.AGE) + growth), Block.UPDATE_CLIENTS);
            case PitcherCropBlock pitcher -> {
                ((PitcherCropGrowth) pitcher).industrialOverkill$grow(level, state, pos, growth);
                yield level.getBlockState(pos) != state;
            }
            default -> false;
        };
        if (changed) CommonHooks.fireCropGrowPost(level, pos, state);
        return changed;
    }
}

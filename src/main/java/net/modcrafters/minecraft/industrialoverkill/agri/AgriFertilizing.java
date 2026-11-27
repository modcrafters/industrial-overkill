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
import net.modcrafters.minecraft.industrialoverkill.power.AshItem;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

final class AgriFertilizing {
    static final int OFF = 0;
    static final int SMART = 1;
    static final int FINISH_ONLY = 2;
    static final int PELLETS_ONLY = 3;

    enum Dose {
        REGULAR(0, 1),
        FILTERED(1, 2),
        PELLET(2, 2),
        ALTERNATIVE_TOP(3, 1),
        ALTERNATIVE_BOTTOM(4, 1);

        final int slot;
        final int stages;

        Dose(int slot, int stages) {
            this.slot = slot;
            this.stages = stages;
        }

        boolean stocked(DefinedMachineEntity machine) {
            var contents = machine.items.inventory("fertilizer");
            var stack = contents.getStackInSlot(slot);
            return !stack.isEmpty() && contents.isItemValid(slot, stack);
        }
    }

    private AgriFertilizing() {}

    private static Dose regularDose(DefinedMachineEntity machine) {
        if (Dose.REGULAR.stocked(machine)) return Dose.REGULAR;
        if (Dose.ALTERNATIVE_TOP.stocked(machine)) return Dose.ALTERNATIVE_TOP;
        return Dose.ALTERNATIVE_BOTTOM.stocked(machine) ? Dose.ALTERNATIVE_BOTTOM : null;
    }

    static Dose choose(DefinedMachineEntity machine, ServerLevel level, BlockPos pos, BlockState crop, int strategy) {
        if (strategy == OFF || strategy == PELLETS_ONLY || AgriPellets.active(level, pos)) return null;
        int remaining = remaining(crop);
        if (remaining <= 0) return null;
        if (strategy == FINISH_ONLY) {
            if (crop.getBlock() instanceof StemBlock || remaining > 2) return null;
            if (remaining == 1) return regularDose(machine);
            return Dose.FILTERED.stocked(machine) ? Dose.FILTERED : null;
        }
        if (remaining == 2 && Dose.FILTERED.stocked(machine)) return Dose.FILTERED;
        var regular = regularDose(machine);
        if (regular != null) return regular;
        return remaining > 1 && Dose.FILTERED.stocked(machine) ? Dose.FILTERED : null;
    }

    static boolean use(DefinedMachineEntity machine, ServerLevel level, BlockPos pos, Dose dose) {
        if (!dose.stocked(machine) || (dose == Dose.PELLET && AgriPellets.active(level, pos))) return false;
        var contents = machine.items.inventory("fertilizer");
        var stack = contents.getStackInSlot(dose.slot).copy();
        if (!advance(level, pos, dose.stages, stack.getItem() instanceof AshItem, 8)) return false;
        stack.shrink(1);
        contents.setStackInSlot(dose.slot, stack);
        if (dose == Dose.PELLET && remaining(level.getBlockState(pos)) > 0) AgriPellets.start(level, pos);
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

    static boolean advance(ServerLevel level, BlockPos pos, int stages, boolean usingAsh, int particles) {
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
            || level.captureBlockSnapshots || level.restoringBlockSnapshots) return false;
        var state = level.getBlockState(pos);
        int remaining = remaining(state);
        if (remaining <= 0) return false;
        var player = FakePlayerFactory.get(level, AgriTilling.PROFILE);
        if (!player.mayBuild() || !level.mayInteract(player, pos)
            || !CommonHooks.canCropGrow(level, pos, state, true) || level.getBlockState(pos) != state) return false;
        // An eligible ash attempt spends the dose and FE even when the crop stays at its current age.
        if (usingAsh && !AshItem.takesEffect(level.random)) return true;
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
        if (changed) {
            CommonHooks.fireCropGrowPost(level, pos, state);
            level.levelEvent(1505, pos, particles);
        }
        return changed;
    }
}

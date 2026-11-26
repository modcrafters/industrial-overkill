package net.modcrafters.minecraft.industrialoverkill.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriDockBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineBlock;

@Mixin(value = DefinedMachineBlock.class, remap = false)
abstract class MachineRoofPlacementMixin extends Block {
    private MachineRoofPlacementMixin(Properties properties) { super(properties); }

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, require = 1)
    private void industrialOverkill$requireDockSpace(BlockPlaceContext context, CallbackInfoReturnable<BlockState> result) {
        var state = result.getReturnValue();
        if (state != null && AgriDockBlock.hasRoofExtension(state)
            && !AgriDockBlock.hasRoom(context.getLevel(), context.getClickedPos(), state))
            result.setReturnValue(null);
    }

    @Inject(method = "onPlace", at = @At("TAIL"), require = 1)
    private void industrialOverkill$placeDock(BlockState state, Level level, BlockPos pos, BlockState old,
        boolean moved, CallbackInfo callback) {
        if (AgriDockBlock.hasRoofExtension(state)) AgriDockBlock.install(level, pos, state);
    }

    // MMDLib schedules a visual update on load, including for controllers saved before the dock existed.
    @Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void industrialOverkill$restoreDock(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
        CallbackInfo callback) {
        if (AgriDockBlock.hasRoofExtension(state)) AgriDockBlock.install(level, pos, state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
        BlockPos pos, BlockPos neighborPos) {
        if (side == Direction.UP && neighbor.isAir() && !level.isClientSide() && AgriDockBlock.hasRoofExtension(state))
            level.scheduleTick(pos, this, 1);
        return super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }
}

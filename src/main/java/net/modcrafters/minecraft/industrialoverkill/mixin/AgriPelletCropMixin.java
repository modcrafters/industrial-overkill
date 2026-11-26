package net.modcrafters.minecraft.industrialoverkill.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriPellets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class AgriPelletCropMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void industrialOverkill$cancelPelletOnCropChange(BlockPos pos, BlockState next, boolean moving,
        CallbackInfoReturnable<BlockState> callback) {
        var previous = callback.getReturnValue();
        if (previous != null && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level
            && !level.captureBlockSnapshots && !level.restoringBlockSnapshots)
            AgriPellets.cropChanged(level, pos, previous, next);
    }
}

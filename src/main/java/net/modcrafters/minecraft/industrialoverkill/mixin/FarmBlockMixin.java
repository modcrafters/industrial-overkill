package net.modcrafters.minecraft.industrialoverkill.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriSoilRetention;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmBlock.class)
abstract class FarmBlockMixin {
    // Both sites follow vanilla's water and rain checks. The second handles bare soil at level zero.
    @Inject(method = "randomTick", at = {
        @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", ordinal = 0),
        @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/FarmBlock;shouldMaintainFarmland(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z")
    }, cancellable = true, require = 2)
    private void industrialOverkill$delayDrying(BlockState state, ServerLevel level, BlockPos pos,
        RandomSource random, CallbackInfo callback) {
        if (AgriSoilRetention.delaysDrying(level, pos)) callback.cancel();
    }
}

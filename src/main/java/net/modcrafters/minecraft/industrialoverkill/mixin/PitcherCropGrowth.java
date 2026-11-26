package net.modcrafters.minecraft.industrialoverkill.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PitcherCropBlock.class)
public interface PitcherCropGrowth {
    @Invoker("grow")
    void industrialOverkill$grow(ServerLevel level, BlockState state, BlockPos pos, int stages);
}

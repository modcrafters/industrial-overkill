package net.modcrafters.minecraft.industrialoverkill.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

public final class CritterCourierRenderer implements BlockEntityRenderer<DefinedMachineEntity> {
    public CritterCourierRenderer(BlockEntityRendererProvider.Context context) {
    }
    @Override
    public void render(DefinedMachineEntity trap, float partialTick, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay) {
        var courier = CritterCourierProcessor.from(trap);
        var mob = courier == null ? null : courier.previewMob();
        if (mob == null)
            return;
        mob.setPos(trap.getBlockPos().getX() + .5, trap.getBlockPos().getY() + .2,
            trap.getBlockPos().getZ() + .5);
        pose.pushPose();
        pose.translate(.5, .2, .5);
        CagedMobRenderer.render(mob, pose, buffers, light, partialTick, .62f, .45f,
            trap.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot(), false,
            1);
        pose.popPose();
    }
}

package zone.moddev.mc.industrialoverkill.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import zone.moddev.mc.industrialoverkill.trap.MobTrapEntity;

public final class MobTrapRenderer implements BlockEntityRenderer<MobTrapEntity> {
    public MobTrapRenderer(BlockEntityRendererProvider.Context context) {
    }
    @Override
    public void render(MobTrapEntity trap, float partialTick, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay) {
        var mob = trap.previewMob();
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

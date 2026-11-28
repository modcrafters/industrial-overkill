package net.modcrafters.minecraft.industrialoverkill.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.power.GeneratorEntity;
import net.modcrafters.minecraft.industrialoverkill.power.OmnivorousGenerator;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT)
public final class GeneratorRenderer implements BlockEntityRenderer<GeneratorEntity> {
    private final ItemRenderer items;

    private GeneratorRenderer(BlockEntityRendererProvider.Context context) {
        items = context.getItemRenderer();
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(OmnivorousGenerator.TYPE.get(), GeneratorRenderer::new);
    }

    @Override
    public void render(GeneratorEntity generator, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        var fuel = generator.displayedFuel();
        var state = generator.getBlockState();
        var level = generator.getLevel();
        if (fuel.isEmpty() || level == null || !state.getValue(BlockStateProperties.LIT)) return;
        var facing = generator.facing();
        var pos = generator.getBlockPos();
        if (!Block.shouldRenderFace(state, level, pos, facing, pos.relative(facing))) return;

        pose.pushPose();
        pose.translate(0.5, 7.5 / 16, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // In model pixels the fire is 0.015 in front of the casing and the open grate is 0.28.
        pose.translate(0, 0, 0.5 + 0.14 / 16);
        pose.scale(0.30F, 0.30F, 0.004F);
        items.renderStatic(fuel, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, overlay, pose, buffers, level, 0);
        pose.popPose();
    }
}

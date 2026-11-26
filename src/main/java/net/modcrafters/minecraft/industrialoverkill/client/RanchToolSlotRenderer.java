package net.modcrafters.minecraft.industrialoverkill.client;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.ranch.RanchToolSlot;
import zone.moddev.mc.lib.gui.client.ElementRenderers;

/** Draws the original item models as grayscale slot hints, including resource-pack overrides. */
@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RanchToolSlotRenderer {
    private static final Map<RenderType, RenderType> ENABLED_HINTS = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> DISABLED_HINTS = new IdentityHashMap<>();
    private static ShaderInstance grayscale;

    private RanchToolSlotRenderer() {}

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
            ResourceLocation.fromNamespaceAndPath(IndustrialOverkill.MOD_ID, "ranch_tool_hint"),
            DefaultVertexFormat.NEW_ENTITY), shader -> grayscale = shader);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ElementRenderers.GLOBAL.registerOverlay(RanchToolSlot.class, RanchToolSlotRenderer::drawHint);
            ElementRenderers.GLOBAL.registerForeground(RanchToolSlot.class, RanchToolSlotRenderer::shadeContents);
        });
    }

    private static void drawHint(RanchToolSlot slot, ElementRenderers.Context context) {
        var access = context.access();
        boolean enabled = slot.enabled(access.state());
        var graphics = context.graphics();
        if (!enabled) {
            var bounds = context.bounds();
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0x40696969);
        }
        if (grayscale == null || !access.contents(slot).isEmpty()) return;

        var client = Minecraft.getInstance();
        var stack = slot.sample();
        var renderer = client.getItemRenderer();
        var model = renderer.getModel(stack, client.level, client.player, 0);
        int[] position = slot.itemPosition(context.bounds());
        var poses = graphics.pose();
        graphics.flush();
        poses.pushPose();
        try {
            poses.translate(position[0] + 8, position[1] + 8, 150);
            poses.scale(16, -16, 16);
            if (!model.usesBlockLight()) Lighting.setupForFlatItems();
            var passes = enabled ? ENABLED_HINTS : DISABLED_HINTS;
            float opacity = slot.appearance().ghostAlpha() * (enabled ? 1 : .6f);
            renderer.render(stack, ItemDisplayContext.GUI, false, poses,
                type -> graphics.bufferSource().getBuffer(passes.computeIfAbsent(type, original -> hintPass(original, opacity))),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            graphics.flush();
        } finally {
            poses.popPose();
            Lighting.setupFor3DItems();
        }
    }

    private static RenderType hintPass(RenderType original, float opacity) {
        return new RenderType("ranch_tool_hint", original.format(), original.mode(), original.bufferSize(),
            original.affectsCrumbling(), original.sortOnUpload(), () -> {
                original.setupRenderState();
                RenderSystem.setShader(() -> grayscale);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(.75f, .75f, .75f, opacity);
            }, () -> {
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.disableBlend();
                original.clearRenderState();
            }) {};
    }

    private static void shadeContents(RanchToolSlot slot, ElementRenderers.Context context) {
        if (slot.enabled(context.access().state()) || context.access().contents(slot).isEmpty()) return;
        var graphics = context.graphics();
        var bounds = context.bounds();
        // Shade the native item after it draws, while keeping pickup, hover and count rendering native.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, 0x40585858);
        graphics.pose().popPose();
    }
}

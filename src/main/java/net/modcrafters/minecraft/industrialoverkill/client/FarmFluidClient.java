package net.modcrafters.minecraft.industrialoverkill.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmFluids;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FarmFluidClient {
    private FarmFluidClient() {}

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        registerFluid(event, FarmFluids.SEWAGE);
        registerFluid(event, FarmFluids.FILTERED_SEWAGE);
    }

    private static void registerFluid(RegisterClientExtensionsEvent event, FarmFluids.Family fluid) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() { return ResourceLocation.withDefaultNamespace("block/water_still"); }

            @Override
            public ResourceLocation getFlowingTexture() { return ResourceLocation.withDefaultNamespace("block/water_flow"); }

            @Override
            public int getTintColor() { return fluid.tint; }
        }, fluid.type);
    }

    @SubscribeEvent
    public static void bucketColors(RegisterColorHandlersEvent.Item event) {
        event.register(new DynamicFluidContainerModel.Colors(), FarmFluids.SEWAGE.bucket.get(), FarmFluids.FILTERED_SEWAGE.bucket.get());
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            translucent(FarmFluids.SEWAGE);
            translucent(FarmFluids.FILTERED_SEWAGE);
        });
    }

    private static void translucent(FarmFluids.Family fluid) {
        ItemBlockRenderTypes.setRenderLayer(fluid.source.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(fluid.flowing.get(), RenderType.translucent());
    }
}

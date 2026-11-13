package net.modcrafters.minecraft.industrialoverkill.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGymEntity;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;
import zone.moddev.mc.lib.gui.client.WidgetRenderers;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GymClient {
    private GymClient() {
    }
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(MachineContent.item(CritterCourierProcessor.ID), AnimalGym.id("occupied"),
                (stack, level, entity, seed) -> CritterCourierProcessor.OCCUPANT.hasContent(stack) ? 1 : 0);
            WidgetRenderers.GLOBAL.register(AnimalGym.id("gym_status"), context -> {
                var font = Minecraft.getInstance().font;
                var text = context.value() instanceof Component component
                    ? component
                    : Component.translatable("gym.status.waiting");
                int y = context.bounds().y();
                for (var line : font.split(text, context.bounds().width())) {
                    context.graphics().drawString(font, line, context.bounds().x(), y, 0xff333333,
                        false);
                    y += 11;
                }
            });
            WidgetRenderers.GLOBAL.register(AnimalGym.id("animal_portrait"), context -> {
                var bounds = context.bounds();
                context.graphics().fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                    0xff30343c);
                var level = Minecraft.getInstance().level;
                int id = context.value() instanceof Number number ? number.intValue() : -1;
                var screen = Minecraft.getInstance().screen;
                var host = screen instanceof zone.moddev.mc.lib.gui.client.MachineScreen machine
                    ? machine.getMenu().blockEntity
                    : null;
                Mob animal = host instanceof AnimalGymEntity gym ? gym.captivePreview() : null;
                if (animal == null && level != null && level.getEntity(id) instanceof Mob live)
                    animal = live;
                if (animal != null) {
                    int scale = (int) (Math.min(bounds.width(), bounds.height())
                        / Math.max(2, animal.getBbHeight() * 1.5));
                    InventoryScreen.renderEntityInInventoryFollowsMouse(context.graphics(),
                        bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), scale, .05f,
                        context.mouseX(), context.mouseY(), animal);
                } else
                    context.graphics().drawCenteredString(Minecraft.getInstance().font,
                        Component.translatable("gym.no_animal"), bounds.x() + bounds.width() / 2,
                        bounds.y() + bounds.height() / 2, 0xffd9b84e);
            });
        });
    }
    @SubscribeEvent
    public static void itemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CritterCourierItemRenderer.instance();
            }
        }, MachineContent.item(CritterCourierProcessor.ID));
    }
    @SubscribeEvent
    public static void itemModels(ModelEvent.ModifyBakingResult event) {
        var id = net.minecraft.client.resources.model.ModelResourceLocation.inventory(CritterCourierProcessor.ID);
        event.getModels().computeIfPresent(id, (key, model) -> new CritterCourierItemModel(model));
    }
    @SubscribeEvent
    public static void models(
        net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        for (var model : GymRenderer.LAMPS)
            event.register(model);
    }
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AnimalGym.CONTROLLER_TYPE.get(), GymRenderer::new);
        event.registerBlockEntityRenderer(MachineContent.entityType(CritterCourierProcessor.ID), CritterCourierRenderer::new);
    }
}

package net.modcrafters.minecraft.industrialoverkill.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriBrainProcessor;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AgriDroneRenderer implements BlockEntityRenderer<DefinedMachineEntity> {
    private static final ModelResourceLocation BODY = model("agri_drone");
    private static final ModelResourceLocation ROTOR = model("agri_drone_rotor");
    // Bottom face of the scanner lens, relative to the model's (8, 8, 8) pivot.
    private static final Vec3 LENS = new Vec3(0, -2.75 / 16, -1.0 / 16);

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
            IndustrialOverkill.MOD_ID, "block/" + name));
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(BODY);
        event.register(ROTOR);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        var id = ResourceLocation.fromNamespaceAndPath(IndustrialOverkill.MOD_ID, "agri_brain");
        event.registerBlockEntityRenderer(MachineContent.entityType(id), context -> new AgriDroneRenderer());
    }

    @Override
    public void render(DefinedMachineEntity machine, float partialTick, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay) {
        var level = machine.getLevel();
        if (level == null) return;
        var drone = AgriDroneClient.sample(machine);
        var controller = machine.getBlockPos();
        double time = level.getGameTime() + (double) partialTick;
        var flight = drone.flight;
        var position = flight.bodyPositionAt(time, AgriDroneFlight.dockPoint(controller));
        var orientation = flight.orientationAt(time, drone.heading);
        float spin = flight.rotorDegreesAt(time);
        int droneLight = LevelRenderer.getLightColor(level, BlockPos.containing(position));
        var vertices = buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();
        pose.translate(position.x - controller.getX(), position.y - controller.getY(), position.z - controller.getZ());
        pose.mulPose(orientation);
        draw(BODY, pose, vertices, droneLight, overlay);
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                pose.pushPose();
                pose.translate(x * 5 / 16.0, 2.5 / 16.0, z * 4 / 16.0);
                pose.mulPose(Axis.YP.rotationDegrees(x * z * spin));
                draw(ROTOR, pose, vertices, droneLight, overlay);
                pose.popPose();
            }
        }
        pose.popPose();
        if (drone.scanSoil != null && level.hasChunkAt(drone.scanSoil)) {
            var lens = position.add(new Vec3(orientation.transform(LENS.toVector3f())));
            double phaseTicks = AgriBrainProcessor.from(machine).phaseGameTicks();
            double progress = Math.clamp((time - drone.phaseStart) / phaseTicks, 0.0, 1.0);
            int color = switch (drone.phase) {
                case FERTILIZE -> 0x7DDBF3;
                case HARVEST -> 0xFFC56B;
                case PLANT -> 0xB2F773;
                default -> machine.visualizerStyle().workColor();
            };
            AgriDroneScanner.render(machine, drone.scanSoil, lens, drone.rowDirection, progress, color, flight, pose, buffers);
        }
    }

    private static void draw(ModelResourceLocation model, PoseStack pose, VertexConsumer vertices,
        int light, int overlay) {
        var client = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(-.5, -.5, -.5);
        client.getBlockRenderer().getModelRenderer().renderModel(pose.last(), vertices, null,
            client.getModelManager().getModel(model), 1, 1, 1, light, overlay, ModelData.EMPTY, null);
        pose.popPose();
    }

    // The field can be visible even when the controller's section is behind the camera.
    @Override
    public boolean shouldRenderOffScreen(DefinedMachineEntity machine) { return true; }

    @Override
    public AABB getRenderBoundingBox(DefinedMachineEntity machine) {
        var bounds = new AABB(machine.getBlockPos()).expandTowards(0, 1, 0);
        var processor = AgriBrainProcessor.from(machine);
        var soil = processor.droneSoil();
        if (soil != null) {
            bounds = bounds.minmax(new AABB(soil).expandTowards(0, 2, 0));
            if (processor.returnProgress() > 0)
                bounds = bounds.minmax(new AABB(processor.firstSoil()).expandTowards(0, 2, 0));
        }
        var drone = AgriDroneClient.get(machine);
        if (drone != null && machine.getLevel() != null)
            bounds = bounds.minmax(new AABB(BlockPos.containing(drone.flight.positionAt(machine.getLevel().getGameTime()))));
        return bounds.inflate(1);
    }

    @Override
    public int getViewDistance() { return 96; }
}

package net.modcrafters.minecraft.industrialoverkill.client;

import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriDockBlock;
import net.modcrafters.minecraft.industrialoverkill.ranch.RanchScan;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RanchAntennaRenderer implements BlockEntityRenderer<DefinedMachineEntity> {
    private static final ModelResourceLocation ANTENNA = ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath(IndustrialOverkill.MOD_ID, "block/ranch_antenna"));
    private static final Vec3 PIVOT = new Vec3(.5, 1.25, .5);
    private static final double NOZZLE_LENGTH = 3.5 / 16;
    private static final DustParticleOptions SIGNAL = new DustParticleOptions(new Vector3f(1f, .74f, .28f), .55f);
    private final WeakHashMap<DefinedMachineEntity, Pulse> lastPulses = new WeakHashMap<>();

    private record Pulse(long tick, int target) {}

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) { event.register(ANTENNA); }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        var id = ResourceLocation.fromNamespaceAndPath(IndustrialOverkill.MOD_ID, "ranch_controller");
        event.registerBlockEntityRenderer(MachineContent.entityType(id), context -> new RanchAntennaRenderer());
    }

    @Override
    public void render(DefinedMachineEntity machine, float partialTick, PoseStack pose, MultiBufferSource buffers,
        int light, int overlay) {
        var level = machine.getLevel();
        var block = machine.getBlockPos();
        if (level == null || !AgriDockBlock.isRanchMount(level.getBlockState(block.above()))) return;
        long tick = level.getGameTime();
        var scan = activeScan(machine);
        var pivot = Vec3.atLowerCornerOf(block).add(PIVOT);
        var direction = Vec3.atLowerCornerOf(machine.facing().getOpposite().getNormal());
        if (scan != null) {
            var target = scan.target();
            var animal = level.getEntity(target.entityId());
            var center = animal == null || !animal.isAlive() ? target.center()
                : animal.getPosition(partialTick).add(0, animal.getBbHeight() / 2.0, 0);
            var toward = center.subtract(pivot).normalize();
            if (toward.lengthSqr() > 0) {
                if (scan.moving()) {
                    double progress = scan.progress(tick + partialTick);
                    double ease = progress * progress * (3 - 2 * progress);
                    direction = scan.from().subtract(pivot).normalize().lerp(toward, ease).normalize();
                } else {
                    direction = toward;
                    pulse(tick, target.entityId(), machine, pivot.add(direction.scale(NOZZLE_LENGTH)), center);
                }
            }
        }

        var client = Minecraft.getInstance();
        int antennaLight = LevelRenderer.getLightColor(level, block.above());
        pose.pushPose();
        pose.translate(PIVOT.x, PIVOT.y, PIVOT.z);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(direction.x, direction.z)));
        pose.mulPose(Axis.XP.rotation((float) Math.atan2(-direction.y, direction.horizontalDistance())));
        pose.translate(-.5, -.5, -.5);
        client.getBlockRenderer().getModelRenderer().renderModel(pose.last(),
            buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS)), null,
            client.getModelManager().getModel(ANTENNA), 1, 1, 1, antennaLight, overlay, ModelData.EMPTY, null);
        pose.popPose();
    }

    private void pulse(long tick, int target, DefinedMachineEntity machine, Vec3 start, Vec3 end) {
        var client = Minecraft.getInstance();
        if (client.isPaused()) return;
        int density = switch (client.options.particles().get()) {
            case ALL -> 4;
            case DECREASED -> 2;
            case MINIMAL -> 1;
        };
        var last = lastPulses.get(machine);
        if (last != null && (last.tick == tick || last.target == target && tick - last.tick < 2)) return;
        lastPulses.put(machine, new Pulse(tick, target));
        int points = Math.clamp((int) Math.ceil(start.distanceTo(end) * density), 2, 64);
        for (int i = 0; i <= points; i++) {
            var point = start.lerp(end, i / (double) points);
            var spark = client.particleEngine.createParticle(SIGNAL, point.x, point.y, point.z, 0, 0, 0);
            if (spark != null) {
                spark.setParticleSpeed(0, 0, 0);
                spark.setLifetime(2);
            }
        }
    }

    private static RanchScan activeScan(DefinedMachineEntity machine) {
        var scan = RanchScan.current(machine);
        if (scan == null) return null;
        return machine.screenFeatures().findProcessor("ranch").filter(processor -> switch (processor.executionStatus()) {
            case PAUSED, HELD -> false;
            default -> true;
        }).isPresent() ? scan : null;
    }

    @Override
    public AABB getRenderBoundingBox(DefinedMachineEntity machine) {
        var bounds = new AABB(machine.getBlockPos()).expandTowards(0, .5, 0);
        var level = machine.getLevel();
        var scan = level != null ? activeScan(machine) : null;
        if (scan != null) {
            var center = scan.target().center();
            bounds = bounds.minmax(new AABB(center, center).inflate(1));
            if (scan.from() != null) bounds = bounds.minmax(new AABB(scan.from(), scan.from()).inflate(1));
        }
        return bounds;
    }
}

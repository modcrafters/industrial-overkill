package net.modcrafters.minecraft.industrialoverkill.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Projects a moving scan line onto the ground or water beneath the crop layer. */
final class AgriDroneScanner {
    private static final float HALF_WIDTH = .34f;

    private AgriDroneScanner() {}

    static void render(DefinedMachineEntity machine, BlockPos soil, Vec3 lens, Direction travel,
        double progress, int color, AgriDroneFlight flight, PoseStack pose, MultiBufferSource buffers) {
        var client = Minecraft.getInstance();
        if (lens.distanceToSqr(client.gameRenderer.getMainCamera().getPosition()) > 64 * 64) return;
        var level = machine.getLevel();
        double height = surfaceHeight(level, soil);
        if (height == 0) return;

        var surface = Vec3.atBottomCenterOf(soil).add(0, height + .015, 0);
        var source = lens.subtract(surface);
        // Local +Z follows this row. Local X runs front-to-back through the machine.
        var forward = Vec3.atLowerCornerOf(travel.getNormal());
        var across = Vec3.atLowerCornerOf(travel.getCounterClockWise().getNormal());
        float sourceX = (float) source.dot(across);
        float sourceZ = (float) source.dot(forward);
        float sweep = (float) (.32 * (2 * progress - 1));
        var vertices = buffers.getBuffer(RenderType.debugQuads());
        var controller = machine.getBlockPos();
        pose.pushPose();
        pose.translate(surface.x - controller.getX(), surface.y - controller.getY(), surface.z - controller.getZ());
        pose.mulPose(Axis.YP.rotationDegrees(-travel.toYRot()));
        var matrix = pose.last();
        // A faint fan widens from the lens into a bright, narrow stripe on the block.
        int fan = 0x24000000 | color;
        vertices.addVertex(matrix, sourceX - .025f, (float) source.y, sourceZ).setColor(fan);
        vertices.addVertex(matrix, sourceX + .025f, (float) source.y, sourceZ).setColor(fan);
        vertices.addVertex(matrix, HALF_WIDTH, 0, sweep).setColor(fan);
        vertices.addVertex(matrix, -HALF_WIDTH, 0, sweep).setColor(fan);
        int stripe = 0xed000000 | color;
        vertices.addVertex(matrix, -HALF_WIDTH, 0, sweep - .018f).setColor(stripe);
        vertices.addVertex(matrix, HALF_WIDTH, 0, sweep - .018f).setColor(stripe);
        vertices.addVertex(matrix, HALF_WIDTH, 0, sweep + .018f).setColor(stripe);
        vertices.addVertex(matrix, -HALF_WIDTH, 0, sweep + .018f).setColor(stripe);
        pose.popPose();

        long tick = level.getGameTime();
        int interval = switch (client.options.particles().get()) {
            case ALL -> 3;
            case DECREASED -> 6;
            case MINIMAL -> 12;
        };
        if (!client.isPaused() && tick % interval == 0 && flight.beginEmission(tick)) {
            var dust = new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255f,
                (color >> 8 & 255) / 255f, (color & 255) / 255f), .4f);
            var contact = surface.add(forward.scale(sweep))
                .add(across.scale((level.random.nextDouble() * 2 - 1) * HALF_WIDTH));
            var spark = client.particleEngine.createParticle(dust, contact.x, contact.y, contact.z, 0, 0, 0);
            if (spark != null) {
                spark.setParticleSpeed(0, .045, 0);
                spark.setLifetime(4);
            }
        }
    }

    private static double surfaceHeight(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        var shape = state.getShape(level, pos);
        double solid = shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
        var fluid = state.getFluidState();
        return fluid.isEmpty() ? solid : Math.max(solid, fluid.getHeight(level, pos));
    }
}

package net.modcrafters.minecraft.industrialoverkill.client;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGymEntity;

/** Shared bait position for drawing the food and aiming both kinds of runner at it. */
public final class GymFoodDisplay {
    // Underside of the 30-degree rod end collar in the generated controller model.
    public static final float X = .1652f, CORD_TOP = 1.6309f;

    private GymFoodDisplay() {
    }

    public static float height(AnimalGymEntity gym, float partialTick) {
        return 1.26f + (float) Math.sin(((gym.getLevel().getGameTime() % 80) + partialTick)
            * (Math.PI * 2 / 80)) * .08f;
    }

    public static Vec3 position(AnimalGymEntity gym, float partialTick) {
        var pos = gym.getBlockPos();
        var towardController = gym.trainingFacing();
        return new Vec3(pos.getX() + .5 - (.5 - X) * towardController.getStepX(),
            pos.getY() + height(gym, partialTick),
            pos.getZ() + .5 - (.5 - X) * towardController.getStepZ());
    }

    public static void lookAt(Mob mob, Vec3 target, float renderScale, boolean interpolate) {
        double dx = target.x - mob.getX(), dz = target.z - mob.getZ();
        // Captive previews are additionally scaled by their renderer; live animals already
        // have fitted dimensions. Use the visible eye height, not the original captive size.
        double dy = target.y - (mob.getY() + mob.getEyeHeight() * renderScale);
        float yaw = (float) (Math.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        mob.setYHeadRot(yaw);
        mob.setXRot(pitch);
        if (!interpolate) {
            mob.yHeadRotO = yaw;
            mob.xRotO = pitch;
        }
    }
}

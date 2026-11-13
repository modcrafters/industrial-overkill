package zone.moddev.mc.industrialoverkill.client;

import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.phys.Vec3;

/** Renders a serialized occupant without ever registering a client-world entity. */
public final class CagedMobRenderer {
    private static final WeakHashMap<Mob, Long> TICKS = new WeakHashMap<>();
    private CagedMobRenderer() {
    }
    public static void render(Mob mob, PoseStack pose, MultiBufferSource buffers, int light,
        float partialTick, float width, float height, float yaw, boolean walking, int intensity) {
        render(mob, pose, buffers, light, partialTick, width, height, yaw, walking, intensity,
            null);
    }
    public static void render(Mob mob, PoseStack pose, MultiBufferSource buffers, int light,
        float partialTick, float width, float height, float yaw, boolean walking, int intensity,
        Vec3 lookTarget) {
        if (mob == null)
            return;
        long now = mob.level().getGameTime();
        Long previous = TICKS.put(mob, now);
        float stride = walking ? GymAnimalAnimation.stride(intensity) : 0;
        // Run/pause packets can arrive between renders in the same world tick.
        mob.walkAnimation.setSpeed(stride);
        if (previous == null || previous != now) {
            for (int i = 0; i < (previous == null ? 1 : Math.clamp(now - previous, 1, 5)); i++)
                mob.walkAnimation.update(stride, 1);
        }
        mob.tickCount = (int) now;
        mob.setYRot(yaw);
        mob.yRotO = yaw;
        mob.yBodyRot = mob.yBodyRotO = mob.yHeadRot = mob.yHeadRotO = yaw;
        mob.setXRot(0);
        mob.xRotO = 0;
        var dimensions = mob.getDimensions(mob.getPose());
        float scale = Math.min(1,
            Math.min(width / dimensions.width(), height / dimensions.height()));
        if (lookTarget != null) {
            GymFoodDisplay.lookAt(mob, lookTarget, scale, false);
            GymBiteAnimation.nibble(mob, partialTick, false);
        }
        pose.pushPose();
        if (lookTarget != null)
            GymBiteAnimation.applyReach(pose, mob, yaw, partialTick, 1);
        pose.scale(scale, scale, scale);
        // Resting animal models suppress leg swing. Override only the
        // preview's render pose, never the saved sit command or the owned trap data.
        var pet = mob instanceof TamableAnimal tamable ? tamable : null;
        boolean sitting = pet != null && pet.isInSittingPose();
        var fox = mob instanceof Fox value ? value : null;
        boolean foxSitting = fox != null && fox.isSitting();
        boolean foxSleeping = fox != null && fox.isSleeping();
        boolean foxCrouching = fox != null && fox.isCrouching();
        var panda = mob instanceof Panda value ? value : null;
        boolean pandaSitting = panda != null && panda.isSitting();
        float sitBlend = panda == null ? 0 : panda.sitAmount;
        float previousSitBlend = panda == null ? 0 : panda.sitAmountO;
        try {
            if (panda != null) {
                panda.sit(!walking && pandaSitting);
                // Clearing only the flag leaves vanilla's interpolated sitting rotation active.
                panda.sitAmount = panda.sitAmountO = panda.isSitting() ? 1 : 0;
            }
            if (walking && pet != null)
                pet.setInSittingPose(false);
            if (walking && fox != null) {
                fox.setSleeping(false);
                fox.setSitting(false);
                fox.setIsCrouching(false);
            }
            Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob)
                .render(mob, yaw, partialTick, pose, buffers, light);
        } finally {
            if (pet != null)
                pet.setInSittingPose(sitting);
            if (fox != null) {
                fox.setSleeping(foxSleeping);
                fox.setSitting(foxSitting);
                fox.setIsCrouching(foxCrouching);
            }
            if (panda != null) {
                panda.sit(pandaSitting);
                panda.sitAmount = sitBlend;
                panda.sitAmountO = previousSitBlend;
            }
            pose.popPose();
        }
    }
}

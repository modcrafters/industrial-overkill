package zone.moddev.mc.industrialoverkill.client;

import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import zone.moddev.mc.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.industrialoverkill.gym.AnimalGymEntity;
import zone.moddev.mc.industrialoverkill.gym.GymFeeding;

/** Server-timed eating, rendered without moving the animal hitbox or consuming client items. */
@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT)
public final class GymBiteAnimation {
    // Positions rather than world/BE references let unloaded animals and worlds be collected.
    private static final WeakHashMap<Mob, BlockPos> RUNNERS = new WeakHashMap<>();
    private static final WeakHashMap<AnimalGymEntity, Long> PARTICLE_TICKS = new WeakHashMap<>();
    private GymBiteAnimation() {
    }

    public static void track(Mob animal, AnimalGymEntity gym) {
        if (gym == null || gym.getGeneration() <= 0)
            RUNNERS.remove(animal);
        else
            RUNNERS.put(animal, gym.getBlockPos());
    }
    private static AnimalGymEntity gym(Mob mob) {
        var pos = RUNNERS.get(mob);
        return pos != null && mob.level().hasChunkAt(pos)
            && mob.level().getBlockEntity(pos) instanceof AnimalGymEntity gym
            && gym.getGeneration() > 0 ? gym : null;
    }
    static void updateLivePoses() {
        var level = Minecraft.getInstance().level;
        for (var iterator = RUNNERS.keySet().iterator(); iterator.hasNext();) {
            var mob = iterator.next();
            var gym = mob.level() == level && !mob.isRemoved() ? gym(mob) : null;
            if (gym == null) {
                iterator.remove();
                continue;
            }
            // Captive previews aim at render time, using the renderer's partial tick.
            if (mob instanceof Animal animal && gym.getAnimalId() == mob.getId())
                GymAnimalAnimation.aim(animal, gym);
        }
    }
    private static float approach(Mob mob, float partialTick) {
        var gym = gym(mob);
        if (gym == null)
            return 0;
        float age = gym.feeding.age(partialTick);
        // Reach the food at contact, then immediately settle back into the running position.
        if (age >= 14)
            return 0;
        float t = age <= GymFeeding.CONTACT ? age / GymFeeding.CONTACT : (14 - age) / 8;
        return t * t * (3 - 2 * t);
    }
    public static float reach(Mob mob, float partialTick) {
        return .65f * approach(mob, partialTick);
    }
    public static float pitchOffset(Mob mob, float partialTick) {
        var gym = gym(mob);
        if (gym == null)
            return 0;
        float age = gym.feeding.age(partialTick);
        return -8 * approach(mob, partialTick)
            + (age >= GymFeeding.CONTACT && age < GymFeeding.EAT_END ? Mth.sin(age * 1.8f) * 3 : 0);
    }
    private static float scale(Mob mob, AnimalGymEntity gym) {
        if (gym.getAnimalId() == mob.getId())
            return 1;
        var dimensions = mob.getDimensions(mob.getPose());
        return Math.min(1, Math.min(.8f / dimensions.width(), 1.25f / dimensions.height()));
    }
    private static Vec3 mouth(Mob mob, AnimalGymEntity gym) {
        float scale = scale(mob, gym);
        var facing = gym.trainingFacing();
        double forward = mob.getBbWidth() * scale * .65;
        return mob.position().add(facing.getStepX() * forward, mob.getEyeHeight() * scale * .9,
            facing.getStepZ() * forward);
    }
    private static Vec3 offset(Mob mob, AnimalGymEntity gym, float partialTick) {
        // Reach toward the bait in both axes, so short animals also get a small hop.
        var delta = GymFoodDisplay.position(gym, partialTick).subtract(mouth(mob, gym));
        return delta.scale(approach(mob, partialTick));
    }
    public static void particles(AnimalGymEntity gym, Mob mob) {
        if (mob == null || gym.getGeneration() <= 0)
            return;
        long now = mob.level().getGameTime();
        Long previous = PARTICLE_TICKS.put(gym, now);
        if (previous != null && previous == now)
            return;
        float age = gym.feeding.age(0);
        if (age < GymFeeding.CONTACT || age >= GymFeeding.EAT_END || gym.feeding.crumbs().isEmpty()
            || now % 2 != 0)
            return;
        var at = mouth(mob, gym).add(offset(mob, gym, 0));
        var random = mob.getRandom();
        var crumb = new ItemParticleOption(ParticleTypes.ITEM, gym.feeding.crumbs());
        for (int i = 0; i < 2; i++)
            mob.level().addParticle(crumb, at.x + (random.nextDouble() - .5) * .12,
                at.y + (random.nextDouble() - .5) * .1, at.z + (random.nextDouble() - .5) * .12,
                (random.nextDouble() - .5) * .06, .03, (random.nextDouble() - .5) * .06);
    }

    public static void nibble(Mob mob, float partialTick, boolean interpolate) {
        mob.setXRot(mob.getXRot() + pitchOffset(mob, partialTick));
        if (!interpolate)
            mob.xRotO = mob.getXRot();
    }

    public static void applyReach(PoseStack pose, Mob mob, float yaw, float partialTick, int sign) {
        var gym = gym(mob);
        if (gym == null)
            return;
        var offset = offset(mob, gym, partialTick).scale(sign);
        pose.translate(offset.x, offset.y, offset.z);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void before(RenderLivingEvent.Pre<?, ?> event) {
        offset(event, 1);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void after(RenderLivingEvent.Post<?, ?> event) {
        offset(event, -1);
    }

    private static void offset(RenderLivingEvent<?, ?> event, int sign) {
        if (!(event.getEntity() instanceof Animal animal) || Minecraft.getInstance().screen != null)
            return; // Inventory portraits retain their normal mouse-following pose.
        var pos = RUNNERS.get(animal);
        if (pos == null || reach(animal, event.getPartialTick()) == 0
            || !animal.level().hasChunkAt(pos))
            return;
        if (animal.level().getBlockEntity(pos) instanceof AnimalGymEntity gym
            && gym.getAnimalId() == animal.getId() && gym.getGeneration() > 0)
            applyReach(event.getPoseStack(), animal, gym.trainingFacing().toYRot(),
                event.getPartialTick(), sign);
    }
}

package zone.moddev.mc.industrialoverkill.client;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.animal.Animal;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import zone.moddev.mc.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.industrialoverkill.gym.AnimalGymEntity;
import zone.moddev.mc.industrialoverkill.gym.GymAnimalRecipe;

/** Drives vanilla species-specific leg animation without moving the animal's hitbox. */
@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT)
public final class GymAnimalAnimation {
    private GymAnimalAnimation() {
    }
    public static float stride(int intensity) {
        return .35f + .15f * Math.clamp(intensity, 1, 3);
    }
    public static AnimalGymEntity workingGym(Animal animal) {
        if (!animal.level().isClientSide || !GymAnimalRecipe.eligible(animal))
            return null;
        // The controller is at most two cells along the belt, in the same base layer.
        // Only inspect already loaded positions; never keep entity/world references.
        for (var direction : Direction.Plane.HORIZONTAL)
            for (int distance = 1; distance <= 2; distance++) {
                var pos = animal.blockPosition().relative(direction, distance);
                if (animal.level().hasChunkAt(pos)
                    && animal.level().getBlockEntity(pos) instanceof AnimalGymEntity gym
                    && gym.getAnimalId() == animal.getId() && gym.getGeneration() > 0
                    && gym.onBelt(animal))
                    return gym;
            }
        return null;
    }
    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Animal animal))
            return;
        var gym = workingGym(animal);
        GymBiteAnimation.track(animal, gym);
        if (gym == null)
            return; // Vanilla immediately resumes control when work stops or the gym unloads.
        float stride = stride(gym.getIntensity());
        animal.walkAnimation.setSpeed(stride);
        animal.walkAnimation.update(stride, 1);
    }
    @SubscribeEvent
    public static void afterClientTick(
        net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        // Minecraft ticks entities before advancing the level clock. Aim after that advance
        // so the head, food bob and server-timed bite use the same animation timestamp.
        GymBiteAnimation.updateLivePoses();
    }
    static void aim(Animal animal, AnimalGymEntity gym) {
        // Client body/head controllers otherwise turn an idle mob between server updates.
        float yaw = gym.trainingFacing().toYRot();
        animal.setYRot(yaw);
        animal.setYHeadRot(yaw);
        animal.yRotO = animal.yBodyRot = animal.yBodyRotO = yaw;
        animal.yHeadRotO = yaw;
        if (!gym.displayFood().isEmpty()
            || gym.feeding.age(0) < zone.moddev.mc.industrialoverkill.gym.GymFeeding.EAT_END) {
            GymFoodDisplay.lookAt(animal, GymFoodDisplay.position(gym, 0), 1, true);
            GymBiteAnimation.nibble(animal, 0, true);
        } else {
            animal.setXRot(0);
            animal.xRotO = 0;
        }
    }
}

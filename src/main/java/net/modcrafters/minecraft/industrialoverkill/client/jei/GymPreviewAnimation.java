package net.modcrafters.minecraft.industrialoverkill.client.jei;

import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Rabbit;

import net.modcrafters.minecraft.industrialoverkill.client.GymAnimalAnimation;

/** Advances only visual state, without ticking a preview's AI or adding it to the world. */
final class GymPreviewAnimation {
    private long previousNanos;
    private double age;

    void advance(Animal animal, int intensity) {
        long now = System.nanoTime();
        double elapsed = previousNanos == 0
            ? 0
            : Math.clamp((now - previousNanos) / 50_000_000.0, 0, 5);
        previousNanos = now;
        age += elapsed;
        float stride = GymAnimalAnimation.stride(intensity);
        // Inventory rendering uses partialTick=1. Integrate phase per frame for smooth
        // motion at any FPS, then restore the actual stride amplitude for vanilla models.
        animal.walkAnimation.update(stride * (float) elapsed, 1);
        animal.walkAnimation.setSpeed(stride);
        animal.tickCount = (int) age;
        if (animal instanceof Rabbit rabbit) {
            // Vanilla rabbit legs use jump completion instead of walkAnimation.
            rabbit.jumpDuration = 1000;
            rabbit.jumpTicks = (int) (animal.walkAnimation.position() % 4 / 4 * 1000);
        }
    }
}

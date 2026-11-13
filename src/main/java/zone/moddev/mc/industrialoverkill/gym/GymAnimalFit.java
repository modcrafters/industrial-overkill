package zone.moddev.mc.industrialoverkill.gym;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;

/** Temporary, synchronized size adjustment; never changes an animal's original scale. */
public final class GymAnimalFit {
    // Inside the hazard rails, with head/stride clearance below the gantry.
    public static final float MAX_WIDTH = .8f;
    public static final float MAX_HEIGHT = 1.25f;
    private static final ResourceLocation MODIFIER = AnimalGym.id("treadmill_fit");
    private GymAnimalFit() {
    }
    public static void apply(Animal animal) {
        var scale = animal.getAttribute(Attributes.SCALE);
        if (scale == null)
            return;
        var current = scale.getModifier(MODIFIER);
        double previous = current == null ? 1 : 1 + current.amount();
        var dimensions = animal.getDimensions(animal.getPose());
        // Divide out our own modifier: repeated work ticks must not shrink again
        // or alternate between the original and fitted sizes.
        double factor = Math.min(1, Math.min(MAX_WIDTH * previous / dimensions.width(),
            MAX_HEIGHT * previous / dimensions.height()));
        if (Math.abs(factor - previous) < .00001)
            return;
        scale.removeModifier(MODIFIER);
        if (factor < .99999)
            scale.addTransientModifier(new AttributeModifier(MODIFIER, factor - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        animal.refreshDimensions();
    }
    public static void remove(Animal animal) {
        var scale = animal.getAttribute(Attributes.SCALE);
        if (scale != null && scale.hasModifier(MODIFIER)) {
            scale.removeModifier(MODIFIER);
            if (!animal.isRemoved())
                animal.refreshDimensions();
        }
    }
}

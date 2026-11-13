package zone.moddev.mc.industrialoverkill.gym;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;

/** Removes retired gym bonuses without touching other mods' attributes or saved scores. */
public final class GymLegacyFitness {
    private GymLegacyFitness() {
    }

    public static void clear(Animal animal) {
        remove(animal, Attributes.MOVEMENT_SPEED, "gym_speed");
        remove(animal, Attributes.KNOCKBACK_RESISTANCE, "gym_strength");
        remove(animal, Attributes.JUMP_STRENGTH, "gym_jump");
        boolean healthChanged = remove(animal, Attributes.MAX_HEALTH, "gym_health");
        if (healthChanged && animal.getHealth() > animal.getMaxHealth())
            animal.setHealth(animal.getMaxHealth());
    }

    private static boolean remove(Animal animal, Holder<Attribute> attribute, String key) {
        var instance = animal.getAttribute(attribute);
        if (instance == null || !instance.hasModifier(AnimalGym.id(key)))
            return false;
        instance.removeModifier(AnimalGym.id(key));
        return true;
    }
}

package net.modcrafters.minecraft.industrialoverkill.gym;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;

import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;

import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.machine.feature.PortableVariable;

/** The item in slot 3 is the sole owner. Decoded mobs are never added to the world. */
public final class GymCaptive {
    private final AnimalGymEntity gym;
    private EntitySnapshot cached = EntitySnapshot.EMPTY;
    private final PortableVariable<EntitySnapshot>.Reader reader =
        CritterCourierProcessor.OCCUPANT.reader();
    private Mob mob;
    private boolean resolved;
    public GymCaptive(AnimalGymEntity gym) {
        this.gym = gym;
    }
    public boolean installed() {
        refresh();
        return !cached.isEmpty();
    }
    private void refresh() {
        var data = reader.read(gym.food.getStackInSlot(3));
        if (!data.equals(cached)) {
            cached = data;
            mob = null;
            resolved = false;
        }
    }
    public Mob mob() {
        refresh();
        if (!resolved && gym.getLevel() != null) {
            resolved = gym.getLevel() != null;
            mob = CritterCourierProcessor.ENTITIES.createDetached(cached, gym.getLevel()).orElse(null);
        }
        return mob;
    }
    public Animal animal() {
        return mob() instanceof Animal animal ? animal : null;
    }
    public void persist() {
        if (!installed() || mob == null)
            return;
        cached = CritterCourierProcessor.ENTITIES.capture(mob);
        var stack = gym.food.getStackInSlot(3).copy();
        CritterCourierProcessor.OCCUPANT.write(stack, cached);
        gym.persistCaptive(stack);
        reader.read(stack);
    }
}

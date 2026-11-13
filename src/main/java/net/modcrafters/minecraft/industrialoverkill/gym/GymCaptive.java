package net.modcrafters.minecraft.industrialoverkill.gym;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.component.CustomData;

import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierData;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** The item in slot 3 is the sole owner. Decoded mobs are never added to the world. */
public final class GymCaptive {
    private final AnimalGymEntity gym;
    private CompoundTag cached = new CompoundTag();
    private CustomData component = CustomData.EMPTY;
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
        var stack = gym.food.getStackInSlot(3);
        var next = stack.is(MachineContent.item(CritterCourierData.ID))
            ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            : CustomData.EMPTY;
        // Components are immutable: reading the same item must not copy its full entity NBT.
        if (next != component) {
            component = next;
            var data = CritterCourierData.read(stack);
            if (!data.equals(cached)) {
                cached = data;
                mob = null;
                resolved = false;
            }
        }
    }
    public Mob mob() {
        refresh();
        if (!resolved && gym.getLevel() != null) {
            resolved = gym.getLevel() != null;
            mob = CritterCourierData.load(cached, gym.getLevel());
        }
        return mob;
    }
    public Animal animal() {
        return mob() instanceof Animal animal ? animal : null;
    }
    public void persist() {
        if (!installed() || mob == null)
            return;
        cached = CritterCourierData.save(mob);
        var stack = gym.food.getStackInSlot(3).copy();
        CritterCourierData.write(stack, cached);
        gym.persistCaptive(stack);
        component = gym.food.getStackInSlot(3).getOrDefault(DataComponents.CUSTOM_DATA,
            CustomData.EMPTY);
    }
}

package zone.moddev.mc.industrialoverkill.gym;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

/** One paid serving on the stick. Eating grants a ration; refilling never grants a second one. */
public final class GymFeeding {
    public static final int CONTACT = 6, EAT_END = 36, REFILL = 56;
    private final AnimalGymEntity gym;
    private ItemStack held = ItemStack.EMPTY, eaten = ItemStack.EMPTY;
    private int age = REFILL;
    private long sampledAt;
    private java.util.UUID diner;
    public GymFeeding(AnimalGymEntity gym) {
        this.gym = gym;
    }

    /** Advance only on working ticks. Pending servings survive pause and occupant changes. */
    public void tick(Animal animal) {
        if (!animal.getUUID().equals(diner)) {
            diner = animal.getUUID();
            eaten = ItemStack.EMPTY;
            age = REFILL;
        }
        if (age < REFILL)
            age++;
        if (age >= REFILL)
            eaten = ItemStack.EMPTY;
        if (!held.isEmpty() && !GymAnimalRecipe.foodFor(animal, held))
            returnServing();
        if (held.isEmpty() && age >= REFILL) {
            for (int slot = 0; slot < 3; slot++) {
                if (GymAnimalRecipe.foodFor(animal, gym.food.getStackInSlot(slot))) {
                    held = gym.food.extractItem(slot, 1, false);
                    gym.setChanged();
                    gym.synchronize();
                    break;
                }
            }
        }
    }
    public boolean eat(Animal animal) {
        if (held.isEmpty() || !GymAnimalRecipe.foodFor(animal, held))
            return false;
        eaten = held;
        held = ItemStack.EMPTY;
        age = 0;
        gym.setChanged();
        return true;
    }
    /** Inspect the next serving without charging food or advancing its animation. */
    public GymAnimalRecipe nextRecipe(Animal animal) {
        var recipe = GymAnimalRecipe.find(animal, held);
        if (recipe != null)
            return recipe;
        for (int slot = 0; slot < 3; slot++) {
            recipe = GymAnimalRecipe.find(animal, gym.food.getStackInSlot(slot));
            if (recipe != null)
                return recipe;
        }
        return null;
    }
    public ItemStack serving() {
        return held.copy();
    }
    public ItemStack crumbs() {
        return eaten;
    }
    public float age(float partialTick) {
        if (gym.getGeneration() <= 0)
            return REFILL;
        return Math.min(REFILL, age + (gym.getLevel() != null && gym.getLevel().isClientSide
            ? Math.max(0, gym.getLevel().getGameTime() - sampledAt) + partialTick
            : 0));
    }
    public ItemStack display() {
        if (gym.getGeneration() <= 0)
            return ItemStack.EMPTY;
        return age(0) < CONTACT ? eaten : held;
    }
    private void returnServing() {
        var stack = held;
        held = ItemStack.EMPTY;
        for (int slot = 0; slot < 3 && !stack.isEmpty(); slot++)
            stack = gym.food.insertItem(slot, stack, false);
        if (!stack.isEmpty())
            drop(stack);
        gym.setChanged();
    }
    private void drop(ItemStack stack) {
        var pos = gym.getBlockPos();
        Containers.dropItemStack(gym.getLevel(), pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
            stack);
    }
    public void dismantle() {
        var stack = held;
        held = ItemStack.EMPTY;
        eaten = ItemStack.EMPTY;
        age = REFILL;
        if (!stack.isEmpty())
            drop(stack);
    }
    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put("held", held.saveOptional(registries));
        tag.put("eaten", eaten.saveOptional(registries));
        tag.putInt("age", age);
        if (diner != null)
            tag.putUUID("diner", diner);
        return tag;
    }
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        held = ItemStack.parseOptional(registries, tag.getCompound("held"));
        eaten = ItemStack.parseOptional(registries, tag.getCompound("eaten"));
        age = tag.contains("age") ? Math.clamp(tag.getInt("age"), 0, REFILL) : REFILL;
        diner = tag.hasUUID("diner") ? tag.getUUID("diner") : null;
        sampledAt = gym.getLevel() == null ? 0 : gym.getLevel().getGameTime();
    }
}

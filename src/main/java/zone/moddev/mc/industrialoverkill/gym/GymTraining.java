package zone.moddev.mc.industrialoverkill.gym;

import java.util.Comparator;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

import zone.moddev.mc.lib.machine.feature.FeatureHost;
import zone.moddev.mc.lib.machine.feature.MachineFeature;

public final class GymTraining implements MachineFeature<CompoundTag> {
    private static final String RATION = "industrial_overkill:gym_ration";
    private static final String RATION_POWER = "industrial_overkill:gym_ration_power";
    private final AnimalGymEntity gym;
    private UUID target;
    private boolean enabled = true;
    private int intensity = 1;
    private String status = "waiting";
    private int generation;
    // Once full, wait for meaningful demand instead of restarting for every extracted work unit.
    private boolean waitingForDemand;
    public GymTraining(AnimalGymEntity gym) {
        this.gym = gym;
    }
    @Override
    public String key() {
        // Retain the feature key so existing enabled/intensity settings still load.
        return "training";
    }
    @Override
    public Codec<CompoundTag> codec() {
        return CompoundTag.CODEC;
    }
    @Override
    public boolean syncsToClient() {
        return true;
    }
    @Override
    public CompoundTag state() {
        var tag = new CompoundTag();
        tag.putBoolean("enabled", enabled);
        tag.putInt("intensity", intensity);
        tag.putBoolean("waiting_for_demand", waitingForDemand);
        return tag;
    }
    @Override
    public void restore(CompoundTag tag) {
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        intensity = Math.clamp(tag.getInt("intensity"), 1, 3);
        waitingForDemand = tag.getBoolean("waiting_for_demand");
    }
    public Animal animal() {
        if (gym.captive.installed())
            return gym.captive.animal();
        return target != null && gym.getLevel() instanceof ServerLevel level
            && level.getEntity(target) instanceof Animal animal ? animal : null;
    }
    public boolean enabled() {
        return enabled;
    }
    public int intensity() {
        return intensity;
    }
    public int generation() {
        return generation;
    }
    public String status() {
        return status;
    }
    public record FoodReserve(long energy, long ticks) {
    }
    /** Food energy and active runtime at the selected intensity; excludes stored FE and rests. */
    public FoodReserve foodReserve() {
        if (!(gym.getLevel() instanceof ServerLevel level))
            return new FoodReserve(0, 0);
        Animal animal = animal();
        if (!gym.captive.installed() && (animal == null || !gym.onBelt(animal)
            || !GymAnimalRecipe.eligible(animal))) {
            // Pausing releases the training claim. Inspect the occupant without reclaiming it.
            animal = level.getEntitiesOfClass(Animal.class, gym.beltBounds(),
                a -> GymAnimalRecipe.eligible(a) && gym.onBelt(a)).stream()
                .min(Comparator.comparing(a -> a.getUUID().toString())).orElse(null);
        }
        if (animal == null || !GymAnimalRecipe.eligible(animal))
            return new FoodReserve(0, 0);
        var data = animal.getPersistentData();
        long ration = Math.max(0, data.getInt(RATION));
        long energy = ration * rationPower(data);
        long ticks = workTicks(ration);
        for (int slot = -1; slot < 3; slot++) {
            var stack = slot == -1 ? gym.feeding.serving() : gym.food.getStackInSlot(slot);
            var recipe = GymAnimalRecipe.find(animal, stack);
            if (recipe != null) {
                energy += (long) stack.getCount() * recipe.energyPerTick() * recipe.foodDurationTicks();
                ticks += stack.getCount() * workTicks(recipe.foodDurationTicks());
            }
        }
        return new FoodReserve(energy, ticks);
    }
    private long workTicks(long ration) {
        // A meal's last partial work unit takes a whole tick; the next meal starts next tick.
        return (ration + intensity - 1) / intensity;
    }
    private static int rationPower(CompoundTag data) {
        return data.contains(RATION_POWER)
            ? Math.clamp(data.getInt(RATION_POWER), 1, GymAnimalRecipe.MAX_ENERGY)
            : GymAnimalRecipe.DEFAULT_ENERGY;
    }
    public void control(String action) {
        switch (action) {
            case "toggle" -> enabled = !enabled;
            case "release" -> enabled = false;
            case "intensity" -> intensity = intensity % 3 + 1;
            default -> {
                return;
            }
        }
        if (!enabled) {
            status = "paused";
            reset();
        }
        gym.setChanged();
        gym.synchronize();
    }
    @Override
    public void reset() {
        boolean stopped = generation > 0;
        var animal = animal();
        if (animal != null) {
            GymAnimals.release(animal, gym);
        }
        GymAnimals.releaseOwner(gym);
        target = null;
        generation = 0;
        gym.updateLampLight();
        if (stopped)
            gym.synchronize();
    }
    @Override
    public void suspended(FeatureHost host) {
        hold(!gym.structureReady() ? "suspended" : !enabled ? "paused" : "redstone", host);
    }
    private void hold(String next, FeatureHost host) {
        boolean changed = !status.equals(next) || generation != 0 || target != null;
        status = next;
        // reset() may publish immediately. Do not send the previous running status with zero FE.
        reset();
        if (changed) {
            host.markChanged();
            host.markSync();
        }
    }
    @Override
    public void tick(FeatureHost host) {
        if (!enabled) {
            hold("paused", host);
            return;
        }
        if (waitingForDemand && gym.energy.space() < gym.energy.capacity() / 4) {
            hold("full", host);
            return;
        }
        boolean contained = gym.captive.installed();
        Animal animal = animal();
        if (contained && (animal == null || !GymAnimalRecipe.eligible(animal))) {
            hold("unsupported", host);
            return;
        }
        if (!contained && animal != null
            && (!GymAnimalRecipe.eligible(animal) || !gym.onBelt(animal))) {
            reset();
            animal = null;
        }
        if (animal == null) {
            animal = host.level()
                .getEntitiesOfClass(Animal.class, gym.beltBounds(),
                    a -> GymAnimalRecipe.eligible(a) && gym.onBelt(a))
                .stream().sorted(Comparator.comparing(a -> a.getUUID().toString()))
                .filter(a -> GymAnimals.claim(a, gym)).findFirst().orElse(null);
            if (animal == null) {
                hold("waiting", host);
                return;
            }
            target = animal.getUUID();
        }
        if (!contained && !GymAnimals.claim(animal, gym)) {
            hold("occupied", host);
            return;
        }
        var data = animal.getPersistentData();
        int ration = Math.max(0, data.getInt(RATION));
        var recipe = ration == 0 ? gym.feeding.nextRecipe(animal) : null;
        if (ration == 0 && recipe == null) {
            gym.feeding.tick(animal);
            hold("food", host);
            return;
        }
        // Already eaten food keeps its paid duration and rate across reloads and transport.
        // Old saves have no power snapshot and retain the original 8 FE/work-unit rate.
        int power = ration == 0
            ? recipe.energyPerTick()
            : rationPower(data);
        int work = Math.min(intensity, ration == 0 ? recipe.foodDurationTicks() : ration);
        int output = power * work;
        if (gym.energy.space() < output) {
            waitingForDemand = true;
            hold("full", host);
            return;
        }
        gym.feeding.tick(animal);
        boolean ate = false;
        if (ration == 0) {
            if (!gym.feeding.eat(animal)) {
                hold("food", host);
                return;
            }
            ration = recipe.foodDurationTicks();
            data.putInt(RATION_POWER, power);
            ate = true;
        }
        data.putInt(RATION, ration - work);
        gym.energy.insert(output, false);
        if (contained)
            gym.captive.persist();
        else
            GymAnimalFit.apply(animal);
        boolean changed = generation != output;
        waitingForDemand = false;
        generation = output;
        gym.updateLampLight();
        status = "running";
        if (changed || ate)
            gym.synchronize();
        host.markChanged();
        host.markSync();
    }
}

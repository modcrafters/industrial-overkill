package zone.moddev.mc.industrialoverkill.gym;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import zone.moddev.mc.industrialoverkill.IndustrialOverkill;

/** Ephemeral leases never disable AI or leave persistent restraint flags on unloaded animals. */
@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID)
public final class GymAnimals {
    private record Lease(AnimalGymEntity owner, UUID formation, long expires) {
    }
    private static final Map<ServerLevel, Map<UUID, Lease>> LEASES = new WeakHashMap<>();
    private GymAnimals() {
    }
    public static void releaseOwner(AnimalGymEntity owner) {
        if (owner.getLevel() instanceof ServerLevel level && LEASES.containsKey(level))
            LEASES.get(level).entrySet().removeIf(entry -> {
                if (entry.getValue().owner() != owner)
                    return false;
                if (level.getEntity(entry.getKey()) instanceof Animal animal)
                    GymAnimalFit.remove(animal);
                return true;
            });
    }
    @SubscribeEvent
    public static void unloaded(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var leases = LEASES.remove(level);
            if (leases != null)
                for (var id : leases.keySet())
                    if (level.getEntity(id) instanceof Animal animal)
                        GymAnimalFit.remove(animal);
        }
    }
    public static boolean claim(Animal animal, AnimalGymEntity owner) {
        if (!(animal.level() instanceof ServerLevel level) || owner.getLevel() != level
            || !GymAnimalRecipe.eligible(animal) || !owner.onBelt(animal)
            || !owner.multiblock().ready()
            || level.getBlockEntity(owner.getBlockPos()) != owner)
            return false;
        var leases = LEASES.computeIfAbsent(level, ignored -> new HashMap<>());
        var previous = leases.get(animal.getUUID());
        if (previous != null && valid(previous, level) && previous.owner() != owner)
            return false;
        leases.put(animal.getUUID(),
            new Lease(owner, owner.multiblock().formation(), level.getGameTime() + 2));
        return true;
    }
    private static boolean valid(Lease lease, ServerLevel level) {
        var owner = lease.owner();
        return lease.expires() >= level.getGameTime() && !owner.isRemoved()
            && level.hasChunkAt(owner.getBlockPos())
            && level.getBlockEntity(owner.getBlockPos()) == owner
            && lease.formation().equals(owner.multiblock().formation())
            && owner.multiblock().ready();
    }
    public static void release(Animal animal, AnimalGymEntity owner) {
        if (!(animal.level() instanceof ServerLevel level))
            return;
        var leases = LEASES.get(level);
        if (leases != null && leases.get(animal.getUUID()) instanceof Lease lease
            && lease.owner() == owner) {
            leases.remove(animal.getUUID());
            GymAnimalFit.remove(animal);
        }
    }
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Animal animal) {
            GymAnimalFit.remove(animal);
            GymLegacyFitness.clear(animal);
        }
    }
    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
            && event.getEntity() instanceof Animal animal) {
            var leases = LEASES.get(level);
            if (leases != null)
                leases.remove(animal.getUUID());
            GymAnimalFit.remove(animal);
        }
    }
    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Animal animal)
            || !(animal.level() instanceof ServerLevel level))
            return;
        var leases = LEASES.get(level);
        var lease = leases == null ? null : leases.get(animal.getUUID());
        if (lease == null) {
            GymAnimalFit.remove(animal);
            return;
        }
        if (!valid(lease, level) || !GymAnimalRecipe.eligible(animal)
            || !lease.owner().onBelt(animal)) {
            leases.remove(animal.getUUID());
            GymAnimalFit.remove(animal);
            return;
        }
        GymAnimalFit.apply(animal);
        animal.getNavigation().stop();
        animal.setDeltaMovement(Vec3.ZERO);
        Vec3 center = lease.owner().beltCenter();
        animal.teleportTo(center.x, center.y, center.z);
        animal.setYRot(lease.owner().trainingFacing().toYRot());
        animal.setYHeadRot(animal.getYRot());
        animal.yBodyRot = animal.getYRot();
    }
}

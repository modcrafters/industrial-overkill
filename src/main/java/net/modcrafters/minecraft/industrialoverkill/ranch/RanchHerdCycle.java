package net.modcrafters.minecraft.industrialoverkill.ranch;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;

/** A single pass through the animals present when a scan cycle starts. */
final class RanchHerdCycle {
    private List<UUID> animals = List.of();
    private final Set<UUID> scannedMates = new HashSet<>();
    private AABB area;
    private int next;
    private int expectedBirths;

    Animal next(ServerLevel level, AABB bounds) {
        if (bounds.equals(area)) {
            var animal = nextPresent(level);
            if (animal != null) return animal;
        }
        area = bounds;
        animals = level.getEntitiesOfClass(Animal.class, bounds, Animal::isAlive).stream()
            .map(Animal::getUUID).toList();
        next = 0;
        expectedBirths = 0;
        scannedMates.clear();
        return nextPresent(level);
    }

    private Animal nextPresent(ServerLevel level) {
        while (next < animals.size()) {
            var id = animals.get(next++);
            if (scannedMates.contains(id)) continue;
            var animal = resolve(level, id);
            if (animal != null) return animal;
        }
        return null;
    }

    Stream<Animal> present(ServerLevel level) {
        return animals.stream().map(id -> resolve(level, id)).filter(Objects::nonNull);
    }

    boolean canBreed(ServerLevel level, int limit) {
        int remaining = limit - expectedBirths;
        return remaining > 0 && present(level).limit(remaining).count() < remaining
            && present(level).noneMatch(Animal::isInLove);
    }

    void bred(Animal mate) {
        scannedMates.add(mate.getUUID());
        expectedBirths++;
    }

    private Animal resolve(ServerLevel level, UUID id) {
        return inArea(level, id, area);
    }

    static Animal inArea(ServerLevel level, UUID id, AABB area) {
        return id != null && level.getEntity(id) instanceof Animal animal && animal.isAlive()
            && area.intersects(animal.getBoundingBox()) ? animal : null;
    }
}

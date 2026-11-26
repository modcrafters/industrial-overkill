package net.modcrafters.minecraft.industrialoverkill.agri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

import com.google.common.collect.MapMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Server-thread index of the Agri-Brain fields intersecting each chunk. */
public final class AgriSoilRetention {
    private static final Map<Level, Map<ChunkPos, Set<AgriSoilRetention>>> FIELDS = new WeakHashMap<>();
    final AABB bounds;
    private final BooleanSupplier running;
    // Controllers own their chunk buckets. The index never keeps an unloaded controller or world alive.
    private final List<Set<AgriSoilRetention>> buckets = new ArrayList<>();

    AgriSoilRetention(Level level, AABB bounds, BooleanSupplier running) {
        this.bounds = bounds;
        this.running = running;
        var chunks = FIELDS.computeIfAbsent(level, key -> new MapMaker().weakValues().makeMap());
        var first = new ChunkPos(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ));
        var last = new ChunkPos(BlockPos.containing(bounds.maxX - 1, bounds.minY, bounds.maxZ - 1));
        ChunkPos.rangeClosed(first, last).forEach(chunk -> {
            var fields = chunks.computeIfAbsent(chunk, key -> Collections.newSetFromMap(new WeakHashMap<>()));
            fields.add(this);
            buckets.add(fields);
        });
    }

    void remove() {
        buckets.forEach(fields -> fields.remove(this));
        buckets.clear();
    }

    public static boolean delaysDrying(ServerLevel level, BlockPos pos) {
        // Overlapping machines share one drying opportunity per plot every eight ticks.
        if (Math.floorMod(level.getGameTime() + pos.getX() + 3L * pos.getZ(), 8) == 0) return false;
        var chunks = FIELDS.get(level);
        if (chunks == null) return false;
        var fields = chunks.get(new ChunkPos(pos));
        if (fields == null) return false;
        for (var field : fields) {
            if (field.bounds.contains(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5)
                && field.running.getAsBoolean()) return true;
        }
        return false;
    }
}

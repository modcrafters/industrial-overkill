package net.modcrafters.minecraft.industrialoverkill.agri;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Fertilizer stays with the crop, independently of the drone or its controller. */
public final class AgriPellets extends SavedData {
    private static final String FILE = "industrial_overkill_pellets";
    private static final Factory<AgriPellets> FACTORY = new Factory<>(AgriPellets::new, AgriPellets::load);
    private static final int INTERVAL = 600;

    private record Boost(BlockPos pos, long due, int remaining) {}

    private final Map<BlockPos, Boost> active = new HashMap<>();
    private final PriorityQueue<Boost> pending = new PriorityQueue<>(Comparator.comparingLong(Boost::due));

    private AgriPellets() {}

    static boolean active(ServerLevel level, BlockPos pos) {
        var data = level.getDataStorage().get(FACTORY, FILE);
        return data != null && data.active.containsKey(pos);
    }

    static void start(ServerLevel level, BlockPos pos) {
        var data = level.getDataStorage().computeIfAbsent(FACTORY, FILE);
        data.schedule(new Boost(pos.immutable(), level.getGameTime() + INTERVAL, 2));
    }

    public static void cropChanged(ServerLevel level, BlockPos pos, BlockState old, BlockState next) {
        int before = AgriFertilizing.remaining(old);
        if (before < 0) return;
        int after = AgriFertilizing.remaining(next);
        if (old.getBlock() == next.getBlock() && after > 0 && after <= before) return;
        var data = level.getDataStorage().get(FACTORY, FILE);
        if (data != null && data.active.remove(pos) != null) data.setDirty();
    }

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var data = level.getDataStorage().get(FACTORY, FILE);
        if (data != null) data.tickDue(level);
    }

    private void tickDue(ServerLevel level) {
        long now = level.getGameTime();
        while (!pending.isEmpty() && pending.peek().due <= now) {
            var boost = pending.remove();
            if (active.get(boost.pos) != boost) continue;
            if (!level.hasChunkAt(boost.pos)) {
                schedule(new Boost(boost.pos, now + 20, boost.remaining));
                continue;
            }
            active.remove(boost.pos);
            setDirty();
            if (AgriFertilizing.advance(level, boost.pos, 1)) {
                level.levelEvent(1505, boost.pos, 4);
                if (boost.remaining > 1 && AgriFertilizing.remaining(level.getBlockState(boost.pos)) > 0)
                    schedule(new Boost(boost.pos, now + INTERVAL, boost.remaining - 1));
            }
        }
    }

    private void schedule(Boost boost) {
        active.put(boost.pos, boost);
        pending.add(boost);
        setDirty();
    }

    private static AgriPellets load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new AgriPellets();
        for (var element : tag.getList("boosts", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            int remaining = entry.getInt("remaining");
            if (remaining >= 1 && remaining <= 2)
                data.schedule(new Boost(BlockPos.of(entry.getLong("pos")), entry.getLong("due"), remaining));
        }
        data.setDirty(false);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        for (var boost : active.values()) {
            var entry = new CompoundTag();
            entry.putLong("pos", boost.pos.asLong());
            entry.putLong("due", boost.due);
            entry.putInt("remaining", boost.remaining);
            entries.add(entry);
        }
        tag.put("boosts", entries);
        return tag;
    }
}

package net.modcrafters.minecraft.industrialoverkill.ranch;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;

/** A timed antenna motion or a scan focused on a living target. */
public record RanchScan(Vec3 from, Target target, long start, int duration) {
    public record Target(int entityId, Vec3 center) {
        static Target of(Animal animal) { return new Target(animal.getId(), animal.getBoundingBox().getCenter()); }

        private CompoundTag save() {
            var tag = new CompoundTag();
            tag.putInt("entity", entityId);
            tag.put("center", savePoint(center));
            return tag;
        }

        private static Target load(CompoundTag tag) {
            return new Target(tag.getInt("entity"), loadPoint(tag.getCompound("center")));
        }
    }

    public static RanchScan current(ProcessorMachine machine) {
        var view = RanchClientState.of(machine);
        return view == null ? null : view.scan();
    }

    public boolean moving() { return from != null; }

    public double progress(double time) { return Math.clamp((time - start) / duration, 0, 1); }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("target", target.save());
        if (from != null) tag.put("from", savePoint(from));
        tag.putLong("start", start);
        tag.putInt("duration", duration);
        return tag;
    }

    static RanchScan load(CompoundTag tag) {
        return new RanchScan(tag.contains("from") ? loadPoint(tag.getCompound("from")) : null,
            Target.load(tag.getCompound("target")), tag.getLong("start"), Math.max(1, tag.getInt("duration")));
    }

    private static CompoundTag savePoint(Vec3 point) {
        var tag = new CompoundTag();
        tag.putDouble("x", point.x);
        tag.putDouble("y", point.y);
        tag.putDouble("z", point.z);
        return tag;
    }

    private static Vec3 loadPoint(CompoundTag tag) {
        return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }
}

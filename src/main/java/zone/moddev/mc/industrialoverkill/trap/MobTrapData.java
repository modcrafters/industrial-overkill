package zone.moddev.mc.industrialoverkill.trap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** A trap owns one serialized mob, never a second live copy in the world. */
public final class MobTrapData {
    private MobTrapData() {
    }
    public static boolean capturable(Mob mob) {
        return mob.isAlive() && !mob.isPassenger() && !mob.isVehicle()
            && !(mob instanceof EnderDragon) && !(mob instanceof WitherBoss);
    }
    public static CompoundTag read(ItemStack stack) {
        return stack.is(MobTrap.ITEM.get())
            ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getCompound("mob")
            : new CompoundTag();
    }
    public static boolean occupied(ItemStack stack) {
        return !read(stack).isEmpty();
    }
    public static CompoundTag save(Mob mob) {
        var data = new CompoundTag();
        mob.save(data);
        // Vanilla panda NBT keeps genes but omits the resting pose.
        if (mob instanceof Panda panda)
            data.putBoolean("industrial_overkill:panda_sitting", panda.isSitting());
        // A lead stays in the world, never points at a former holder after transport.
        data.remove("leash");
        data.remove("Leash");
        return data;
    }
    public static void write(ItemStack stack, CompoundTag data) {
        var wrapper = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.isEmpty())
            wrapper.remove("mob");
        else
            wrapper.put("mob", data.copy());
        if (wrapper.isEmpty())
            stack.remove(DataComponents.CUSTOM_DATA);
        else
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(wrapper));
    }
    public static ItemStack stack(CompoundTag data) {
        var stack = new ItemStack(MobTrap.ITEM.get());
        write(stack, data);
        return stack;
    }
    public static Component name(CompoundTag data) {
        return EntityType.by(data).map(EntityType::getDescription)
            .orElse(Component.translatable("trap.unknown"));
    }
    public static Mob load(CompoundTag data, Level level) {
        if (data.isEmpty() || data.contains("Passengers"))
            return null;
        try {
            var entity = EntityType.loadEntityRecursive(data.copy(), level, value -> value);
            if (entity instanceof Mob mob && capturable(mob)) {
                restorePose(mob, data);
                if (mob instanceof net.minecraft.world.entity.animal.Animal animal)
                    zone.moddev.mc.industrialoverkill.gym.GymLegacyFitness.clear(animal);
                return mob;
            }
            return null;
        } catch (RuntimeException invalid) {
            return null; // Keep undecodable data in its trap for later recovery.
        }
    }
    /** Also used when an existing client preview receives updated entity NBT. */
    public static void restorePose(Mob mob, CompoundTag data) {
        if (mob instanceof Panda panda && data.contains("industrial_overkill:panda_sitting"))
            panda.sit(data.getBoolean("industrial_overkill:panda_sitting"));
    }
    public static boolean uuidAvailable(Mob mob, ServerLevel level) {
        for (var dimension : level.getServer().getAllLevels())
            if (dimension.getEntity(mob.getUUID()) != null)
                return false;
        return true;
    }
}

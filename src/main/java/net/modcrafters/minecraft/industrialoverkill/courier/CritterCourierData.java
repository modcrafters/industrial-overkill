package net.modcrafters.minecraft.industrialoverkill.courier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.machine.feature.PortableData;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** A trap owns one serialized mob, never a second live copy in the world. */
public final class CritterCourierData {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
        IndustrialOverkill.MOD_ID, "critter_courier");

    private CritterCourierData() {
    }
    public static boolean capturable(Mob mob) {
        return mob.isAlive() && !mob.isPassenger() && !mob.isVehicle()
            && !(mob instanceof EnderDragon) && !(mob instanceof WitherBoss);
    }
    public static EntitySnapshot snapshot(ItemStack stack) {
        return stack.is(MachineContent.item(ID))
            ? PortableData.read(stack, "courier", "occupant", EntitySnapshot.CODEC, EntitySnapshot.EMPTY)
            : EntitySnapshot.EMPTY;
    }
    public static CompoundTag read(ItemStack stack) {
        return snapshot(stack).data();
    }
    public static boolean occupied(ItemStack stack) {
        return !snapshot(stack).isEmpty();
    }
    public static CompoundTag save(Mob mob) {
        return EntitySnapshot.capture(mob, data -> {
            // Vanilla panda NBT keeps genes but omits the resting pose.
            if (mob instanceof Panda panda)
                data.putBoolean("industrial_overkill:panda_sitting", panda.isSitting());
            // A lead stays in the world after transport.
            data.remove("leash");
            data.remove("Leash");
        }).data();
    }
    public static void write(ItemStack stack, CompoundTag data) {
        PortableData.write(stack, "courier", "occupant", EntitySnapshot.CODEC, new EntitySnapshot(data));
    }
    public static ItemStack stack(CompoundTag data) {
        var stack = new ItemStack(MachineContent.item(ID));
        write(stack, data);
        return stack;
    }
    public static Component name(CompoundTag data) {
        return new EntitySnapshot(data).type().map(EntityType::getDescription)
            .orElse(Component.translatable("courier.unknown"));
    }
    public static Mob load(CompoundTag data, Level level) {
        return load(new EntitySnapshot(data), level);
    }
    public static Mob load(EntitySnapshot snapshot, Level level) {
        var mob = snapshot.createDetached(level, Mob.class).filter(CritterCourierData::capturable).orElse(null);
        if (mob != null) {
            restorePose(mob, snapshot.data());
            if (mob instanceof net.minecraft.world.entity.animal.Animal animal)
                net.modcrafters.minecraft.industrialoverkill.gym.GymLegacyFitness.clear(animal);
        }
        return mob;
    }
    /** Also used when an existing client preview receives updated entity NBT. */
    public static void restorePose(Mob mob, CompoundTag data) {
        if (mob instanceof Panda panda && data.contains("industrial_overkill:panda_sitting"))
            panda.sit(data.getBoolean("industrial_overkill:panda_sitting"));
    }
}

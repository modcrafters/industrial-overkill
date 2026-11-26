package net.modcrafters.minecraft.industrialoverkill.client;

import java.lang.ref.WeakReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

final class AgriDroneRotorSound extends AbstractTickableSoundInstance {
    private static final double RANGE = 16;
    private final WeakReference<DefinedMachineEntity> owner;
    private final AgriDroneFlight flight;
    private final Vec3 dock;

    private AgriDroneRotorSound(DefinedMachineEntity machine, AgriDroneFlight flight, Vec3 dock) {
        super(IndustrialOverkill.AGRI_DRONE_ROTOR.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        owner = new WeakReference<>(machine);
        this.flight = flight;
        this.dock = dock;
        looping = true;
        attenuation = SoundInstance.Attenuation.LINEAR;
    }

    static AgriDroneRotorSound playIfAudible(DefinedMachineEntity machine, AgriDroneFlight flight,
        AgriDroneRotorSound current, double time) {
        var client = Minecraft.getInstance();
        var sounds = client.getSoundManager();
        if (current != null && !current.isStopped() && sounds.isActive(current)) return current;
        float speed = (float) flight.rotorSpeedAt(time);
        if (speed <= 0 || client.options.getSoundSourceVolume(SoundSource.BLOCKS) == 0
            || client.options.getSoundSourceVolume(SoundSource.MASTER) == 0) return null;
        var dock = AgriDroneFlight.dockPoint(machine.getBlockPos());
        var position = flight.bodyPositionAt(time, dock);
        if (!withinRange(client, position)) return null;
        var sound = new AgriDroneRotorSound(machine, flight, dock);
        sound.update(position, speed);
        sounds.play(sound);
        return sound;
    }

    private static boolean withinRange(Minecraft client, Vec3 position) {
        return client.gameRenderer.getMainCamera().getPosition().distanceToSqr(position) < RANGE * RANGE;
    }

    @Override
    public void tick() {
        var client = Minecraft.getInstance();
        var machine = owner.get();
        if (machine == null || machine.isRemoved() || client.level == null || machine.getLevel() != client.level) {
            stop();
            return;
        }
        double time = client.level.getGameTime();
        float speed = (float) flight.rotorSpeedAt(time);
        var position = flight.bodyPositionAt(time, dock);
        if (speed <= 0 || !withinRange(client, position)) stop();
        else update(position, speed);
    }

    private void update(Vec3 position, float speed) {
        x = position.x;
        y = position.y;
        z = position.z;
        pitch = .52f + .58f * speed;
        volume = .23f * speed;
    }
}

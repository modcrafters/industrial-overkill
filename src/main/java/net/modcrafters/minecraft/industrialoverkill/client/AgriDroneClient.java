package net.modcrafters.minecraft.industrialoverkill.client;

import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriBrainProcessor;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** One flight per client-side controller, sampled by both its renderer and its positional sound. */
public final class AgriDroneClient {
    private static final WeakHashMap<DefinedMachineEntity, AgriDroneClient> DRONES = new WeakHashMap<>();
    final AgriDroneFlight flight;
    private AgriDroneRotorSound hum;
    private long lastUpdate;
    Direction rowDirection;
    Vec3 heading;
    BlockPos scanSoil;
    AgriBrainProcessor.Phase phase;
    long phaseStart;

    private AgriDroneClient(AgriDroneFlight flight) { this.flight = flight; }

    static AgriDroneClient get(DefinedMachineEntity machine) { return DRONES.get(machine); }

    public static void tick(DefinedMachineEntity machine) {
        var drone = sample(machine);
        drone.hum = AgriDroneRotorSound.playIfAudible(machine, drone.flight, drone.hum, drone.lastUpdate);
    }

    static AgriDroneClient sample(DefinedMachineEntity machine) {
        long time = machine.getLevel().getGameTime();
        var drone = DRONES.get(machine);
        if (drone != null && drone.lastUpdate == time) return drone;
        var processor = AgriBrainProcessor.from(machine);
        var soil = processor.droneSoil();
        var dock = AgriDroneFlight.dockPoint(machine.getBlockPos());
        var hover = soil == null ? dock : AgriDroneFlight.hoverPoint(soil);
        double progress = soil == null ? 0 : processor.returnProgress();
        var target = progress > 0 ? AgriDroneFlight.hoverPoint(processor.firstSoil()) : hover;
        var workPosition = hover.lerp(target, progress);
        if (drone == null) {
            drone = new AgriDroneClient(soil == null
                ? AgriDroneFlight.parked(dock, time) : new AgriDroneFlight(workPosition, time));
            DRONES.put(machine, drone);
        }
        drone.lastUpdate = time;
        var flight = drone.flight;
        boolean inField = soil != null && flight.readyForWork(workPosition, time);
        if (soil == null) flight.landOn(dock, time);
        else if (inField) {
            if (progress > 0) flight.returnTo(target, time, progress, processor.returnGameTicksLeft());
            else flight.aimAt(target, time, processor.travelGameTicks());
        }
        drone.rowDirection = soil == null ? machine.facing() : processor.scanDirection();
        drone.heading = progress > 0 ? target.subtract(hover) : Vec3.atLowerCornerOf(drone.rowDirection.getNormal());
        var phase = processor.phase();
        var scanSoil = inField && phase != AgriBrainProcessor.Phase.TRAVEL && phase != AgriBrainProcessor.Phase.RETURN
            ? soil : null;
        if (phase != drone.phase || !java.util.Objects.equals(scanSoil, drone.scanSoil)) drone.phaseStart = time;
        drone.phase = phase;
        drone.scanSoil = scanSoil;
        return drone;
    }
}

package net.modcrafters.minecraft.industrialoverkill.client;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** A short flight between synchronized work targets, sampled in fractional game ticks. */
public final class AgriDroneFlight {
    private enum Route { FIELD, LANDING, TAKEOFF }

    private static final float TURN_DEGREES_PER_TICK = 30;
    private static final double MAX_LEAN_SLOPE = Math.tan(Math.toRadians(8));
    private static final double LEAN_TRANSITION_TICKS = 3;
    private static final double SPIN_UP_TICKS = 8;
    private static final double COAST_TICKS = 16;
    private double travelTicks = 6;
    private Vec3 origin;
    private Vec3 destination;
    private double departure;
    private double returnSample;
    private Route route = Route.FIELD;
    private double verticalTicks;
    private double rotorStart, rotorAngle, rampTicks;
    private double startSpeed = 1, endSpeed = 1;
    private Vec3 initialLean = Vec3.ZERO;
    private double leanStart;
    private float yawDegrees;
    private double lastYawTime = Double.NaN;
    private long lastEmission = Long.MIN_VALUE;

    public AgriDroneFlight(Vec3 firstTarget, double time) {
        origin = destination = firstTarget;
        departure = time;
        rotorStart = time;
    }

    public static AgriDroneFlight parked(Vec3 dock, double time) {
        var flight = new AgriDroneFlight(dock, time);
        flight.route = Route.LANDING;
        flight.travelTicks = 0;
        flight.startSpeed = flight.endSpeed = 0;
        return flight;
    }

    /** Flight height is measured from the field plane, including over irrigation channels. */
    public static Vec3 hoverPoint(BlockPos soil) {
        return Vec3.atBottomCenterOf(soil).add(0, 2.72, 0);
    }

    /** Rest the lowest part of the drone (the scanner lens) on the casing. */
    public static Vec3 dockPoint(BlockPos controller) {
        return Vec3.atBottomCenterOf(controller).add(0, 1 + 2.75 / 16, 0);
    }

    public Vec3 positionAt(double time) {
        if (time < departure) return origin;
        if (time >= departure + travelTicks) return destination;
        double elapsed = time - departure;
        if (route == Route.FIELD) {
            double t = Math.clamp(elapsed / travelTicks, 0.0, 1.0);
            return origin.lerp(destination, returnSample > 0 ? t : smoothStep(t));
        }
        // Clear the roof vertically on takeoff and descend only once centered above it on landing.
        double horizontalTicks = travelTicks - verticalTicks;
        double horizontal = segmentProgress(route == Route.TAKEOFF ? elapsed - verticalTicks : elapsed, horizontalTicks);
        double vertical = segmentProgress(route == Route.TAKEOFF ? elapsed : elapsed - horizontalTicks, verticalTicks);
        return new Vec3(Mth.lerp(horizontal, origin.x, destination.x), Mth.lerp(vertical, origin.y, destination.y),
            Mth.lerp(horizontal, origin.z, destination.z));
    }

    public void landOn(Vec3 dock, double time) {
        if (route == Route.LANDING) return;
        beginDockFlight(Route.LANDING, dock, time);
        rampRotors(0, departure + travelTicks, COAST_TICKS);
    }

    /** Resume the field route once the drone has lifted off and flown back to the field. */
    public boolean readyForWork(Vec3 workPosition, double time) {
        if (route == Route.FIELD) return true;
        if (route == Route.LANDING) {
            beginDockFlight(Route.TAKEOFF, workPosition, time);
            rampRotors(1, time, SPIN_UP_TICKS);
            departure += rampTicks;
        }
        if (time < departure + travelTicks) return false;
        route = Route.FIELD;
        origin = destination;
        departure = time;
        return true;
    }

    private void beginDockFlight(Route next, Vec3 target, double time) {
        startLean(time);
        origin = positionAt(time);
        destination = target;
        route = next;
        returnSample = 0;
        departure = time;
        var displacement = target.subtract(origin);
        verticalTicks = Math.abs(displacement.y) * 10;
        travelTicks = verticalTicks + displacement.horizontalDistance() * 3;
    }

    public float rotorDegreesAt(double time) {
        double elapsed = time - rotorStart;
        double ramp = Math.clamp(elapsed, 0, rampTicks);
        // Area under the linear speed ramp, measured in full-speed ticks.
        double fullSpeedTicks = elapsed < 0 ? elapsed * startSpeed
            : ramp * (startSpeed + rotorSpeedAt(time)) / 2 + (elapsed - ramp) * endSpeed;
        return (float) ((rotorAngle + fullSpeedTicks * 45) % 360);
    }

    /** Fraction of cruising speed shared by the rotor animation and its motor sound. */
    public double rotorSpeedAt(double time) {
        if (time < rotorStart) return startSpeed;
        if (rampTicks == 0) return endSpeed;
        return Mth.lerp(Math.min(1, (time - rotorStart) / rampTicks), startSpeed, endSpeed);
    }

    Vec3 bodyPositionAt(double time, Vec3 dock) {
        var position = positionAt(time);
        double bob = Math.clamp((position.y - dock.y) / .5, 0.0, 1.0);
        double phase = (time % 120) * Math.PI / 20;
        return position.add(bob * .06 * Math.cos(phase), bob * .045 * Math.sin(2 * phase),
            bob * .06 * Math.sin(phase));
    }

    private void rampRotors(double speed, double time, double fullRampTicks) {
        rotorAngle = rotorDegreesAt(time);
        startSpeed = rotorSpeedAt(time);
        endSpeed = speed;
        rotorStart = time;
        rampTicks = Math.abs(endSpeed - startSpeed) * fullRampTicks;
    }

    /** Follow the current leg in flight, then turn toward the working row while hovering. */
    public float yawDegreesAt(double time, Vec3 hoverHeading) {
        var leg = destination.subtract(origin);
        var heading = (route == Route.LANDING || time < departure + travelTicks) && leg.horizontalDistanceSqr() > 1.0e-8
            ? leg : hoverHeading;
        // The model's nose points north (-Z) before its Y rotation.
        float targetYaw = (float) Math.toDegrees(Math.atan2(-heading.x, -heading.z));
        yawDegrees = Double.isNaN(lastYawTime) ? targetYaw : Mth.approachDegrees(yawDegrees, targetYaw,
            (float) Math.max(0, time - lastYawTime) * TURN_DEGREES_PER_TICK);
        lastYawTime = time;
        return yawDegrees;
    }

    public Quaternionf orientationAt(double time, Vec3 hoverHeading) {
        var lean = leanAt(time).scale(MAX_LEAN_SLOPE);
        // Tip the body's up axis toward travel in world space, even while the nose is turning.
        return new Quaternionf().rotationTo(0, 1, 0, (float) lean.x, 1, (float) lean.z)
            .rotateY((float) Math.toRadians(yawDegreesAt(time, hoverHeading)));
    }

    private Vec3 leanAt(double time) {
        double remaining = departure + travelTicks - (route == Route.LANDING ? verticalTicks : 0) - time;
        if (remaining <= 0) return Vec3.ZERO;
        var direction = new Vec3(destination.x - origin.x, 0, destination.z - origin.z).normalize();
        double start = route == Route.TAKEOFF ? departure + verticalTicks : leanStart;
        return initialLean.lerp(direction, smoothStep((time - start) / LEAN_TRANSITION_TICKS))
            .scale(smoothStep(remaining / LEAN_TRANSITION_TICKS));
    }

    private static double smoothStep(double fraction) {
        double t = Math.clamp(fraction, 0.0, 1.0);
        return t * t * (3 - 2 * t);
    }

    private static double segmentProgress(double elapsed, double duration) {
        return duration > 0 ? smoothStep(elapsed / duration) : 1;
    }

    private void startLean(double time) {
        initialLean = leanAt(time);
        leanStart = time;
    }

    public void aimAt(Vec3 target, double time, double ticks) {
        if (returnSample == 0 && destination.equals(target)) return;
        startLean(time);
        origin = positionAt(time);
        destination = target;
        departure = time;
        travelTicks = ticks;
        returnSample = 0;
    }

    /** Continue to fly between network snapshots. A speed change adjusts the remaining flight. */
    public void returnTo(Vec3 first, double time, double progress, double ticksLeft) {
        if (returnSample == progress && destination.equals(first)) return;
        // Consecutive return snapshots describe one continuous diagonal leg.
        if (returnSample == 0 || !destination.equals(first)) startLean(time);
        origin = positionAt(time);
        destination = first;
        departure = time;
        travelTicks = Math.max(1, ticksLeft);
        returnSample = progress;
    }

    public boolean beginEmission(long tick) {
        if (tick == lastEmission) return false;
        lastEmission = tick;
        return true;
    }
}

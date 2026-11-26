package net.modcrafters.minecraft.industrialoverkill.waste;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.entity.EntityTypeTest;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.feature.WorkingArea;

public final class HonestSewerProcessor extends StoredProcessor implements WorkingArea.Provider {
    private static final int BATCH_PROGRESS = 600;
    private static final int SEWAGE_PER_ANIMAL = 25;
    private static final int POWERED_SPEED = 6;
    private static final int ENERGY_PER_TICK = 2;
    private final ProcessorState.Key<Integer> progress = variables.integer("progress", 0, BATCH_PROGRESS, 0).define();
    private final ProcessorState.Key<Boolean> powered = variables.bool("powered", false).visual().define();

    @Override
    protected int dataVersion() { return 1; }

    @Override
    protected CompoundTag migrateState(int version, CompoundTag data) {
        // Turn the old cycle's elapsed time and paid ticks into progress toward the next batch.
        int elapsed = Math.clamp(data.getInt("elapsed"), 0, BATCH_PROGRESS);
        int paidTicks = Math.clamp(data.getInt("powered_ticks"), 0, elapsed);
        data.putInt("progress", Math.min(BATCH_PROGRESS, elapsed + (POWERED_SPEED - 1) * paidTicks));
        return data;
    }

    @Override
    public WorkingArea baseWorkingArea() { return new WorkingArea(-2, 0, -2, 2, 2, 2); }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine context) { return true; }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        if (!(context.level() instanceof ServerLevel level)) return ProcessState.IDLE;
        int previous = state.get(progress);
        int remaining = BATCH_PROGRESS - previous;
        int passiveStep = Math.min(deltaTicks, remaining);
        var resources = context.resources();
        var energy = resources.energy("energy");
        var output = resources.fluids("sewage");
        int boostableTicks = Math.min(deltaTicks, Math.ceilDiv(remaining, POWERED_SPEED));
        int affordableTicks = (int) Math.min(boostableTicks, energy.stored() / ENERGY_PER_TICK);
        boolean canBoost = affordableTicks > 0 && output.fits(List.of(FarmSupplies.fluid(FarmSupplies.SEWAGE, 1)));
        boolean mayFinish = passiveStep + (canBoost ? (POWERED_SPEED - 1) * affordableTicks : 0) >= remaining;
        int animals = mayFinish || canBoost ? countAnimals(level, context, mayFinish ? Integer.MAX_VALUE : 1) : 0;
        int step = passiveStep;
        int paidTicks = 0;

        if (animals > 0) {
            try (var transaction = resources.begin()) {
                if (canBoost) {
                    paidTicks = (int) (energy.consume((long) affordableTicks * ENERGY_PER_TICK, transaction)
                        / ENERGY_PER_TICK);
                    step += (POWERED_SPEED - 1) * paidTicks;
                }
                if (step >= remaining && output.insert(FarmSupplies.fluid(FarmSupplies.SEWAGE,
                    (int) Math.min(Integer.MAX_VALUE, (long) animals * SEWAGE_PER_ANIMAL)), transaction) == 0) {
                    state.set(progress, previous + passiveStep);
                    state.set(powered, false);
                    return ProcessState.BLOCKED;
                }
                transaction.commit();
            }
        }
        state.set(progress, step >= remaining ? 0 : previous + step);
        state.set(powered, paidTicks > 0);
        return ProcessState.RUNNING;
    }

    private int countAnimals(ServerLevel level, ProcessorMachine context, int limit) {
        var bounds = workingArea().bounds(context.position(),
            context.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        var animals = new ArrayList<Animal>();
        level.getEntities(EntityTypeTest.forClass(Animal.class), bounds, Animal::isAlive, animals, limit);
        return animals.size();
    }
}

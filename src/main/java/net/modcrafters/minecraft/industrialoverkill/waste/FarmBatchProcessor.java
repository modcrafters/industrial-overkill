package net.modcrafters.minecraft.industrialoverkill.waste;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.machine.feature.ProcessorAction;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

abstract class FarmBatchProcessor extends StoredProcessor {
    protected static final int ENERGY_PER_TICK = 5;
    protected final ProcessorState.Key<Integer> mode;
    private final ProcessorState.Key<Integer> batch;
    private final ProcessorState.Key<Integer> elapsed = variables.integer("elapsed", 0, 800, 0).define();
    private final Map<ResourceLocation, ProcessorAction> modeActions;

    FarmBatchProcessor(int batchCount, String... actions) {
        mode = variables.integer("mode", 0, actions.length - 1, 0).synced().define();
        batch = variables.integer("batch", -1, batchCount - 1, -1).define();
        var controls = new LinkedHashMap<ResourceLocation, ProcessorAction>();
        for (int index = 0; index < actions.length; index++) {
            int selection = index;
            controls.put(FarmSupplies.id(actions[index]), new ProcessorAction((state, context) -> true,
                (state, context) -> {
                    state.set(mode, selection);
                    return ProcessorAction.Result.success();
                }));
        }
        modeActions = Map.copyOf(controls);
    }

    @Override
    public final Map<ResourceLocation, ProcessorAction> actions() { return modeActions; }

    protected final int batchProgress() { return variables.get(elapsed); }

    protected final int batchDuration() {
        int recipe = variables.get(batch);
        return recipe < 0 ? 0 : duration(recipe);
    }

    @Override
    public final boolean canStart(ProcessorState state, ProcessorMachine context) {
        return chooseBatch((DefinedMachineEntity) context, state.get(mode)) >= 0;
    }

    @Override
    public final ProcessState process(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        var host = (DefinedMachineEntity) context;
        int recipe = state.get(batch);
        if (recipe >= 0 && !hasInputs(host, recipe)) {
            clearBatch(state);
            recipe = -1;
        }
        if (recipe < 0) {
            recipe = chooseBatch(host, state.get(mode));
            if (recipe < 0) return ProcessState.IDLE;
            if (!hasOutputSpace(host, recipe)) return ProcessState.BLOCKED;
            state.set(batch, recipe);
            startBatch(host, recipe);
        }
        int progress = state.get(elapsed);
        int duration = duration(recipe);
        if (progress < duration) {
            int step = Math.min(deltaTicks, duration - progress);
            if (!host.energy.consume((long) ENERGY_PER_TICK * step)) return ProcessState.HELD;
            progress += step;
            state.set(elapsed, progress);
        }
        if (progress < duration) return ProcessState.RUNNING;
        if (!finishBatch(host, recipe)) return ProcessState.BLOCKED;
        clearBatch(state);
        return ProcessState.RUNNING;
    }

    private void clearBatch(ProcessorState state) {
        state.set(batch, -1);
        state.set(elapsed, 0);
    }

    protected abstract int chooseBatch(DefinedMachineEntity host, int mode);
    protected abstract boolean hasInputs(DefinedMachineEntity host, int recipe);
    protected abstract boolean hasOutputSpace(DefinedMachineEntity host, int recipe);
    protected abstract int duration(int recipe);
    protected void startBatch(DefinedMachineEntity host, int recipe) {}
    protected abstract boolean finishBatch(DefinedMachineEntity host, int recipe);
}

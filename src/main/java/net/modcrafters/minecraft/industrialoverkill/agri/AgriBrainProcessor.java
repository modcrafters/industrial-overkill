package net.modcrafters.minecraft.industrialoverkill.agri;

import java.util.Map;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.modcrafters.minecraft.industrialoverkill.waste.FarmSupplies;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorAction;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.feature.WorkingArea;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Visits each crop position and commits the planned farm work after hovering. */
public final class AgriBrainProcessor extends StoredProcessor implements WorkingArea.Provider {
    public static final int TRAVEL_TICKS = 6;
    public static final int PHASE_TICKS = 5;
    public static final int SCAN_FE_PER_TICK = 5;
    public static final int PLANT_FE = 100;
    public static final int TILL_FE = 200;
    public static final int HARVEST_FE = 100;
    public static final int FERTILIZE_FE = 100;
    private static final int RETURN_TICKS_PER_DIAGONAL_BLOCK = 20;

    public enum Phase {
        TRAVEL, CHECK, FERTILIZE, HARVEST, PLANT, RETURN;

        private static final Phase[] VALUES = values();

        int bit() { return 1 << ordinal(); }
    }

    private AgriSoilRetention soilRetention;
    private final ProcessorState.Key<Integer> scanRadius = variables.integer("scan_radius", 0, 10, 0).synced().define();
    private final ProcessorState.Key<Integer> scanIndex = variables.integer("scan_index", 0, 440, 0).synced().define();
    private final ProcessorState.Key<Integer> returnTicks = variables.integer(
        "return_ticks", 0, 20 * RETURN_TICKS_PER_DIAGONAL_BLOCK, 0).synced().define();
    private final ProcessorState.Key<Integer> flightGearboxes = variables.integer(
        "return_gearboxes", 0, 8, 0).synced().define();
    private final ProcessorState.Key<Integer> phase = variables.integer("scan_phase", 0, Phase.RETURN.ordinal(), 0).synced().define();
    private final ProcessorState.Key<Integer> phaseTicks = variables.integer("phase_ticks", 0, TRAVEL_TICKS, 0).define();
    private final ProcessorState.Key<Integer> plannedWork = variables.integer("planned_work", 0,
        Phase.FERTILIZE.bit() | Phase.HARVEST.bit() | Phase.PLANT.bit(), 0).define();
    private final ProcessorState.Key<Boolean> hoeWearDue = variables.bool("hoe_wear_due", false).define();
    private final ProcessorState.Key<Integer> plantingMode = variables.integer("planting_mode", 0, 2, 0).synced().define();
    private final ProcessorState.Key<Integer> nextSeed = variables.integer("next_seed", 0, 3, 0).define();
    private final ProcessorState.Key<Integer> fertilizingMode = variables.integer("fertilizing_mode", 0, 3, 0).synced().define();
    private final Map<ResourceLocation, ProcessorAction> modeActions = Map.of(
        FarmSupplies.id("planting_first_found"), plantingAction(0),
        FarmSupplies.id("planting_round_robin"), plantingAction(1),
        FarmSupplies.id("planting_rows"), plantingAction(2),
        FarmSupplies.id("fertilizing_off"), fertilizingAction(AgriFertilizing.OFF),
        FarmSupplies.id("fertilizing_smart"), fertilizingAction(AgriFertilizing.SMART),
        FarmSupplies.id("fertilizing_finish"), fertilizingAction(AgriFertilizing.FINISH_ONLY),
        FarmSupplies.id("fertilizing_pellets"), fertilizingAction(AgriFertilizing.PELLETS_ONLY));

    private ProcessorAction plantingAction(int mode) {
        return new ProcessorAction((state, context) -> true, (state, context) -> {
            selectPlantingMode(mode);
            return ProcessorAction.Result.success();
        });
    }

    private ProcessorAction fertilizingAction(int mode) {
        return new ProcessorAction((state, context) -> true, (state, context) -> {
            state.set(fertilizingMode, mode);
            return ProcessorAction.Result.success();
        });
    }

    @Override
    public Map<ResourceLocation, ProcessorAction> actions() { return modeActions; }

    public static AgriBrainProcessor from(Object host) {
        return zone.moddev.mc.lib.machine.feature.Processors.find(host, "agri", AgriBrainProcessor.class).orElseThrow();
    }

    public int plantingMode() { return variables.get(plantingMode); }

    public void cyclePlantingMode() {
        selectPlantingMode((plantingMode() + 1) % 3);
    }

    private void selectPlantingMode(int mode) {
        variables.update(() -> {
            variables.set(plantingMode, mode);
            variables.set(nextSeed, 0);
        });
    }

    /** Next non-empty socket in round-robin mode; sampled live for the slot guide. */
    public int nextSeedSlot() {
        return machine == null ? -1 : firstSeed(variables.get(nextSeed));
    }

    private int firstSeed(int start) {
        var seeds = machine.inventoryContents("seeds");
        for (int offset = 0; offset < 4; offset++) {
            int slot = (start + offset) % 4;
            if (AgriPlanting.accepts(seeds.get(slot))) return slot;
        }
        return -1;
    }

    private int seedSlot(WorkingArea area) {
        return switch (plantingMode()) {
            case 1 -> nextSeedSlot();
            case 2 -> area.row(variables.get(scanIndex)) % 4;
            default -> firstSeed(0);
        };
    }

    @Override
    public WorkingArea baseWorkingArea() {
        return new WorkingArea(-1, 0, -3, 1, 0, -1);
    }

    /** The scanner's soil block, or its departure block during the return flight. */
    public BlockPos droneSoil() {
        int radius = variables.get(scanRadius);
        if (machine == null || !processing() || radius == 0) return null;
        return soilPosition(baseWorkingArea().expandHorizontal(radius - 1), variables.get(scanIndex));
    }

    public BlockPos firstSoil() {
        return soilPosition(baseWorkingArea().expandHorizontal(variables.get(scanRadius) - 1), 0);
    }

    /** Horizontal travel along the active row of the snake. */
    public Direction scanDirection() {
        var right = machine.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise();
        int width = 2 * variables.get(scanRadius) + 1;
        return (variables.get(scanIndex) / width) % 2 == 0 ? right : right.getOpposite();
    }

    private static int returnDuration(int radius) {
        return 2 * radius * RETURN_TICKS_PER_DIAGONAL_BLOCK;
    }

    public double returnProgress() {
        int ticks = variables.get(returnTicks);
        return ticks == 0 ? 0 : ticks / (double) returnDuration(variables.get(scanRadius));
    }

    public double returnGameTicksLeft() {
        int remaining = returnDuration(variables.get(scanRadius)) - variables.get(returnTicks);
        return gameTicksFor(remaining);
    }

    public Phase phase() { return Phase.VALUES[variables.get(phase)]; }

    public double travelGameTicks() { return gameTicksFor(TRAVEL_TICKS); }

    public double phaseGameTicks() { return gameTicksFor(PHASE_TICKS); }

    private double gameTicksFor(int workTicks) {
        return workTicks / (1 + variables.get(flightGearboxes) / 8.0);
    }

    private BlockPos soilPosition(WorkingArea area, int scan) {
        int index = scan % area.size();
        var front = machine.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        return area.snakeBlockAt(machine.position(), front, index).below();
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine context) { return true; }

    @Override
    protected int dataVersion() { return 1; }

    @Override
    protected CompoundTag migrateState(int version, CompoundTag data) {
        if (data.getInt("return_ticks") > 0) data.putInt("scan_phase", Phase.RETURN.ordinal());
        data.remove("scan_ticks_remaining");
        return data;
    }

    @Override
    public void reset() {
        if (soilRetention != null) {
            soilRetention.remove();
            soilRetention = null;
        }
    }

    private void updateSoilRetention(DefinedMachineEntity host, WorkingArea area) {
        var bounds = area.bounds(host.getBlockPos().below(), host.facing());
        if (soilRetention != null && soilRetention.bounds.equals(bounds)) return;
        reset();
        soilRetention = new AgriSoilRetention(host.getLevel(), bounds,
            () -> processing() && !host.isRemoved());
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        var host = (DefinedMachineEntity) context;
        if (!(host.getLevel() instanceof ServerLevel level)) return ProcessState.IDLE;
        var energy = host.energy;
        if (!energy.consume((long) SCAN_FE_PER_TICK * deltaTicks)) return ProcessState.HELD;
        int radius = 1 + rangeUpgrades();
        var area = workingArea();
        updateSoilRetention(host, area);
        if (state.get(scanRadius) != radius) {
            state.set(scanRadius, radius);
            state.set(scanIndex, 0);
            state.set(returnTicks, 0);
            takeGearboxSnapshot(host, state);
            beginPhase(state, Phase.TRAVEL);
        }
        var current = phase();
        if (current == Phase.RETURN) {
            int elapsed = state.get(returnTicks);
            if (elapsed < returnDuration(radius)) {
                state.set(returnTicks, Math.min(returnDuration(radius), elapsed + deltaTicks));
            } else {
                state.set(returnTicks, 0);
                state.set(scanIndex, 0);
                // The diagonal crossing already brought the drone to the first plot.
                takeGearboxSnapshot(host, state);
                beginPhase(state, Phase.CHECK);
            }
            return ProcessState.RUNNING;
        }
        int elapsed = state.get(phaseTicks) + deltaTicks;
        int duration = current == Phase.TRAVEL ? TRAVEL_TICKS : PHASE_TICKS;
        if (elapsed < duration) {
            state.set(phaseTicks, elapsed);
            return ProcessState.RUNNING;
        }
        if (current == Phase.TRAVEL) {
            beginPhase(state, Phase.CHECK);
            return ProcessState.RUNNING;
        }

        var soil = soilPosition(area, state.get(scanIndex));
        if (current == Phase.CHECK) state.set(plannedWork, planVisit(host, level, area, soil));
        int plan = state.get(plannedWork);
        for (int next = current.ordinal() + 1; next <= Phase.PLANT.ordinal(); next++) {
            var action = Phase.VALUES[next];
            if ((plan & action.bit()) == 0) continue;
            beginPhase(state, action);
            return ProcessState.RUNNING;
        }
        finishVisit(host, level, state, area, soil, plan);
        takeGearboxSnapshot(host, state);
        if (state.get(scanIndex) == area.size() - 1) beginPhase(state, Phase.RETURN);
        else {
            state.set(scanIndex, state.get(scanIndex) + 1);
            beginPhase(state, Phase.TRAVEL);
        }
        return ProcessState.RUNNING;
    }

    private void beginPhase(ProcessorState state, Phase next) {
        state.set(phase, next.ordinal());
        state.set(phaseTicks, 0);
    }

    private void takeGearboxSnapshot(DefinedMachineEntity host, ProcessorState state) {
        var gears = host.screenInventory("mmdlib_gearbox").getStackInSlot(0);
        state.set(flightGearboxes, gears.getItem() instanceof zone.moddev.mc.lib.common.item.GearboxUpgradeItem
            ? Math.min(8, gears.getCount()) : 0);
    }

    private int planVisit(DefinedMachineEntity host, ServerLevel level, WorkingArea area, BlockPos soil) {
        if (!level.hasChunkAt(soil)) return 0;
        var pos = soil.above();
        var crop = level.getBlockState(pos);
        var dose = AgriFertilizing.choose(host, level, pos, crop, variables.get(fertilizingMode));
        boolean harvest = AgriHarvesting.mature(crop) || AgriFertilizing.canFinish(crop, dose);
        int plan = dose == null ? 0 : Phase.FERTILIZE.bit();
        if (harvest) plan |= Phase.HARVEST.bit();
        var ground = level.getBlockState(soil);
        boolean plantable = ground.is(Blocks.FARMLAND) || (ground.is(Blocks.DIRT) || ground.is(Blocks.GRASS_BLOCK))
            && !host.items.inventory("hoe").getStackInSlot(0).isEmpty();
        boolean cropCanRefillSeeds = crop.getBlock() instanceof CropBlock && !(crop.getBlock() instanceof TorchflowerCropBlock);
        if (plantable && (crop.isAir() || harvest) && (hasSeed(host, seedSlot(area))
            || harvest && cropCanRefillSeeds))
            plan |= Phase.PLANT.bit();
        return plan;
    }

    private static boolean hasSeed(DefinedMachineEntity host, int slot) {
        return slot >= 0 && AgriPlanting.accepts(host.items.inventory("seeds").getStackInSlot(slot));
    }

    private void finishVisit(DefinedMachineEntity host, ServerLevel level, ProcessorState state, WorkingArea area,
        BlockPos soil, int plan) {
        if (!level.hasChunkAt(soil)) return;
        var pos = soil.above();
        var energy = host.energy;
        int strategy = state.get(fertilizingMode);
        boolean prepareHarvest = (plan & Phase.HARVEST.bit()) != 0;
        if ((plan & Phase.FERTILIZE.bit()) != 0) {
            var crop = level.getBlockState(pos);
            var dose = AgriFertilizing.choose(host, level, pos, crop, strategy);
            if (dose != null && (prepareHarvest || !AgriFertilizing.canFinish(crop, dose)))
                poweredAction(energy, FERTILIZE_FE, () -> AgriFertilizing.use(host, level, pos, dose));
        }
        if (prepareHarvest)
            poweredAction(energy, HARVEST_FE, () -> AgriHarvesting.harvest(host, pos));
        if (poweredAction(energy, TILL_FE, () -> AgriTilling.till(host, soil, state.get(hoeWearDue))))
            state.set(hoeWearDue, !state.get(hoeWearDue));
        AgriIrrigation.waterVisitedSoil(host, soil);
        if ((plan & Phase.PLANT.bit()) == 0) return;
        int slot = seedSlot(area);
        if (poweredAction(energy, PLANT_FE, () -> AgriPlanting.plant(host, soil, slot))) {
            if (plantingMode() == 1) state.set(nextSeed, (slot + 1) % 4);
            if (strategy == AgriFertilizing.SMART || strategy == AgriFertilizing.PELLETS_ONLY)
                poweredAction(energy, FERTILIZE_FE, () -> AgriFertilizing.use(host, level, pos, AgriFertilizing.Dose.PELLET));
        }
    }

    /** Fund world changes up front; a rejected action costs neither FE nor usage statistics. */
    private static boolean poweredAction(EnergyBuffer energy, int cost, BooleanSupplier action) {
        if (energy.stored() < cost) return false;
        var rollback = energy.checkpoint();
        if (!energy.consume(cost)) return false;
        boolean completed = false;
        try {
            completed = action.getAsBoolean();
            return completed;
        } finally {
            if (!completed) rollback.run();
        }
    }
}

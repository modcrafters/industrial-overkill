package net.modcrafters.minecraft.industrialoverkill.stew;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.Processors;
import zone.moddev.mc.lib.machine.feature.RecipeChain;
import zone.moddev.mc.lib.machine.feature.RecipeCycle;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;
import zone.moddev.mc.lib.machine.recipe.CyclePlan;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;

/** Rock Stew policy and save ownership. RecipeChain owns execution and claims. */
public final class RockStewProcessor extends StoredProcessor {
    public static final int SLOTS = 33; // Retain legacy storage/slot IDs; new paths are limited by RockStewRecipes.MAX_STEPS.
    public record Binding(ResourceLocation id, String kind, MachineRecipe recipe) {
        public static final Codec<Binding> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Binding::id),
            Codec.STRING.fieldOf("kind").forGetter(Binding::kind),
            MachineRecipe.CODEC.codec().fieldOf("recipe").forGetter(Binding::recipe)).apply(i, Binding::new));
        public ItemStack product() { return ((ItemProduct) recipe.outputs().get("output").getFirst()).stack(); }
    }
    public record Route(List<Binding> steps) {
        public static final Codec<Route> CODEC = Binding.CODEC.listOf(1, SLOTS).xmap(Route::new, Route::steps);
        public Route { steps = List.copyOf(steps); }
        public ResourceLocation source() { return steps.getFirst().id(); }
        public ItemStack destination() { return steps.getLast().product(); }
    }

    private final ProcessorState.Key<CompoundTag> saved = variables.value("chain_data", CompoundTag.CODEC,
        new CompoundTag(), CompoundTag::copy).define();
    private final ProcessorState.Key<Boolean> running = variables.bool("running", false).visual().define();
    private final ProcessorState.Key<Integer> progressLights = variables.integer("progress_lights", 0, 3, 0).visual().define();
    private final List<ProcessorState.Key<Integer>> stageProgressLights = java.util.stream.IntStream.rangeClosed(1, 5)
        .mapToObj(stage -> variables.integer("progress_lights_" + stage, 0, 7, 0).visual().define()).toList();
    // A recipe preview, independent of output stock and whether a cycle is currently running.
    private final ProcessorState.Key<String> displayProduct = variables.string("display_product", "").synced().define();
    // Preserve the original failed snapshot when an explicit UI action restarts its path.
    private final ProcessorState.Key<CompoundTag> recovery = variables.value("recovery_data", CompoundTag.CODEC,
        new CompoundTag(), CompoundTag::copy).define();
    private int[] stockModes = new int[0]; // None / One Run / Full Stack, independent of equal numeric targets.
    private RecipeChain chain;
    private Route route;
    private RecipeCycle legacy;
    private CompoundTag legacySource;
    private String fault = "";
    private boolean initialized;
    private long checkedEpoch = -1;

    public static RockStewProcessor from(Object host) {
        return Processors.find(host, "rock_stew", RockStewProcessor.class).orElse(null);
    }
    private DefinedMachineEntity owner() { return (DefinedMachineEntity) machine; }
    private zone.moddev.mc.lib.api.capabilities.ItemStorage output() { return owner().items.inventory("output"); }
    public RecipeChain chain() { return chain; }
    public Route route() { return route; }
    public Route displayRoute() { return route; }
    public int displaySize() {
        var shown = displayRoute();
        int count = Math.max(5, shown == null ? 0 : shown.steps().size());
        // Old outputs beyond a shorter new path must remain available for extraction.
        for (int i = count; i < output().getSlots(); i++)
            if (!output().getStackInSlot(i).isEmpty()) count = i + 1;
        return count;
    }
    public int stockMode(int index) {
        return index >= 0 && index < stockModes.length ? stockModes[index] : 0;
    }
    private int oneRun(int index) {
        return index + 1 < chain.size() ? chain.step(index + 1).inputCount() : chain.step(index).product().getCount();
    }
    private void applyStock(int index) {
        chain.bufferLimit(index, stockModes[index] == 1 ? oneRun(index) : chain.step(index).product().getMaxStackSize());
        chain.target(index, switch (stockModes[index]) {
            case 1 -> oneRun(index);
            case 2 -> chain.step(index).product().getMaxStackSize();
            default -> 0;
        });
    }
    private void restoreStock(CompoundTag data) {
        stockModes = new int[chain.size()];
        int[] modes = data.getIntArray("stock_modes");
        for (int i = 0; i < chain.size(); i++) {
            int target = chain.target(i);
            stockModes[i] = modes.length == chain.size() && modes[i] >= 0 && modes[i] <= 2 ? modes[i]
                : target == 0 ? 0 : target == chain.step(i).product().getMaxStackSize() ? 2 : 1;
            applyStock(i);
        }
    }
    public boolean outputBlocked(int index) {
        var shown = displayRoute();
        if (shown == null || index < 0 || index >= shown.steps().size()) return false;
        var product = shown.steps().get(index).product();
        var held = output().getStackInSlot(index);
        if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, product)) return true;
        return (long) held.getCount() + product.getCount()
            > Math.min(output().getSlotLimit(index), product.getMaxStackSize());
    }

    public boolean switching() { return chain != null && chain.draining(); }
    public String problem() { return fault; }
    @Override protected int dataVersion() { return 1; }
    @Override protected CompoundTag migrateState(int version, CompoundTag old) {
        if (!old.contains("chain_data") && (old.contains("cycle") || old.contains("resumeRecipe") || old.contains("resume_recipe"))) {
            var payload = new CompoundTag();
            payload.put("legacy", old.copy());
            old.put("chain_data", payload);
        }
        return old;
    }

    @Override public void restoreResources(HolderLookup.Provider registries) {
        variables.update(() -> restoreChain(registries));
    }

    private void restoreChain(HolderLookup.Provider registries) {
        reset();
        initialized = true;
        var data = variables.get(saved);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        try {
            mergeLegacyFluids(registries);
            if (data.contains("legacy")) {
                legacySource = data.getCompound("legacy").copy();
                restoreLegacy(registries);
            }
            if (data.contains("route")) {
                var loaded = Route.CODEC.parse(ops, data.get("route")).getOrThrow();
                route = new Route(loaded.steps().stream().map(step -> step.kind().equals("generate") ? step
                    : new Binding(step.id(), step.kind(), cycleRecipe(step.recipe()))).toList());
                chain = bind(route);
                var state = RecipeChain.State.CODEC.parse(ops, data.get("state")).getOrThrow();
                state = new RecipeChain.State(state.stages().stream().map(stage -> new RecipeChain.StageState(
                    stage.recipe(), stage.target(), new RecipeCycle.State(stage.cycle().plan().map(RockStewProcessor::consumedFluidsOnly),
                    stage.cycle().progress()))).toList(), state.paused(), state.draining(), state.first());
                if (!chain.restore(state))
                    fault = "stew.recovery";
                restoreStock(data);
            }
            // Upgrade previously queued selections to immediate replacement, preserving stored contents.
            if (fault.isEmpty() && data.contains("pending")) activate(Route.CODEC.parse(ops, data.get("pending")).getOrThrow());
            installClaims();
        } catch (RuntimeException failure) {
            // Keep the original serialized payload and all stock for recovery.
            fault = "stew.recovery";
        } finally {
            updateDisplayProduct();
            updateProgressLights();
        }
    }

    @Override public void reset() {
        variables.update(this::resetChain);
    }

    private void resetChain() {
        if (chain != null) chain.reset();
        if (legacy != null) legacy.reset();
        chain = null;
        clearProgressLights();
        stockModes = new int[0];
        legacy = null;
        route = null;
        updateDisplayProduct();
        legacySource = null;
        initialized = false;
        fault = "";
        checkedEpoch = -1;
    }

    private void initialize() {
        if (!initialized) restoreResources(machine.level().registryAccess());
    }

    private void installClaims() {
        output().pendingClaims(index -> chain != null && index < chain.size()
            && !paused() && executionStatus() != ProcessState.HELD && fault.isEmpty()
            ? chain.pendingTake(index) : 0);
    }

    private RecipeChain bind(Route selected) {
        var host = owner();
        var outputs = java.util.stream.IntStream.range(0, selected.steps().size())
            .mapToObj(i -> StorageGroups.items(output(), i)).toList();
        var steps = new ArrayList<RecipeChain.Step>();
        for (int i = 0; i < selected.steps().size(); i++) {
            var inventory = MachineInventory.builder().group("output", outputs.get(i))
                .group("energy", StorageGroups.energy(host.energy))
                .group("water", StorageGroups.fluids(host.fluids, 0))
                .group("lava", StorageGroups.fluids(host.fluids, 1));
            if (i > 0) inventory.group("input", outputs.get(i - 1));
            var step = selected.steps().get(i);
            steps.add(new RecipeChain.Step(step.id(), cycleRecipe(step.recipe()), inventory.build()));
        }
        return new RecipeChain(steps);
    }

    /** Source fluid thresholds are checked at admission, not held as extra cycle ingredients. */
    private static MachineRecipe cycleRecipe(MachineRecipe recipe) {
        var inputs = new java.util.LinkedHashMap<>(recipe.inputs());
        for (var group : List.of("water", "lava")) {
            var consumed = inputs.getOrDefault(group, List.of()).stream().filter(part -> part.consumed()).toList();
            if (consumed.isEmpty()) inputs.remove(group);
            else inputs.put(group, consumed);
        }
        return new MachineRecipe(inputs, recipe.outputs(), recipe.time());
    }

    private boolean sourceFluidsReady() {
        var water = owner().fluids.getFluidInTank(0);
        var lava = owner().fluids.getFluidInTank(1);
        return water.getFluid() == net.minecraft.world.level.material.Fluids.WATER
            && lava.getFluid() == net.minecraft.world.level.material.Fluids.LAVA
            && water.getAmount() >= RockStewRecipes.SOURCE_FLUID_MINIMUM
            && lava.getAmount() >= RockStewRecipes.SOURCE_FLUID_MINIMUM;
    }

    /** Upgrade old saves without changing consumed amounts, products or paid progress. */
    private static CyclePlan consumedFluidsOnly(CyclePlan plan) {
        var fluids = plan.fluids().stream().filter(take -> take.fluid().amount() > take.retained())
            .map(take -> new CyclePlan.FluidTake(take.group(), take.tank(),
                take.fluid().withAmount(take.fluid().amount() - take.retained()), 0)).toList();
        return new CyclePlan(plan.recipe(), plan.time(), plan.items(), fluids, plan.products(), plan.energy());
    }

    @Override public boolean canStart(ProcessorState state, ProcessorMachine context) { return true; }

    @Override public ProcessState process(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        initialize();
        variables.set(running, false);
        if (!fault.isEmpty()) return ProcessState.BLOCKED;
        if (checkedEpoch != RockStewRecipes.epoch()) {
            revalidate();
            checkedEpoch = RockStewRecipes.epoch();
        }
        if (legacySource != null && legacy == null && !resumeLegacy()) return ProcessState.BLOCKED;
        if (legacy != null) {
            var result = legacy.advance(context.level().random);
            variables.set(running, result == RecipeCycle.Result.RUNNING);
            if (!legacy.active()) { legacy = null; legacySource = null; }
            persist();
            return result == RecipeCycle.Result.STALLED ? ProcessState.STALLED
                : result == RecipeCycle.Result.BLOCKED ? ProcessState.BLOCKED : ProcessState.RUNNING;
        }
        boolean usedEnergy = false;
        if (chain != null) {
            var energy = owner().energy;
            var usedBefore = energy.used();
            chain.paused(false);
            chain.held(false);
            chain.tick(context.level().random, stage -> stage != 0 || sourceFluidsReady());
            usedEnergy = !energy.used().equals(usedBefore);
        }
        boolean active = chain != null && java.util.stream.IntStream.range(0, chain.size())
            .anyMatch(i -> chain.cycle(i).plan().isPresent());
        boolean stalled = active && java.util.stream.IntStream.range(0, chain.size())
            .anyMatch(i -> chain.result(i) == RecipeCycle.Result.STALLED);
        // A later stage can stall after earlier stages have already done work.
        variables.set(running, usedEnergy || (active && !stalled));
        updateProgressLights();
        persist();
        return stalled ? ProcessState.STALLED : active ? ProcessState.RUNNING
            : switching() ? ProcessState.BLOCKED : ProcessState.IDLE;
    }

    /** Cancel unfinished work and release all claims; stored inputs and products remain in their slots. */
    private void activate(Route selected) {
        var replacement = bind(selected);
        replacement.target(replacement.size() - 1, selected.destination().getMaxStackSize());
        if (chain != null) chain.reset();
        if (legacy != null) legacy.reset();
        legacy = null;
        legacySource = null;
        route = selected;
        updateDisplayProduct();
        chain = replacement;
        clearProgressLights();
        stockModes = new int[chain.size()];
        stockModes[stockModes.length - 1] = 2;
        variables.set(running, false);
        installClaims();
    }

    private void updateDisplayProduct() {
        variables.set(displayProduct, route == null ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(route.destination().getItem()).toString());
    }

    private void updateProgressLights() {
        // Retain the original three-lamp binding for existing resource packs.
        variables.set(progressLights, progressFrame(0, 3));
        for (int stage = 0; stage < stageProgressLights.size(); stage++)
            variables.set(stageProgressLights.get(stage), progressFrame(stage, 7));
    }

    private int progressFrame(int stage, int activeFrames) {
        if (!fault.isEmpty() || chain == null || stage >= chain.size()) return 0;
        var cycle = chain.cycle(stage);
        if (cycle.plan().isEmpty()) return 0;
        // Frame zero is idle; remaining frames divide paid cycle progress evenly.
        return (int) Math.min(activeFrames, 1 + (long) activeFrames * cycle.progress() / cycle.plan().orElseThrow().time());
    }

    private void clearProgressLights() {
        variables.set(progressLights, 0);
        stageProgressLights.forEach(key -> variables.set(key, 0));
    }

    private void revalidate() {
        if (route != null && chain != null && !chain.draining()) {
            try {
                var current = RockStewRecipes.route(machine.level(), route.source(), route.destination());
                if (!sameRoute(route, current)) activate(current);
            } catch (RuntimeException invalid) { chain.reset(); chain.drain(); }
        }
    }

    private boolean sameRoute(Route a, Route b) {
        var ops = machine.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return Route.CODEC.encodeStart(ops, a).getOrThrow().equals(Route.CODEC.encodeStart(ops, b).getOrThrow());
    }

    /** Both recipe viewers submit a source and destination; the server resolves the allowed path. */
    public boolean select(ResourceLocation source, ItemStack destination) {
        if (machine == null || machine.level() == null || machine.level().isClientSide()) return false;
        initialize();
        var root = RockStewRecipes.sources(machine.level()).stream()
            .filter(recipe -> recipe.id().equals(source)).findFirst().orElse(null);
        if (root == null) return false;
        var graph = RockStewRecipes.graph(machine.level(), root);
        int node = graph.indexOf(destination);
        if (node < 0) return false;
        var selected = RockStewRecipes.route(root, graph, node);
        variables.update(() -> {
            if (!fault.isEmpty()) {
                restartFailedWork(selected);
            } else if (route != null && sameRoute(route, selected)) {
                chain.cancelDrain();
            } else activate(selected);
            persist();
        });
        return true;
    }

    private void restartFailedWork(Route selected) {
        variables.set(recovery, variables.get(saved).copy());
        activate(selected); // Release claims only; inputs were never removed from storage.
        fault = "";
    }
    public void stock(int stage) {
        initialize();
        if (route == null || stage < 0 || stage >= route.steps().size()) return;
        if (!fault.isEmpty()) {
            // A deliberate stock click can recover an outdated/invalid cycle by restarting the admitted route.
            Route current;
            try { current = RockStewRecipes.route(machine.level(), route.source(), route.destination()); }
            catch (RuntimeException invalid) { return; } // User can still choose a different valid path.
            var previousModes = stockModes.clone();
            variables.update(() -> {
                restartFailedWork(current);
                for (int i = 0; i < Math.min(previousModes.length, stockModes.length); i++) {
                    stockModes[i] = previousModes[i];
                    applyStock(i);
                }
                persist();
            });
        }
        if (chain == null || stage >= chain.size() || switching()) return;
        variables.update(() -> {
            stockModes[stage] = (stockModes[stage] + 1) % 3;
            applyStock(stage);
            persist();
        });
    }

    private void persist() {
        if (!fault.isEmpty()) return;
        var ops = machine.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var data = new CompoundTag();
        if (route != null && chain != null) {
            data.putIntArray("stock_modes", stockModes);
            data.put("route", Route.CODEC.encodeStart(ops, route).getOrThrow());
            data.put("state", RecipeChain.State.CODEC.encodeStart(ops, chain.state()).getOrThrow());
        }
        if (legacySource != null) {
            var old = legacySource.copy();
            if (legacy != null && legacy.active()) {
                old.put("cycle", CyclePlan.CODEC.encodeStart(ops, legacy.plan().orElseThrow()).getOrThrow());
                old.putInt("progress", legacy.progress());
            }
            data.put("legacy", old);
        }
        variables.set(saved, data);
    }

    private void restoreLegacy(HolderLookup.Provider registries) {
        if (!legacySource.contains("cycle")) return;
        legacy = new RecipeCycle(legacyInventory());
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var plan = CyclePlan.CODEC.parse(ops, legacySource.get("cycle")).getOrThrow();
        var fluids = plan.fluids().stream().map(take -> {
            if (!take.group().equals("reagent")) return take;
            var fluid = take.fluid().fluid().value();
            if (fluid != net.minecraft.world.level.material.Fluids.WATER && fluid != net.minecraft.world.level.material.Fluids.LAVA)
                throw new IllegalArgumentException("Unsupported legacy reagent retained for recovery");
            return new CyclePlan.FluidTake(fluid == net.minecraft.world.level.material.Fluids.WATER
                ? "water" : "lava", take.tank(), take.fluid(), take.retained());
        }).toList();
        plan = new CyclePlan(plan.recipe(), plan.time(), plan.items(), fluids, plan.products(), plan.energy());
        if (!legacy.restore(new RecipeCycle.State(Optional.of(consumedFluidsOnly(plan)), legacySource.getInt("progress"))))
            throw new IllegalArgumentException("Legacy active cycle cannot be restored");
    }

    private MachineInventory legacyInventory() {
        var host = owner();
        return MachineInventory.builder()
            .group("output", StorageGroups.items(output(), java.util.stream.IntStream.range(0, output().getSlots()).toArray()))
            .group("water", StorageGroups.fluids(host.fluids, 0)).group("lava", StorageGroups.fluids(host.fluids, 1))
            .group("energy", StorageGroups.energy(host.energy)).build();
    }

    private boolean resumeLegacy() {
        var id = ResourceLocation.tryParse(legacySource.getString("resume_recipe"));
        var root = RockStewRecipes.sources(machine.level()).stream().filter(h -> h.id().equals(id)).findFirst().orElse(null);
        if (root == null) return false;
        var resumed = new RecipeCycle(legacyInventory());
        if (!resumed.resume(root.id(), cycleRecipe(root.value().recipe()), legacySource.getInt("progress"), legacySource.getInt("resume_time"))) return false;
        legacy = resumed;
        return true;
    }

    private void mergeLegacyFluids(HolderLookup.Provider registries) {
        var storage = owner().fluids;
        var tag = storage.serializeNBT(registries);
        var tanks = tag.getList("Tanks", Tag.TAG_COMPOUND);
        var remainder = new ListTag();
        for (var raw : storage.recoveryContents()) {
            var entry = (CompoundTag) raw;
            var fluid = FluidStack.parseOptional(registries, entry);
            int index = fluid.getFluid() == net.minecraft.world.level.material.Fluids.WATER ? 0
                : fluid.getFluid() == net.minecraft.world.level.material.Fluids.LAVA ? 1 : -1;
            boolean reagent = entry.getString("TankId").equals("reagent")
                || !entry.contains("TankId") && entry.contains("Tank") && entry.getInt("Tank") == 2;
            if (index < 0 || !reagent) { remainder.add(entry.copy()); continue; }
            var target = FluidStack.parseOptional(registries, tanks.getCompound(index).getCompound("Fluid"));
            if (target.getAmount() + fluid.getAmount() > storage.getTankCapacity(index)
                || !target.isEmpty() && !FluidStack.isSameFluidSameComponents(target, fluid)) { remainder.add(entry.copy()); continue; }
            var merged = fluid.copyWithAmount(target.getAmount() + fluid.getAmount());
            var encoded = new CompoundTag();
            encoded.put("Fluid", merged.save(registries));
            encoded.putString("TankId", index == 0 ? "water" : "lava");
            tanks.set(index, encoded);
        }
        tag.put("Recovery", remainder);
        storage.deserializeNBT(registries, tag);
    }
}

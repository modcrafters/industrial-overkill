package net.modcrafters.minecraft.industrialoverkill.ranch;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;
import zone.moddev.mc.lib.common.item.GearboxUpgradeItem;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.ProcessorAction;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StorageAdmission;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.feature.WorkingArea;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.inventory.MachineTx;

import static net.modcrafters.minecraft.industrialoverkill.ranch.RanchAddons.Socket.BREEDING;
import static net.modcrafters.minecraft.industrialoverkill.ranch.RanchAddons.Socket.FEEDING;
import static net.modcrafters.minecraft.industrialoverkill.ranch.RanchAddons.Socket.PACKAGING;
import static net.modcrafters.minecraft.industrialoverkill.ranch.RanchAddons.Socket.SHEARING;
import static net.modcrafters.minecraft.industrialoverkill.ranch.RanchClientState.installed;

public final class RanchControllerProcessor extends StoredProcessor implements WorkingArea.Provider, StorageAdmission {
    private static final int FOCUS_TICKS = 40;
    private static final int TURN_TICKS = 10;
    private static final int ENERGY_PER_ACTION = 100;
    private static final int MAX_ADULT_SETTING = 32;
    private static final int DEFAULT_ADULTS_TARGET = 8;
    private static final int MILK_COOLDOWN = 600;
    // Store the cooldown on the cow so overlapping controllers share it across saves.
    private static final String NEXT_MILKING = "industrial_overkill:ranch_next_milking";

    private RanchHerdCycle herd = new RanchHerdCycle();
    private Phase phase = Phase.IDLE;
    private int remaining;
    private UUID target;
    private UUID parent;
    private Vec3 aim;
    private final ProcessorState.Key<Integer> adultsTarget = variables
        .integer("adults_target", 0, MAX_ADULT_SETTING, DEFAULT_ADULTS_TARGET)
        .synced().define();
    private final Map<ResourceLocation, ProcessorAction> limits = Map.of(
        RanchAddons.id("adults_target_down"), adjustTarget(-1),
        RanchAddons.id("adults_target_up"), adjustTarget(1));

    private enum Phase { IDLE, TURNING, FOCUSING }

    @Override
    public WorkingArea baseWorkingArea() { return new WorkingArea(-2, 0, -5, 2, 2, -1); }

    @Override
    public boolean acceptsItem(ProcessorMachine host, String member, ItemStack stack) {
        if (member.startsWith("food/")) return installed(host, BREEDING) || installed(host, FEEDING);
        return switch (member) {
            case "containers/0" -> containerHandler(stack) != null;
            case "couriers/0" -> installed(host, PACKAGING) && CritterCourierProcessor.isEmptyCourier(stack);
            case "shears/0" -> installed(host, SHEARING);
            default -> false;
        };
    }

    @Override
    public Map<ResourceLocation, ProcessorAction> actions() { return limits; }

    @Override
    protected int dataVersion() { return 1; }

    @Override
    protected CompoundTag migrateState(int fromVersion, CompoundTag data) {
        if (data.contains("adults_target", Tag.TAG_ANY_NUMERIC)) return data;

        boolean oldBreeding = data.contains("breed_limit", Tag.TAG_ANY_NUMERIC);
        boolean oldPacking = data.contains("keep_adults", Tag.TAG_ANY_NUMERIC);
        int previous = DEFAULT_ADULTS_TARGET;
        // An installed packer keeps its original herd floor when the two settings are merged.
        if (oldPacking && (!oldBreeding || machine != null && installed(machine, PACKAGING))) {
            previous = data.getInt("keep_adults");
        } else if (oldBreeding) {
            previous = data.getInt("breed_limit");
        }
        data.putInt("adults_target", Math.clamp(previous, 0, MAX_ADULT_SETTING));
        return data;
    }

    @Override
    public void restoreResources(HolderLookup.Provider registries) {
        herd = new RanchHerdCycle();
        resetScan();
    }

    static boolean managesHerdSize(ProcessorMachine machine) {
        return installed(machine, BREEDING) || installed(machine, PACKAGING);
    }

    private ProcessorAction adjustTarget(int step) {
        return new ProcessorAction((state, context) -> {
            int value = state.get(adultsTarget) + step;
            return value >= 0 && value <= MAX_ADULT_SETTING && managesHerdSize(context.machine());
        }, (state, context) -> {
            state.set(adultsTarget, state.get(adultsTarget) + step);
            return ProcessorAction.Result.success();
        });
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine context) {
        return context.resources().energy("energy").stored() >= ENERGY_PER_ACTION;
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        if (!(context.level() instanceof ServerLevel level)) return ProcessState.IDLE;
        if (!canStart(state, context)) {
            resetScan();
            show(context, null);
            return ProcessState.IDLE;
        }
        remaining -= deltaTicks;
        if (remaining > 0) return ProcessState.RUNNING;

        var resources = context.resources();
        var bounds = workingArea().bounds(context.position(),
            context.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        var animal = RanchHerdCycle.inArea(level, target, bounds);
        switch (phase) {
            case IDLE -> beginNext(level, context, bounds, null);
            case TURNING -> {
                if (animal == null) beginNext(level, context, bounds, aim);
                else beginScan(level, context, animal, null);
            }
            case FOCUSING -> finishFocus(state, level, context, bounds, resources, animal);
        }
        return ProcessState.RUNNING;
    }

    private void finishFocus(ProcessorState state, ServerLevel level, ProcessorMachine context, AABB bounds,
        MachineInventory resources, Animal animal) {
        var departure = animal == null ? aim : animal.getBoundingBox().getCenter();
        boolean breeding = installed(context, BREEDING);
        int herdTarget = state.get(adultsTarget);
        if (parent == null && animal != null && breeding) {
            var mate = findMate(level, resources, animal, herdTarget);
            if (mate != null) {
                parent = animal.getUUID();
                beginScan(level, context, mate, departure);
                return;
            }
        }
        var primary = parent == null ? animal : RanchHerdCycle.inArea(level, parent, bounds);
        boolean worked = parent != null && breeding && breed(level, resources, primary, animal, herdTarget);
        if (!worked && primary != null) tend(level, context, primary, herdTarget);
        beginNext(level, context, bounds, departure);
    }

    private void beginNext(ServerLevel level, ProcessorMachine context, AABB bounds, Vec3 departure) {
        parent = null;
        var next = herd.next(level, bounds);
        if (next != null) {
            beginScan(level, context, next, departure);
        } else {
            resetScan();
            remaining = TURN_TICKS;
            show(context, null);
        }
    }

    private void beginScan(ServerLevel level, ProcessorMachine context, Animal animal, Vec3 departure) {
        var focus = RanchScan.Target.of(animal);
        target = animal.getUUID();
        aim = focus.center();
        phase = departure == null ? Phase.FOCUSING : Phase.TURNING;
        remaining = departure == null ? FOCUS_TICKS : TURN_TICKS;
        show(context, new RanchScan(departure, focus, level.getGameTime(), scanDuration(context, remaining)));
    }

    private void resetScan() {
        phase = Phase.IDLE;
        remaining = 0;
        target = null;
        parent = null;
        aim = null;
    }

    private static void show(ProcessorMachine context, RanchScan scan) {
        var view = RanchClientState.of(context);
        if (view != null) view.showScan(scan);
    }

    private static int scanDuration(ProcessorMachine context, int workTicks) {
        var gears = ((ScreenHost) context).screenInventory("mmdlib_gearbox").getStackInSlot(0);
        int count = gears.getItem() instanceof GearboxUpgradeItem ? Math.min(8, gears.getCount()) : 0;
        return Math.ceilDiv(workTicks * 8, 8 + count);
    }

    private void tend(ServerLevel level, ProcessorMachine context, Animal animal, int keep) {
        var resources = context.resources();
        if (installed(context, PACKAGING) && packageAdult(level, resources, animal, keep)) return;
        if (installed(context, FEEDING) && feed(resources, animal, growthFoodSlot(animal, resources.items("food")))) return;
        if (animal instanceof Cow cow) {
            milk(resources, cow, level.getGameTime());
        } else if (animal instanceof Sheep sheep && installed(context, SHEARING)) {
            var shears = resources.items("shears").peek(0);
            if (!shears.isEmpty()) shear(resources, level, sheep, shears);
        }
    }

    private static int growthFoodSlot(Animal animal, ItemGroup food) {
        if (animal.getAge() >= 0 || AgeableMob.getSpeedUpSecondsWhenFeeding(-animal.getAge()) == 0) return -1;
        return feedingSlot(animal, food);
    }

    private static int feedingSlot(Animal animal, ItemGroup food) {
        return feedingSlot(animal, food, -1);
    }

    private static int feedingSlot(Animal animal, ItemGroup food, int reserved) {
        for (int slot = 0; slot < food.size(); slot++) {
            if (food.free(slot) > (slot == reserved ? 1 : 0) && animal.isFood(food.peek(slot))) return slot;
        }
        return -1;
    }

    private static boolean feed(MachineInventory resources, Animal animal, int slot) {
        if (slot < 0) return false;
        try (var transaction = resources.begin()) {
            if (resources.items("food").extract(slot, 1, transaction) != 1
                || resources.energy("energy").consume(ENERGY_PER_ACTION, transaction) != ENERGY_PER_ACTION) return false;
            transaction.commit();
        }
        animal.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-animal.getAge()), true);
        return true;
    }

    private Animal findMate(ServerLevel level, MachineInventory resources, Animal animal, int limit) {
        if (resources.energy("energy").stored() < 2 * ENERGY_PER_ACTION || !readyToBreed(animal)) return null;
        var food = resources.items("food");
        int slot = feedingSlot(animal, food);
        if (slot < 0 || !herd.canBreed(level, limit)) return null;
        return herd.present(level).filter(mate -> compatibleMate(animal, mate) && feedingSlot(mate, food, slot) >= 0)
            .findFirst().orElse(null);
    }

    private boolean breed(ServerLevel level, MachineInventory resources, Animal animal, Animal mate, int limit) {
        if (animal == null || mate == null || !readyToBreed(animal) || !compatibleMate(animal, mate)
            || !herd.canBreed(level, limit)) return false;
        var food = resources.items("food");
        int slot = feedingSlot(animal, food);
        int mateSlot = feedingSlot(mate, food, slot);
        if (slot < 0 || mateSlot < 0) return false;
        int cost = 2 * ENERGY_PER_ACTION;
        try (var transaction = resources.begin()) {
            if (food.extract(slot, 1, transaction) != 1 || food.extract(mateSlot, 1, transaction) != 1
                || resources.energy("energy").consume(cost, transaction) != cost) return false;
            transaction.commit();
        }
        animal.setInLove(null);
        mate.setInLove(null);
        herd.bred(mate);
        return true;
    }

    private static boolean compatibleMate(Animal animal, Animal mate) {
        return mate != animal && mate.getType() == animal.getType() && readyToBreed(mate) && animal.distanceToSqr(mate) < 64;
    }

    private static boolean readyToBreed(Animal animal) {
        return animal.getAge() == 0 && animal.canFallInLove() && animal.getType().is(RanchAddons.BREEDABLE);
    }

    private boolean packageAdult(ServerLevel level, MachineInventory resources, Animal animal, int keep) {
        if (animal.isBaby() || animal.isInLove()) return false;
        var couriers = resources.items("couriers");
        var courier = couriers.peek(0);
        if (!CritterCourierProcessor.isEmptyCourier(courier)) return false;
        if (herd.present(level).filter(member -> !member.isBaby()).limit(keep + 1).count() <= keep) return false;

        var snapshot = CritterCourierProcessor.snapshotForCapture(animal);
        if (snapshot.isEmpty()) return false;
        var packed = courier.copyWithCount(1);
        CritterCourierProcessor.OCCUPANT.write(packed, snapshot);
        try (var transaction = resources.begin()) {
            if (resources.items("output").insert(packed, transaction) != 1 || couriers.extract(0, 1, transaction) != 1
                || resources.energy("energy").consume(ENERGY_PER_ACTION, transaction) != ENERGY_PER_ACTION) return false;
            transaction.commit();
        }
        CritterCourierProcessor.removeCaptured(animal);
        level.playSound(null, animal.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1, .8f);
        return true;
    }

    private static boolean readyToMilk(Cow cow, long now) {
        return !cow.isBaby() && now >= cow.getPersistentData().getLong(NEXT_MILKING);
    }

    private static void milk(MachineInventory resources, Cow cow, long now) {
        if (!readyToMilk(cow, now)) return;
        try (var transaction = resources.begin()) {
            if (!storeMilk(resources, transaction)
                || resources.energy("energy").consume(ENERGY_PER_ACTION, transaction) != ENERGY_PER_ACTION) return;
            transaction.commit();
        }
        cow.getPersistentData().putLong(NEXT_MILKING, now + MILK_COOLDOWN);
        cow.playSound(SoundEvents.COW_MILK, 1, 1);
    }

    private static boolean storeMilk(MachineInventory resources, MachineTx transaction) {
        var tools = resources.items("containers");
        var tool = tools.peek(0);
        var handler = containerHandler(tool);
        var milk = new FluidStack(NeoForgeMod.MILK, FluidType.BUCKET_VOLUME);
        if (tools.free(0) == 0 || handler == null || handler.fill(milk, FluidAction.SIMULATE) <= 0) return false;

        var output = resources.items("output");
        int remaining = milk.getAmount();
        for (int slot = 0; slot < output.size() && remaining > 0; slot++) {
            while (remaining > 0 && output.free(slot) > 0) {
                var partial = containerHandler(output.peek(slot));
                if (partial == null || !holdsMilk(partial)) break;
                int filled = fillWithMilk(partial, milk, remaining);
                if (filled == 0) break;
                var result = partial.getContainer();
                var taken = new long[output.size()];
                taken[slot] = 1;
                if (!output.fits(List.of(result), taken)) break;
                if (output.extract(slot, 1, transaction) != 1
                    || output.insert(result, transaction) != result.getCount()) return false;
                remaining -= filled;
            }
        }

        boolean reusableBucket = tool.is(Items.BUCKET);
        while (remaining > 0 && handler != null && tools.free(0) > (reusableBucket ? 1 : 0)) {
            int filled = fillWithMilk(handler, milk, remaining);
            var result = handler.getContainer();
            if (filled == 0 || !output.fits(List.of(result))) break;
            if (tools.extract(0, 1, transaction) != 1
                || output.insert(result, transaction) != result.getCount()) return false;
            remaining -= filled;
            if (remaining > 0) handler = containerHandler(tools.peek(0));
        }
        if (remaining == 0) return true;
        // Keep one empty vanilla bucket to route milk that could not go into items.
        return reusableBucket && resources.fluids("milk").insert(
            new FluidAmount(NeoForgeMod.MILK, DataComponentPatch.EMPTY, remaining), transaction) == remaining;
    }

    private static IFluidHandlerItem containerHandler(ItemStack stack) {
        return stack.isEmpty() ? null : FluidUtil.getFluidHandler(stack.copyWithCount(1)).orElse(null);
    }

    private static boolean holdsMilk(IFluidHandlerItem handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            if (handler.getFluidInTank(tank).is(NeoForgeMod.MILK)) return true;
        }
        return false;
    }

    private static int fillWithMilk(IFluidHandlerItem handler, FluidStack milk, int amount) {
        int filled = handler.fill(milk.copyWithAmount(amount), FluidAction.EXECUTE);
        return filled > 0 && filled <= amount && !handler.getContainer().isEmpty() ? filled : 0;
    }

    private static boolean shear(MachineInventory resources, ServerLevel level, Sheep sheep, ItemStack shears) {
        if (!sheep.readyForShearing()) return false;
        var wool = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(sheep.getColor().getName() + "_wool"));
        var output = resources.items("output");
        // Require room for the largest vanilla roll before choosing a yield.
        if (!output.fits(List.of(new ItemStack(wool, 3)))) return false;
        var drops = new ItemStack(wool, 1 + level.random.nextInt(3));
        var toolSlot = resources.items("shears");
        var wornShears = shears.copy();
        wornShears.hurtAndBreak(1, level, null, item -> {});
        try (var transaction = resources.begin()) {
            if (output.insert(drops, transaction) != drops.getCount()
                || toolSlot.extract(0, 1, transaction) != 1
                || (!wornShears.isEmpty() && toolSlot.insert(0, wornShears, transaction) != 1)
                || resources.energy("energy").consume(ENERGY_PER_ACTION, transaction) != ENERGY_PER_ACTION) return false;
            transaction.commit();
        }
        sheep.setSheared(true);
        level.playSound(null, sheep, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1, 1);
        sheep.gameEvent(GameEvent.SHEAR);
        return true;
    }
}

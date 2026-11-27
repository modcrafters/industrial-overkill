package net.modcrafters.minecraft.industrialoverkill.power;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.api.capabilities.SideConfig;
import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.neoforge.BasePoweredMachine;
import zone.moddev.mc.lib.machine.neoforge.MachineInventoryLifecycle;
import zone.moddev.mc.lib.machine.neoforge.MachineRecoveryItem;
import zone.moddev.mc.lib.machine.neoforge.MachineResources;
import zone.moddev.mc.lib.machine.recipe.CyclePlan;

public final class GeneratorEntity extends BasePoweredMachine {
    private static final int EXPORT = 128;
    private final ItemStorage items = new ItemStorage(5).role(Role.INPUT, 0).role(Role.OUTPUT, 1, 2, 3, 4)
        .restrict(stack -> GeneratorFuel.forStack(getLevel(), stack) != null, 0).onChange(this::setChanged);
    private final MachineInventoryLifecycle inventory;
    private final FluidStorage fluids = new FluidStorage().onChange(this::setChanged);
    private final MachineInventory burnInventory;
    private CyclePlan burn;
    private int total;
    private int remaining;
    private int rate;

    public GeneratorEntity(BlockPos pos, BlockState state) {
        super(OmnivorousGenerator.TYPE.get(), pos, state, 32_000, 0, EXPORT);
        var resources = new MachineResources(this, this::facing);
        int catalyst = fluids.addTank(GeneratorCatalyst.CAPACITY, Role.INPUT);
        fluids.restrict(stack -> GeneratorCatalyst.rules(getLevel()).containsKey(stack.getFluid()), catalyst);
        int byproduct = fluids.addTank(GeneratorCatalyst.CAPACITY, Role.OUTPUT);
        fluids.tankId(catalyst, "catalyst").tankId(byproduct, "byproduct");
        resources.items(new ResourceGroup<>("fuel", Component.translatable("omnivore.fuel"), 0xe49d4a,
            ResourceType.ITEMS, IoMode.INPUT, List.of(0), List.of("fuel"), new SideConfig(IoMode.INPUT)).inventory("items"), items);
        resources.items(new ResourceGroup<>("containers", Component.translatable("omnivore.containers"), 0x69b4b0,
            ResourceType.ITEMS, IoMode.OUTPUT, List.of(1, 2, 3, 4), List.of("containers", "output2", "output3", "output4"),
            new SideConfig(IoMode.OUTPUT)).inventory("items"), items);
        resources.energy(new ResourceGroup<>("energy", Component.translatable("gym.group.energy"), 0xd95b40,
            ResourceType.ENERGY, IoMode.OUTPUT, List.of(0), List.of("energy"), new SideConfig(IoMode.OUTPUT)), energy);
        resources.fluids(new ResourceGroup<>("catalyst", Component.translatable("omnivore.catalyst"), 0x5387d1,
            ResourceType.FLUIDS, IoMode.INPUT, List.of(catalyst), List.of("catalyst"), new SideConfig(IoMode.INPUT)), fluids);
        resources.fluids(new ResourceGroup<>("byproduct", Component.translatable("omnivore.byproduct"), 0xa488bb,
            ResourceType.FLUIDS, IoMode.OUTPUT, List.of(byproduct), List.of("byproduct"), new SideConfig(IoMode.OUTPUT)), fluids);
        inventory = poweredInventory(resources.build().automation()).items("items", items).fluids("fluids", fluids);
        burnInventory = resources.recipes();
    }

    @Override public ItemStorage screenItems() { return items; }
    @Override public FluidStorage screenFluids() { return fluids; }
    @Override protected MachineInventoryLifecycle machineInventory() { return inventory; }
    @Override public boolean screenAutomationAddons() { return false; }
    @Override public int getProgress() { return remaining; }
    @Override public int getTime() { return Math.max(1, total); }
    public int getBurnRate() { return remaining > 0 ? rate : 0; }

    private boolean loadFuel() {
        var stack = items.getStackInSlot(0);
        var liquid = fluids.getFluidInTank(0);
        var rule = liquid.isEmpty() ? null : GeneratorCatalyst.rules(level).get(liquid.getFluid());
        var catalyst = rule == null ? null : rule.value();
        var fuel = GeneratorFuel.forStack(level, stack, catalyst);
        if (fuel == null) return false;
        burn = CyclePlan.of(rule == null ? OmnivorousGenerator.ID : rule.id(), fuel.recipe(stack, catalyst), burnInventory)
            .filter(plan -> plan.reserve(burnInventory)).orElse(null);
        if (burn == null) return false;
        total = fuel.energy();
        remaining = total;
        rate = fuel.rate();
        return true;
    }

    @Override protected void beforeMachineTick() {
        boolean burning = remaining > 0 || (energy.space() > 0 && !level.hasNeighborSignal(worldPosition) && loadFuel());
        if (burning) {
            int generated = Math.min(remaining, rate);
            energy.insert(generated, false);
            remaining -= generated;
            if (remaining == 0) finishBurn(level);
            setChanged();
        }
        var state = getBlockState();
        if (state.getValue(BlockStateProperties.LIT) != burning) {
            level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, burning), Block.UPDATE_CLIENTS);
        }
    }

    private void finishBurn(Level level) {
        // Older saves already consumed the fuel and returned its container at ignition.
        if (burn != null && !burn.finish(burnInventory, level.random)) {
            burn.release(burnInventory);
            LogUtils.getLogger().warn("Generator at {} lost its reserved fuel or output space", worldPosition);
        }
        burn = null;
        remaining = 0;
    }

    @Override public void dropContents(Level level, BlockPos pos) {
        if (!level.isClientSide && remaining > 0) finishBurn(level);
        super.dropContents(level, pos);
    }

    @Override protected void afterMachineTick() {
        for (var side : Direction.values()) {
            if (energy.stored() == 0) break;
            if (screenAutomation().mode(ResourceType.ENERGY, 0, side).canExtract() && level.hasChunkAt(worldPosition.relative(side))) {
                Transfer.pushEnergy(level, worldPosition, side, energy, EXPORT);
            }
        }
    }

    @Override protected void saveMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("burn_total", total);
        tag.putInt("burn_remaining", remaining);
        tag.putInt("burn_rate", rate);
        if (burn != null) {
            tag.put("burn_plan", CyclePlan.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), burn).getOrThrow());
        }
    }

    @Override public void clearContent() {
        if (burn != null) burn.release(burnInventory);
        burn = null;
        total = 0;
        remaining = 0;
        rate = 0;
        super.clearContent();
    }

    @Override protected void loadMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        if (burn != null) burn.release(burnInventory);
        burn = null;
        total = Math.max(0, tag.getInt("burn_total"));
        rate = Math.clamp(tag.getInt("burn_rate"), 0, total);
        remaining = rate == 0 ? 0 : Math.clamp(tag.getInt("burn_remaining"), 0, total);
        if (remaining > 0 && tag.contains("burn_plan")) {
            burn = CyclePlan.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("burn_plan"))
                .result().filter(plan -> plan.restore(burnInventory)).orElse(null);
            if (burn == null) remaining = 0;
        }
    }

    @Override protected void recoverResources(Level level, BlockPos pos, CompoundTag payload) {
        Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, MachineRecoveryItem.create(payload));
    }
}

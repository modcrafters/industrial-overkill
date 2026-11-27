package net.modcrafters.minecraft.industrialoverkill.power;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.api.capabilities.SideConfig;
import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.machine.neoforge.BasePoweredMachine;
import zone.moddev.mc.lib.machine.neoforge.MachineInventoryLifecycle;
import zone.moddev.mc.lib.machine.neoforge.MachineRecoveryItem;
import zone.moddev.mc.lib.machine.neoforge.MachineResources;

public final class GeneratorEntity extends BasePoweredMachine {
    private static final int EXPORT = 128;
    private final ItemStorage items = new ItemStorage(2).role(Role.INPUT, 0).role(Role.OUTPUT, 1)
        .restrict(stack -> GeneratorFuel.forStack(getLevel(), stack) != null, 0).onChange(this::setChanged);
    private final MachineInventoryLifecycle inventory;
    private int total;
    private int remaining;
    private int rate;

    public GeneratorEntity(BlockPos pos, BlockState state) {
        super(OmnivorousGenerator.TYPE.get(), pos, state, 32_000, 0, EXPORT);
        var resources = new MachineResources(this, this::facing);
        resources.items(new ResourceGroup<>("fuel", Component.translatable("omnivore.fuel"), 0xe49d4a,
            ResourceType.ITEMS, IoMode.INPUT, List.of(0), List.of("fuel"), new SideConfig(IoMode.INPUT)).inventory("items"), items);
        resources.items(new ResourceGroup<>("containers", Component.translatable("omnivore.containers"), 0x69b4b0,
            ResourceType.ITEMS, IoMode.OUTPUT, List.of(1), List.of("containers"), new SideConfig(IoMode.OUTPUT)).inventory("items"), items);
        resources.energy(new ResourceGroup<>("energy", Component.translatable("gym.group.energy"), 0xd95b40,
            ResourceType.ENERGY, IoMode.OUTPUT, List.of(0), List.of("energy"), new SideConfig(IoMode.OUTPUT)), energy);
        inventory = poweredInventory(resources.build().automation()).items("items", items);
    }

    @Override public ItemStorage screenItems() { return items; }
    @Override protected MachineInventoryLifecycle machineInventory() { return inventory; }
    @Override public boolean screenAutomationAddons() { return false; }
    @Override public int getProgress() { return remaining; }
    @Override public int getTime() { return Math.max(1, total); }
    public int getBurnRate() { return remaining > 0 ? rate : 0; }

    private boolean loadFuel() {
        var stack = items.getStackInSlot(0);
        var fuel = GeneratorFuel.forStack(level, stack);
        if (fuel == null) return false;
        var serving = stack.copyWithCount(1);
        var container = GeneratorFuel.remainder(serving);
        if (!container.isEmpty() && !items.fits(1, container)) return false;

        items.extractItem(0, 1, false);
        if (!container.isEmpty()) items.insertResult(1, container, false);
        total = fuel.energy();
        remaining = total;
        rate = fuel.rate();
        return true;
    }

    @Override protected void beforeMachineTick() {
        boolean burning = energy.space() > 0 && !level.hasNeighborSignal(worldPosition) && (remaining > 0 || loadFuel());
        if (burning) remaining -= (int) energy.insert(Math.min(remaining, rate), false);
        var state = getBlockState();
        if (state.getValue(BlockStateProperties.LIT) != burning) {
            level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, burning), Block.UPDATE_CLIENTS);
        }
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
    }

    @Override public void clearContent() {
        total = 0;
        remaining = 0;
        rate = 0;
        super.clearContent();
    }

    @Override protected void loadMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        total = Math.max(0, tag.getInt("burn_total"));
        rate = Math.clamp(tag.getInt("burn_rate"), 0, total);
        remaining = rate == 0 ? 0 : Math.clamp(tag.getInt("burn_remaining"), 0, total);
    }

    @Override protected void recoverResources(Level level, BlockPos pos, CompoundTag payload) {
        Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, MachineRecoveryItem.create(payload));
    }
}

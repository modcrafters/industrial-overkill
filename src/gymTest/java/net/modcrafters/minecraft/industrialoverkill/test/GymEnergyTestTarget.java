package net.modcrafters.minecraft.industrialoverkill.test;

import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;

/** An observable receiver for verifying the gym's active export path. Test source set only. */
@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GymEnergyTestTarget {
    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BlockEntityType.CHEST,
            (chest, side) -> new EnergyStorage(100000, 1000, 0) {
                @Override
                public int receiveEnergy(int amount, boolean simulate) {
                    int received = super.receiveEnergy(amount, simulate);
                    if (!simulate)
                        chest.getPersistentData().putInt("gym_test_received",
                            chest.getPersistentData().getInt("gym_test_received") + received);
                    return received;
                }
            });
    }
}

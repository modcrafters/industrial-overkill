package net.modcrafters.minecraft.industrialoverkill.agri;

import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import net.minecraft.util.Unit;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmSupplies;
import zone.moddev.mc.lib.api.capabilities.ItemInventories.Address;
import zone.moddev.mc.lib.machine.definition.MachineFeatureTypes;
import zone.moddev.mc.lib.machine.feature.MachineFeature;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Registers legacy slot addresses before the machine loads its item inventories. */
public record AgriFertilizerMigration(String key) implements MachineFeature<Unit> {
    public static void register() {
        MachineFeatureTypes.register(FarmSupplies.id("fertilizer_migration"), Set.of(), (definition, host) -> {
            var items = ((DefinedMachineEntity) host).items;
            items.migrateLayout("filtered_fertilizer", List.of(new Address("fertilizer", 1)));
            items.migrateLayout("pellets", List.of(new Address("fertilizer", 2)));
            items.migrateLayout("alternative_fertilizers", List.of(new Address("fertilizer", 3), new Address("fertilizer", 4)));
            return new AgriFertilizerMigration(definition.id());
        });
    }

    @Override public Codec<Unit> codec() { return Unit.CODEC; }
    @Override public Unit state() { return Unit.INSTANCE; }
    @Override public void restore(Unit ignored) {}
}

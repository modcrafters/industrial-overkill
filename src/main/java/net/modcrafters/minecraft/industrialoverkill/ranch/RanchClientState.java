package net.modcrafters.minecraft.industrialoverkill.ranch;

import java.util.List;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import zone.moddev.mc.lib.api.capabilities.ItemInventories;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.MachineFeature;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.FeatureSync;

/** Transient client view of the ranch's scan and installed add-ons. */
final class RanchClientState implements MachineFeature<CompoundTag> {
    static final String UPGRADES = "upgrades";
    private final String key;
    private final DefinedMachineEntity machine;
    private int clientAddons;
    private RanchScan scan;

    RanchClientState(String key, DefinedMachineEntity machine) {
        this.key = key;
        this.machine = machine;
        for (var socket : RanchAddons.Socket.values()) {
            // Fold the former one-slot inventories into the last four grid slots.
            machine.items.migrateLayout(socket.legacyInventory,
                List.of(new ItemInventories.Address(UPGRADES, 5 + socket.ordinal())));
        }
        machine.items.inventory(UPGRADES).changes().subscribe(() -> {
            if (!machine.isRemoved()) FeatureSync.send(machine);
        });
    }

    static boolean installed(ProcessorMachine machine, RanchAddons.Socket socket) {
        if (machine.level() != null && machine.level().isClientSide()) {
            var view = of(machine);
            return view != null && (view.clientAddons & socket.bit()) != 0;
        }
        var upgrades = machine.resources().items(UPGRADES);
        for (int slot = 0; slot < upgrades.size(); slot++) {
            if (socket.matches(upgrades.peek(slot))) return true;
        }
        return false;
    }

    static RanchClientState of(ProcessorMachine machine) {
        if (!(machine instanceof ScreenHost host)) return null;
        var views = host.screenFeatures().contributors(RanchClientState.class);
        return views.isEmpty() ? null : views.getFirst();
    }

    RanchScan scan() { return scan; }

    void showScan(RanchScan next) {
        if (next == null && scan == null) return;
        scan = next;
        FeatureSync.send(machine);
    }

    @Override
    public String key() { return key; }

    @Override
    public Codec<CompoundTag> codec() { return CompoundTag.CODEC; }

    @Override
    public CompoundTag state() { return new CompoundTag(); }

    @Override
    public void restore(CompoundTag ignored) {
        // Installed items are restored by normal machine storage.
        scan = null;
    }

    @Override
    public boolean syncsToClient() { return true; }

    @Override
    public CompoundTag clientState() {
        int addons = 0;
        for (var socket : RanchAddons.Socket.values()) {
            if (installed(machine, socket)) addons |= socket.bit();
        }
        var tag = new CompoundTag();
        tag.putInt("addons", addons);
        if (scan != null) tag.put("scan", scan.save());
        return tag;
    }

    @Override
    public void restoreOnClient(CompoundTag tag) {
        clientAddons = tag.getInt("addons");
        scan = tag.contains("scan") ? RanchScan.load(tag.getCompound("scan")) : null;
    }
}

package net.modcrafters.minecraft.industrialoverkill.upgrade;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;

/** Mod-owned upgrade items; MMDLib supplies storage and presentation. */
public final class MachineUpgrades {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
        Registries.ITEM, IndustrialOverkill.MOD_ID);
    public static final DeferredHolder<Item, zone.moddev.mc.lib.common.item.RangeUpgradeItem> RANGE =
        zone.moddev.mc.lib.MMDLib.RANGE_UPGRADE;
    private static final DeferredHolder<Item, zone.moddev.mc.lib.common.item.RangeUpgradeItem> LEGACY_RANGE =
        ITEMS.register("range_upgrade", zone.moddev.mc.lib.common.item.RangeUpgradeItem::new);
    public static final DeferredHolder<Item, zone.moddev.mc.lib.common.item.GearboxUpgradeItem> GEARBOX =
        zone.moddev.mc.lib.MMDLib.GEARBOX_UPGRADE;
    public static final DeferredHolder<Item, zone.moddev.mc.lib.common.item.CapacitorUpgradeItem> CAPACITOR =
        zone.moddev.mc.lib.MMDLib.CAPACITOR_UPGRADE;
    // Keep pre-library stacks loadable; new recipes and the creative tab use mmdlib's item.
    private static final DeferredHolder<Item, zone.moddev.mc.lib.common.item.GearboxUpgradeItem> LEGACY_GEARBOX =
        ITEMS.register("gearbox_upgrade", zone.moddev.mc.lib.common.item.GearboxUpgradeItem::new);

    private MachineUpgrades() {}

    public static void register(IEventBus bus) { ITEMS.register(bus); }
}

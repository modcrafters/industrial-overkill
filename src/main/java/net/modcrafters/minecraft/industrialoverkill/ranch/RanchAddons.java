package net.modcrafters.minecraft.industrialoverkill.ranch;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import zone.moddev.mc.lib.machine.definition.MachineFeatureTypes;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

import static net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill.MOD_ID;

public final class RanchAddons {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<Item> BREEDING = ITEMS.registerSimpleItem("pair_pressure");
    public static final DeferredItem<Item> FEEDING = ITEMS.registerSimpleItem("snackcelerator");
    public static final DeferredItem<Item> PACKAGING = ITEMS.registerSimpleItem("livestock_to_go");
    public static final DeferredItem<Item> SHEARING = ITEMS.registerSimpleItem("baa_rber_shop");
    static final TagKey<EntityType<?>> BREEDABLE = TagKey.create(Registries.ENTITY_TYPE, id("ranch_breedable"));

    private RanchAddons() {}

    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        RanchSettings.register();
        RanchToolSlot.register();
        MachineFeatureTypes.register(id("ranch_client_state"), Set.of(),
            (definition, host) -> new RanchClientState(definition.id(), (DefinedMachineEntity) host));
    }

    enum Socket {
        BREEDING("breeding", RanchAddons.BREEDING),
        FEEDING("feeding", RanchAddons.FEEDING),
        PACKAGING("packaging", RanchAddons.PACKAGING),
        SHEARING("shearing", RanchAddons.SHEARING);

        final String legacyInventory;
        private final DeferredItem<Item> item;

        Socket(String legacyInventory, DeferredItem<Item> item) {
            this.legacyInventory = legacyInventory;
            this.item = item;
        }

        int bit() { return 1 << ordinal(); }

        boolean matches(ItemStack stack) { return stack.is(item.get()); }
    }
}

package net.modcrafters.minecraft.industrialoverkill.waste;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;

import static net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill.MOD_ID;

public final class FarmSupplies {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<Item> FILTER_CAKE = ITEMS.registerSimpleItem("filter_cake");
    public static final DeferredItem<Item> FERTILIZER = ITEMS.registerSimpleItem("fertilizer");
    public static final DeferredItem<Item> FILTERED_FERTILIZER = ITEMS.registerSimpleItem("filtered_fertilizer");
    public static final DeferredItem<Item> SLOW_RELEASE_PELLET = ITEMS.registerSimpleItem("slow_release_pellet");
    public static final TagKey<Item> COMPOSTABLE_SEEDS = TagKey.create(Registries.ITEM, id("compostable_seeds"));
    public static final ResourceLocation SEWAGE = id("sewage");
    public static final ResourceLocation FILTERED_SEWAGE = id("filtered_sewage");
    public static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("water");

    private FarmSupplies() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void register(IEventBus bus) { ITEMS.register(bus); }

    static FluidAmount fluid(ResourceLocation id, int amount) {
        var fluid = BuiltInRegistries.FLUID.get(id);
        return new FluidAmount(fluid.builtInRegistryHolder(), DataComponentPatch.EMPTY, amount);
    }
}

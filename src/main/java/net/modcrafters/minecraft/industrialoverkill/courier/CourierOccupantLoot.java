package net.modcrafters.minecraft.industrialoverkill.courier;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** Preserves the captured animal when a Courier drops as an item. */
public final class CourierOccupantLoot extends LootModifier {
    private static final MapCodec<CourierOccupantLoot> CODEC = RecordCodecBuilder.mapCodec(instance ->
        codecStart(instance).apply(instance, CourierOccupantLoot::new));
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, IndustrialOverkill.MOD_ID);

    private CourierOccupantLoot(LootItemCondition[] conditions) { super(conditions); }

    public static void register(IEventBus bus) {
        TYPES.register("courier_occupant", () -> CODEC);
        TYPES.register(bus);
    }

    @Override
    public MapCodec<CourierOccupantLoot> codec() { return CODEC; }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        var courier = CritterCourierProcessor.from(context.getParamOrNull(LootContextParams.BLOCK_ENTITY));
        if (courier == null || !courier.occupied()) return loot;
        var item = MachineContent.item(CritterCourierProcessor.ID);
        for (var stack : loot) {
            if (stack.is(item)) CritterCourierProcessor.OCCUPANT.write(stack, courier.occupant());
        }
        return loot;
    }
}

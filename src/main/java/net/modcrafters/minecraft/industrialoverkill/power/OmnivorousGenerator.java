package net.modcrafters.minecraft.industrialoverkill.power;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.MachineCapabilities;

public final class OmnivorousGenerator {
    private static final String NAME = "omnivorous_generator";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, IndustrialOverkill.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IndustrialOverkill.MOD_ID);
    public static final DeferredItem<Item> ASH = ITEMS.registerSimpleItem("ash");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IndustrialOverkill.MOD_ID);
    public static final DeferredHolder<Block, GeneratorBlock> BLOCK = BLOCKS.register(NAME,
        () -> new GeneratorBlock(BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorEntity>> TYPE = TYPES.register(NAME,
        () -> BlockEntityType.Builder.of(GeneratorEntity::new, BLOCK.get()).build(null));

    private OmnivorousGenerator() {}

    public static void register(IEventBus bus) {
        GeneratorBurnArrow.register();
        ITEMS.register(NAME, () -> new BlockItem(BLOCK.get(), new Item.Properties()));
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener((RegisterDataMapTypesEvent event) -> event.register(GeneratorFuel.CALORIES));
        bus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TYPE.get(), (machine, side) ->
                machine.screenItems().view(slot -> side == null ? (slot == 0 ? IoMode.INPUT : IoMode.OUTPUT)
                    : machine.screenAutomation().mode(ResourceType.ITEMS, slot, side)));
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TYPE.get(), (machine, side) ->
                machine.screenFluids().view(tank -> machine.screenAutomation().mode(ResourceType.FLUIDS, tank, side)));
            MachineCapabilities.energy(event, TYPE.get(), machine -> machine.energy,
                (machine, side) -> machine.screenAutomation().mode(ResourceType.ENERGY, 0, side));
        });
    }
}

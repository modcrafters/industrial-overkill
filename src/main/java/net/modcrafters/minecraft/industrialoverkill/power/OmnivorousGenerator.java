package net.modcrafters.minecraft.industrialoverkill.power;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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
import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.drawer.DrawerRegistry;
import zone.moddev.mc.lib.gui.model.Edge;

public final class OmnivorousGenerator {
    private static final String NAME = "omnivorous_generator";
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(IndustrialOverkill.MOD_ID, NAME);
    public static final ResourceLocation RECIPES = ID.withPath("generator_recipes");
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, IndustrialOverkill.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IndustrialOverkill.MOD_ID);
    public static final DeferredItem<AshItem> ASH = ITEMS.registerItem("ash", AshItem::new);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IndustrialOverkill.MOD_ID);
    public static final DeferredHolder<Block, GeneratorBlock> BLOCK = BLOCKS.register(NAME,
        () -> new GeneratorBlock(BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorEntity>> TYPE = TYPES.register(NAME,
        () -> BlockEntityType.Builder.of(GeneratorEntity::new, BLOCK.get()).build(null));

    private OmnivorousGenerator() {}

    public static void register(IEventBus bus) {
        GeneratorCatalyst.register(bus);
        GeneratorBurnArrow.register();
        DrawerRegistry.GLOBAL.register(RECIPES, context -> context.screen().id().equals(ID)
            ? List.of(new DrawerDefinition(RECIPES, RECIPES.getPath(), DrawerPlacement.top(Edge.LEFT, 50))) : List.of());
        ITEMS.register(NAME, () -> new BlockItem(BLOCK.get(), new Item.Properties()));
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(AshItem::registerDispenserBehavior));
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

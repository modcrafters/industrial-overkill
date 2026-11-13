package net.modcrafters.minecraft.industrialoverkill.gym;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierData;
import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.gui.WidgetType;
import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.value.ValueTypes;
import zone.moddev.mc.lib.machine.multiblock.FixedLayout;
import zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition;
import zone.moddev.mc.lib.machine.multiblock.StructureMember;
import zone.moddev.mc.lib.machine.multiblock.StructurePort;
import zone.moddev.mc.lib.machine.multiblock.StructureRequirement;
import zone.moddev.mc.lib.machine.neoforge.MultiblockItem;
import zone.moddev.mc.lib.machine.neoforge.MultiblockPartEntity;
import zone.moddev.mc.lib.machine.neoforge.MultiblockPorts;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

public final class AnimalGym {
    /** The belt occupies the controller's viewer-relative right side in every orientation. */
    public static final Set<RelativeFace> UNAVAILABLE_FACES = Set.of(RelativeFace.RIGHT,
        RelativeFace.TOP);
    private static final Set<Direction> CONTROLLER_FACES = java.util.Arrays
        .stream(RelativeFace.values())
        .filter(face -> !UNAVAILABLE_FACES.contains(face))
        .map(face -> face.toWorld(Direction.NORTH))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK,
        IndustrialOverkill.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM,
        IndustrialOverkill.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister
        .create(Registries.BLOCK_ENTITY_TYPE, IndustrialOverkill.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister
        .create(Registries.CREATIVE_MODE_TAB, IndustrialOverkill.MOD_ID);
    public static final DeferredHolder<Block, GymBlock> CONTROLLER = block("animal_gym",
        GymBlock.Kind.CONTROLLER);
    public static final DeferredHolder<Block, GymBlock> BELT = block("gym_belt",
        GymBlock.Kind.BELT);
    public static final DeferredHolder<Block, GymBlock> POST = block("gym_post",
        GymBlock.Kind.POST);
    public static final DeferredHolder<Block, GymBlock> BEAM = block("gym_beam",
        GymBlock.Kind.BEAM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnimalGymEntity>> CONTROLLER_TYPE = TYPES
        .register("animal_gym",
            () -> BlockEntityType.Builder.of(AnimalGymEntity::new, CONTROLLER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockPartEntity>> PART_TYPE = TYPES
        .register("gym_part", () -> BlockEntityType.Builder
            .of(AnimalGym::part, BELT.get(), POST.get(), BEAM.get()).build(null));
    public static final DeferredHolder<Item, MultiblockItem> ITEM = ITEMS.register("animal_gym",
        () -> new MultiblockItem(new Item.Properties().stacksTo(16), AnimalGym::definition));
    public static final DeferredHolder<Item, RecoveredFoodItem> RECOVERY = ITEMS
        .register("recovered_food", RecoveredFoodItem::new);
    private static MultiblockDefinition definition;
    private AnimalGym() {
    }
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(IndustrialOverkill.MOD_ID, path);
    }
    private static MultiblockPartEntity part(BlockPos pos,
        net.minecraft.world.level.block.state.BlockState state) {
        return new MultiblockPartEntity(PART_TYPE.get(), pos, state);
    }
    private static DeferredHolder<Block, GymBlock> block(String name, GymBlock.Kind kind) {
        return BLOCKS.register(name,
            () -> new GymBlock(BlockBehaviour.Properties.of().strength(3, 6).noOcclusion(),
                kind));
    }
    public static void register(IEventBus bus) {
        GymScreenElements.register();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        TABS.register("machines",
            () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.industrial_overkill"))
                .icon(() -> new ItemStack(ITEM.get()))
                .displayItems((parameters, output) -> {
                    output.accept(ITEM.get());
                    output.accept(MachineContent.item(CritterCourierData.ID));
                    output.accept(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        id("rock_stew_factory")));
                }).build());
        TABS.register(bus);
        bus.addListener(
            (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> MultiblockPorts
                .register(event, CONTROLLER_TYPE.get(), PART_TYPE.get()));
        WidgetTypes.GLOBAL.register(id("gym_status"),
            new WidgetType(Set.of(ValueTypes.TEXT), widget -> {
            }));
        WidgetTypes.GLOBAL.register(id("animal_portrait"),
            new WidgetType(Set.of(ValueTypes.WHOLE), widget -> {
            }));
        for (String action : List.of("toggle", "release", "intensity")) {
            MaterialExtensions.GLOBAL.events().registerCallback(id(action),
                MaterialEventTypes.GUI_CLICK, click -> {
                    if (click.menu().blockEntity instanceof AnimalGymEntity gym) {
                        if (action.equals("release") && gym.captive.installed())
                            gym.ejectTrap(click.player());
                        else
                            gym.control(action);
                    }
                });
        }
    }
    public static MultiblockDefinition definition() {
        if (definition == null) {
            var members = new ArrayList<StructureMember>();
            members.add(new StructureMember("controller", "controller", BlockPos.ZERO,
                CONTROLLER.get().defaultBlockState()));
            // Exactly three base positions. The gantry is geometry on these members.
            for (int x = 1; x <= 2; x++)
                members.add(new StructureMember("belt_" + x, "belt", new BlockPos(-x, 0, 0),
                    BELT.get().defaultBlockState().setValue(GymBlock.END, x == 2)));
            var requirements = new ArrayList<StructureRequirement>();
            for (int x = 0; x <= 2; x++)
                requirements
                    .add(new StructureRequirement("clearance", new BlockPos(-x, 1, 0), true));
            definition = new MultiblockDefinition(id("animal_gym"), 2,
                new FixedLayout(members, requirements), 3, 2,
                MultiblockDefinition.Dismantling.REMOVE_OWNED,
                Map.of("clearance", (level, pos) -> level.getBlockState(pos).isAir()),
                List.of(
                    new StructurePort("controller", "food", StructurePort.Kind.ITEMS,
                        CONTROLLER_FACES,
                        false, true, true),
                    new StructurePort("controller", "energy", StructurePort.Kind.ENERGY,
                        CONTROLLER_FACES,
                        false, false, true)));
        }
        return definition;
    }
}

package zone.moddev.mc.industrialoverkill.trap;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.industrialoverkill.gym.AnimalGym;
import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.api.MaterialExtensions;

public final class MobTrap {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK,
        IndustrialOverkill.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM,
        IndustrialOverkill.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister
        .create(Registries.BLOCK_ENTITY_TYPE, IndustrialOverkill.MOD_ID);
    public static final DeferredHolder<Block, MobTrapBlock> BLOCK = BLOCKS.register("mob_trap",
        () -> new MobTrapBlock(BlockBehaviour.Properties.of().strength(2.5f, 6).noOcclusion()
            .pushReaction(PushReaction.BLOCK)));
    public static final DeferredHolder<Item, MobTrapItem> ITEM = ITEMS.register("mob_trap",
        MobTrapItem::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MobTrapEntity>> TYPE = TYPES
        .register("mob_trap",
            () -> BlockEntityType.Builder.of(MobTrapEntity::new, BLOCK.get()).build(null));
    private MobTrap() {
    }
    public static void register(IEventBus bus) {
        // MMDLib's provider replacement keeps the normal stats drawer on machines,
        // but this compact utility screen has no drawers at all.
        zone.moddev.mc.lib.gui.drawer.DrawerRegistry.GLOBAL.replace(
            zone.moddev.mc.lib.gui.drawer.BuiltinDrawers.STATS,
            context -> context.host() == null || context.host() instanceof MobTrapEntity
                ? java.util.List.of()
                : java.util.List.of(new zone.moddev.mc.lib.gui.drawer.DrawerDefinition(
                    zone.moddev.mc.lib.gui.drawer.BuiltinDrawers.STATS, "stats",
                    zone.moddev.mc.lib.gui.drawer.DrawerPlacement
                        .top(zone.moddev.mc.lib.gui.model.Edge.LEFT, 40))));
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        MaterialExtensions.GLOBAL.events().registerCallback(AnimalGym.id("trap_release"),
            MaterialEventTypes.GUI_CLICK, click -> {
                if (click.menu().blockEntity instanceof MobTrapEntity trap)
                    trap.release(click.player());
            });
        MaterialExtensions.GLOBAL.events().registerCallback(AnimalGym.id("trap_pickup"),
            MaterialEventTypes.GUI_CLICK, click -> {
                if (click.menu().blockEntity instanceof MobTrapEntity trap)
                    trap.pickUp(click.player());
            });
    }
}

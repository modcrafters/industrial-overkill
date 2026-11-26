package net.modcrafters.minecraft.industrialoverkill.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;

/** The half-block above a machine reserves its roof equipment and owns no contents. */
@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID)
public final class AgriDockBlock extends Block {
    private static final ResourceKey<Block> CONTROLLER = ResourceKey.create(Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath(IndustrialOverkill.MOD_ID, "agri_brain"));
    private static final ResourceKey<Block> RANCH = ResourceKey.create(Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath(IndustrialOverkill.MOD_ID, "ranch_controller"));
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IndustrialOverkill.MOD_ID);
    private static final java.util.function.Supplier<AgriDockBlock> DOCK = registerRoof("agri_brain_dock", CONTROLLER);
    private static final java.util.function.Supplier<AgriDockBlock> ANTENNA = registerRoof("ranch_antenna_mount", RANCH);
    // Reserve the full half-block even when the animated equipment moves away.
    private static final VoxelShape LANDING_BAY = box(0, 0, 0, 16, 8, 16);
    private final ResourceKey<Block> controller;

    private AgriDockBlock(ResourceKey<Block> controller) {
        super(Properties.of().strength(3.5f).sound(SoundType.METAL).forceSolidOn().noOcclusion().noLootTable()
            .pushReaction(PushReaction.BLOCK));
        this.controller = controller;
    }

    private static java.util.function.Supplier<AgriDockBlock> registerRoof(String id, ResourceKey<Block> controller) {
        return BLOCKS.register(id, () -> new AgriDockBlock(controller));
    }

    public static void register(IEventBus bus) { BLOCKS.register(bus); }

    public static boolean isController(BlockState state) { return state.is(CONTROLLER); }

    public static boolean hasRoofExtension(BlockState state) { return state.is(CONTROLLER) || state.is(RANCH); }

    public static boolean isRanchMount(BlockState state) { return state.is(ANTENNA.get()); }

    private static BlockState roof(BlockState controller) {
        return (controller.is(RANCH) ? ANTENNA : DOCK).get().defaultBlockState();
    }

    public static boolean hasRoom(Level level, BlockPos base, BlockState controller) {
        var above = base.above();
        return !level.isOutsideBuildHeight(above) && level.isEmptyBlock(above)
            && level.isUnobstructed(roof(controller), above, CollisionContext.empty());
    }

    public static void install(Level level, BlockPos base, BlockState controller) {
        if (!level.isClientSide && !level.restoringBlockSnapshots && !level.isOutsideBuildHeight(base.above())
            && level.isEmptyBlock(base.above()))
            level.setBlockAndUpdate(base.above(), roof(controller));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return LANDING_BAY;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
        BlockPos pos, BlockPos neighborPos) {
        return side == Direction.DOWN && !neighbor.is(controller) ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
        BlockHitResult hit) {
        var base = pos.below();
        var controller = level.getBlockState(base);
        return controller.is(this.controller) ? controller.useWithoutItem(level, player, hit.withPosition(base)) : InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
        Player player, InteractionHand hand, BlockHitResult hit) {
        var base = pos.below();
        var controller = level.getBlockState(base);
        return controller.is(this.controller) ? controller.useItemOn(stack, level, player, hand, hit.withPosition(base))
            : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var base = level.getBlockState(pos.below());
        return base.is(controller) ? new ItemStack(base.getBlock()) : ItemStack.EMPTY;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breakDock(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof AgriDockBlock roof) || !(event.getPlayer() instanceof ServerPlayer player)) return;
        var base = event.getPos().below();
        if (!event.getLevel().getBlockState(base).is(roof.controller)) return;
        event.setCanceled(true);
        // Use the normal controller break path, including permissions, tool wear and inventory drops.
        player.gameMode.destroyBlock(base);
    }
}

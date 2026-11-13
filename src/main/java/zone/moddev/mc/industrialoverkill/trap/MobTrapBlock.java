package zone.moddev.mc.industrialoverkill.trap;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;

public final class MobTrapBlock extends BaseEntityBlock {
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");
    public MobTrapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OCCUPIED, false)
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(MobTrapBlock::new);
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OCCUPIED, BlockStateProperties.HORIZONTAL_FACING);
    }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
            context.getHorizontalDirection().getOpposite());
    }
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
        CollisionContext context) {
        return Block.box(0, 0, 0, 16, state.getValue(OCCUPIED) ? 16 : 3, 16);
    }
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobTrapEntity(pos, state);
    }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
        BlockEntityType<T> type) {
        return level.isClientSide || type != MobTrap.TYPE.get()
            ? null
            : (world, pos, block, entity) -> ((MobTrapEntity) entity).serverTick();
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
        Player player, BlockHitResult hit) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof MobTrapEntity trap))
            return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            trap.pickUp(player);
            return InteractionResult.CONSUME;
        }
        return ScreenRuntime.open(player, trap, pos)
            ? InteractionResult.CONSUME
            : InteractionResult.PASS;
    }
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
        Player player) {
        if (!level.isClientSide && player.isCreative()
            && level.getBlockEntity(pos) instanceof MobTrapEntity trap && !trap.occupied())
            trap.take();
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
        boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
            && level.getBlockEntity(pos) instanceof MobTrapEntity trap)
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5,
                pos.getZ() + .5, trap.take());
        super.onRemove(state, level, pos, replacement, moved);
    }
}

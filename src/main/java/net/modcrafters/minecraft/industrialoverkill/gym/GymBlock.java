package net.modcrafters.minecraft.industrialoverkill.gym;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import zone.moddev.mc.lib.machine.neoforge.MultiblockBlock;

public final class GymBlock extends MultiblockBlock {
    public static final BooleanProperty END = BooleanProperty.create("end");
    public enum Kind {
        CONTROLLER, BELT, POST, BEAM
    }
    private final Kind kind;
    public GymBlock(Properties properties, Kind kind) {
        super(properties.pushReaction(PushReaction.BLOCK),
            () -> kind == Kind.CONTROLLER
                ? AnimalGym.CONTROLLER_TYPE.get()
                : AnimalGym.PART_TYPE.get(),
            kind == Kind.CONTROLLER);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(END, false));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(END);
    }
    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return kind == Kind.BELT;
    }
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        var lights = kind == Kind.BELT ? level.getAuxLightManager(pos) : null;
        return lights == null ? 0 : lights.getLightAt(pos);
    }
    @Override
    protected boolean isSignalSource(BlockState state) {
        return kind == Kind.CONTROLLER;
    }
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos,
        Direction side) {
        return kind == Kind.CONTROLLER && level.getBlockEntity(pos) instanceof AnimalGymEntity gym
            && gym.structureReady() && gym.screenRedstone().connects(side);
    }
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return kind == Kind.CONTROLLER && level.getBlockEntity(pos) instanceof AnimalGymEntity gym
            && gym.structureReady() ? gym.screenRedstone().signal(side) : 0;
    }
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
        CollisionContext context) {
        return switch (kind) {
            case CONTROLLER -> Block.box(0, 0, 0, 16, 16, 16);
            case BELT -> Block.box(0, 0, 0, 16, 4, 16);
            case POST -> Block.box(5, 0, 5, 11, 16, 11);
            case BEAM -> state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                .getAxis() == net.minecraft.core.Direction.Axis.Z
                    ? Block.box(0, 0, 5, 16, 5, 11)
                    : Block.box(5, 0, 0, 11, 5, 16);
        };
    }
}

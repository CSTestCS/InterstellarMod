package com.cstestcs.interstellar.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The bottom of every spacecraft. Retracted (a thin pad under the hull) while flying, extended (struts down to the
 * ground) once landed.
 */
public class LandingGearBlock extends Block {
    public static final BooleanProperty EXTENDED = BooleanProperty.create("extended");
    private static final VoxelShape EXTENDED_SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape RETRACTED_SHAPE = Block.box(0, 10, 0, 16, 16, 16);

    public LandingGearBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(EXTENDED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EXTENDED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(EXTENDED) ? EXTENDED_SHAPE : RETRACTED_SHAPE;
    }
}

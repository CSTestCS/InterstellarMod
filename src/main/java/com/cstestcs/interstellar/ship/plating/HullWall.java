package com.cstestcs.interstellar.ship.plating;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Hull plating wall. Sneak while placing to lay it on its side (see {@link Tilt}).
 */
public class HullWall extends WallBlock implements EntityBlock, Platable {

    public HullWall(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(Tilt.TILT, Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction tilt = Tilt.forPlacement(ctx);
        if (tilt == Direction.UP) return super.getStateForPlacement(ctx);
        return connect(defaultBlockState().setValue(Tilt.TILT, tilt).setValue(WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).is(Fluids.WATER)), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
            BlockState neighbour, RandomSource random) {
        if (!Tilt.tilted(state)) return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return connect(state, level, pos);
    }

    /** Arms of a sideways shape, in its own frame: towards the same shape lying the same way, or any solid face. */
    private BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        Direction tilt = Tilt.of(state);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Direction w = Tilt.toWorld(tilt, d);
            BlockPos np = pos.relative(w);
            BlockState n = level.getBlockState(np);
            boolean joined = n.getBlock() == this && Tilt.of(n) == tilt || n.isFaceSturdy(level, np, w.getOpposite());
            state = state.setValue(PROPERTY_BY_DIRECTION.get(d), joined ? WallSide.LOW : WallSide.NONE);
        }
        boolean ns = state.getValue(NORTH) != WallSide.NONE && state.getValue(SOUTH) != WallSide.NONE;
        boolean ew = state.getValue(EAST) != WallSide.NONE && state.getValue(WEST) != WallSide.NONE;
        boolean anyNs = state.getValue(NORTH) != WallSide.NONE || state.getValue(SOUTH) != WallSide.NONE;
        boolean anyEw = state.getValue(EAST) != WallSide.NONE || state.getValue(WEST) != WallSide.NONE;
        return state.setValue(UP, !(ns && !anyEw || ew && !anyNs));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult r = Plating.use(stack, state, level, pos, player, hit);
        return r != null ? r : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }


    // ---- lying on its side (see Tilt): the upright shape, turned

    private static BlockState upright(BlockState state) {
        return state.setValue(Tilt.TILT, Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Tilt.tilted(state) ? Tilt.shape(super.getShape(upright(state), level, pos, context), Tilt.of(state)) : super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Tilt.tilted(state) ? Tilt.shape(super.getCollisionShape(upright(state), level, pos, context), Tilt.of(state))
                : super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Tilt.tilted(state) ? Tilt.shape(super.getVisualShape(upright(state), level, pos, context), Tilt.of(state))
                : super.getVisualShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Tilt.tilted(state) ? Tilt.shape(super.getInteractionShape(upright(state), level, pos), Tilt.of(state)) : super.getInteractionShape(state, level, pos);
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Tilt.tilted(state) ? Tilt.shape(super.getBlockSupportShape(upright(state), level, pos), Tilt.of(state)) : super.getBlockSupportShape(state, level, pos);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return Tilt.tilted(state) ? Tilt.shape(super.getOcclusionShape(upright(state)), Tilt.of(state)) : super.getOcclusionShape(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return Tilt.rotate(super.rotate(state, rotation), rotation);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return Tilt.mirror(super.mirror(state, mirror), mirror);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Tilt.TILT);
    }

    // ---- plating (see Plating)

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlatedBlockEntity(pos, state);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockState plating = Plating.get(level, pos);
        return plating != null ? Plating.destroyProgress(plating, player, level, pos) : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return Plating.explosionResistance(level, pos, explosion, super.getExplosionResistance(state, level, pos, explosion));
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return Plating.canHarvest(level, pos, player, super.canHarvestBlock(state, level, pos, player));
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return Plating.sound(level, pos, entity, super.getSoundType(state, level, pos, entity));
    }

    @Override
    public MapColor getMapColor(BlockState state, BlockGetter level, BlockPos pos, MapColor defaultColor) {
        return Plating.mapColor(level, pos, super.getMapColor(state, level, pos, defaultColor));
    }
}

package com.cstestcs.interstellar.ship.plating;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Hull plating door (opens by hand). Sneak while placing to lay it on its side (see {@link Tilt}): its second half
 * then goes beside it instead of above. Both halves share one plating.
 */
public class HullDoor extends DoorBlock implements EntityBlock, Platable {

    public HullDoor(Properties properties) {
        super(HullShapes.HULL, properties);
        registerDefaultState(defaultBlockState().setValue(Tilt.TILT, Direction.UP));
    }

    @Override
    public boolean dropsPlating(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public BlockPos partner(BlockState state, BlockPos pos) {
        Direction up = Tilt.of(state);
        return pos.relative(state.getValue(HALF) == DoubleBlockHalf.LOWER ? up : up.getOpposite());
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction tilt = Tilt.forPlacement(ctx);
        if (tilt == Direction.UP) return super.getStateForPlacement(ctx);
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos(), other = pos.relative(tilt);
        if (level.isOutsideBuildHeight(other) || !level.getBlockState(other).canBeReplaced(ctx)) return null;
        boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(other);
        return defaultBlockState().setValue(Tilt.TILT, tilt).setValue(FACING, Tilt.lookingLocal(ctx, tilt)).setValue(HINGE, DoorHingeSide.LEFT)
                .setValue(POWERED, powered).setValue(OPEN, powered).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        if (!Tilt.tilted(state)) {
            super.setPlacedBy(level, pos, state, by, stack);
            return;
        }
        level.setBlockAndUpdate(pos.relative(Tilt.of(state)), state.setValue(HALF, DoubleBlockHalf.UPPER));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
            BlockState neighbour, RandomSource random) {
        if (!Tilt.tilted(state)) return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
        DoubleBlockHalf half = state.getValue(HALF);
        Direction toOther = half == DoubleBlockHalf.LOWER ? Tilt.of(state) : Tilt.of(state).getOpposite();
        if (direction != toOther) return state;
        return neighbour.getBlock() == this && neighbour.getValue(HALF) != half && Tilt.of(neighbour) == Tilt.of(state)
                ? neighbour.setValue(HALF, half) : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Tilt.tilted(state) || super.canSurvive(state, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        if (!Tilt.tilted(state)) {
            super.neighborChanged(state, level, pos, block, orientation, moved);
            return;
        }
        boolean signal = level.hasNeighborSignal(pos) || level.hasNeighborSignal(partner(state, pos));
        if (defaultBlockState().is(block) || signal == state.getValue(POWERED)) return;
        if (signal != state.getValue(OPEN)) {
            level.playSound(null, pos, signal ? type().doorOpen() : type().doorClose(), SoundSource.BLOCKS, 1f, level.getRandom().nextFloat() * 0.1f + 0.9f);
            level.gameEvent(null, signal ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        }
        level.setBlock(pos, state.setValue(POWERED, signal).setValue(OPEN, signal), Block.UPDATE_CLIENTS);
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

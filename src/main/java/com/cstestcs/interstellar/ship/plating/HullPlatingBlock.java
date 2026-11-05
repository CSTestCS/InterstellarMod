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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Hull plating: a plain full block, plated with any other full block it looks exactly like that block.
 */
public class HullPlatingBlock extends Block implements EntityBlock, Platable {

    public HullPlatingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult r = Plating.use(stack, state, level, pos, player, hit);
        return r != null ? r : super.useItemOn(stack, state, level, pos, player, hand, hit);
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

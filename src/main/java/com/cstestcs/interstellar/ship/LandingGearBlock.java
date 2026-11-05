package com.cstestcs.interstellar.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.cstestcs.interstellar.ship.plating.Platable;
import com.cstestcs.interstellar.ship.plating.PlatedBlockEntity;
import com.cstestcs.interstellar.ship.plating.Plating;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The bottom of every spacecraft. Retracted (a thin pad under the hull) while flying, extended (struts down to the
 * ground) once landed.
 */
public class LandingGearBlock extends Block implements EntityBlock, Platable {
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

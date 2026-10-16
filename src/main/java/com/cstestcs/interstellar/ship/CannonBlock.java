package com.cstestcs.interstellar.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Spacecraft cannon. Points the way it is placed (any of six directions) and fires whatever is loaded into it: a gun
 * from the gunsmith (its projectiles and stats), a bow, crossbow or trident, or projectiles such as arrows, fire
 * charges, TNT, snowballs, wind charges, fireworks or splash potions (consumed one per shot). Its barrel is as thick
 * as its firepower, from a thin rod for arrows up to a full block for TNT and big guns. Empty cannons do not fire.
 * <p>
 * Right-click with a weapon or ammunition to load it; shift-right-click with an empty hand to take it out. Placed in
 * the world (not built into a ship), a redstone pulse fires it once.
 */
public class CannonBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 3);
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][6];

    static {
        int[] thick = { 4, 8, 12, 16 };
        for (int s = 0; s < 4; s++) {
            double lo = (16 - thick[s]) / 2.0, hi = 16 - lo;
            for (Direction d : Direction.values()) {
                SHAPES[s][d.ordinal()] = switch (d.getAxis()) {
                    case X -> Block.box(0, lo, lo, 16, hi, hi);
                    case Y -> Block.box(lo, 0, lo, hi, 16, hi);
                    case Z -> Block.box(lo, lo, 0, hi, hi, 16);
                };
            }
        }
    }

    public CannonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SIZE, 0).setValue(TRIGGERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIZE, TRIGGERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Faces the player who places it, like a dispenser (sneak to aim it the way you look instead).
        Direction look = ctx.getNearestLookingDirection();
        return defaultBlockState().setValue(FACING, ctx.isSecondaryUseActive() ? look : look.getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(SIZE)][state.getValue(FACING).ordinal()];
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Outside a ship, a redstone pulse fires one shot (like a dispenser). */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
            net.minecraft.world.level.redstone.@org.jspecify.annotations.Nullable Orientation orientation, boolean moved) {
        boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
        boolean triggered = state.getValue(TRIGGERED);
        if (powered && !triggered) {
            level.scheduleTick(pos, this, 2);
            level.setBlock(pos, state.setValue(TRIGGERED, true), Block.UPDATE_CLIENTS);
        } else if (!powered && triggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof CannonBlockEntity cannon)) return;
        ItemStack load = cannon.weapon();
        if (load.isEmpty()) {
            level.levelEvent(1001, pos, 0); // dispenser "click": nothing loaded
            return;
        }
        Direction facing = state.getValue(FACING);
        net.minecraft.world.phys.Vec3 dir = net.minecraft.world.phys.Vec3.atLowerCornerOf(facing.getUnitVec3i());
        net.minecraft.world.phys.Vec3 muzzle = net.minecraft.world.phys.Vec3.atCenterOf(pos).add(dir.scale(0.75));
        if (load.is(net.minecraft.world.item.Items.TNT)) muzzle = muzzle.add(0, 0.5, 0); // TNT spawns half a block lower
        if (ShipWeapons.fire(level, null, muzzle, dir, load, 1f) && ShipWeapons.consumesAmmo(load)) {
            ItemStack rest = load.copy();
            rest.shrink(1);
            cannon.setWeapon(rest.isEmpty() ? ItemStack.EMPTY : rest);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CannonBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !ShipWeapons.canLoad(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof CannonBlockEntity cannon)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack loaded = cannon.weapon();
        if (!loaded.isEmpty() && !ItemStack.isSameItemSameComponents(loaded, stack)) {
            player.sendOverlayMessage(Component.translatable("message.interstellar.cannon.occupied", loaded.getHoverName()));
            return InteractionResult.FAIL;
        }
        int room = loaded.isEmpty() ? stack.getMaxStackSize() : loaded.getMaxStackSize() - loaded.getCount();
        int take = ShipWeapons.consumesAmmo(stack) ? Math.min(room, stack.getCount()) : (loaded.isEmpty() ? 1 : 0);
        if (take <= 0) return InteractionResult.FAIL;
        ItemStack put = stack.copyWithCount((loaded.isEmpty() ? 0 : loaded.getCount()) + take);
        if (!player.getAbilities().instabuild) stack.shrink(take);
        cannon.setWeapon(put);
        level.playSound(null, pos, SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.BLOCKS, 0.8f, 1.0f);
        player.sendOverlayMessage(Component.translatable("message.interstellar.cannon.loaded", put.getHoverName(), put.getCount()));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CannonBlockEntity cannon) || cannon.weapon().isEmpty()) return InteractionResult.PASS;
        if (!player.isSecondaryUseActive()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.interstellar.cannon.holds", cannon.weapon().getHoverName(), cannon.weapon().getCount()));
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            ItemStack out = cannon.weapon();
            cannon.setWeapon(ItemStack.EMPTY);
            if (!player.getInventory().add(out)) net.minecraft.world.Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), out);
        }
        return InteractionResult.SUCCESS;
    }
}

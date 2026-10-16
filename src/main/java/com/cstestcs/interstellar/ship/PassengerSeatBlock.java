package com.cstestcs.interstellar.ship;

import com.cstestcs.interstellar.InterstellarConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A passenger seat. Players right-click to sit. Mobs sit down by themselves when they touch a free seat, and nearby
 * idle mobs walk over to one (checked once per second per seat, only while a player is nearby). Shift-right-click
 * collapses the seat, which throws its rider off and keeps everyone off until a player right-clicks it open again.
 * Seated mobs come along when the craft takes off. A seat next to the control panel is the pilot's seat.
 */
public class PassengerSeatBlock extends ShipPartBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty COLLAPSED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("collapsed");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 8, 15);
    private static final VoxelShape COLLAPSED_SHAPE = Block.box(1, 0, 1, 15, 3, 15);
    private static final int CHECK_INTERVAL = 20;

    public PassengerSeatBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(COLLAPSED, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLLAPSED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(COLLAPSED) ? COLLAPSED_SHAPE : SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (player.isSecondaryUseActive()) {
            if (!state.getValue(COLLAPSED)) {
                server.setBlock(pos, state.setValue(COLLAPSED, true), Block.UPDATE_ALL);
                for (SeatEntity seat : server.getEntitiesOfClass(SeatEntity.class, new AABB(pos))) seat.ejectPassengers();
            }
        } else if (state.getValue(COLLAPSED)) {
            server.setBlock(pos, state.setValue(COLLAPSED, false), Block.UPDATE_ALL);
        } else if (!occupied(server, pos) && !player.isPassenger()) {
            SeatEntity.seat(server, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) level.scheduleTick(pos, this, CHECK_INTERVAL);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, CHECK_INTERVAL);
        if (state.getValue(COLLAPSED) || occupied(level, pos) || !level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 16)) return;
        // Touching the seat: sit down.
        List<Mob> touching = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(0.3), m -> !m.isPassenger() && SeatEntity.wantsToSit(m));
        if (!touching.isEmpty()) {
            Mob mob = touching.get(0);
            if (mob.isLeashed()) mob.dropLeash();
            SeatEntity.seat(level, pos, mob);
            return;
        }
        // Otherwise the nearest idle mob walks over to it.
        int r = InterstellarConfig.SERVER.seatPickupRadius.get();
        net.minecraft.world.phys.Vec3 centre = net.minecraft.world.phys.Vec3.atBottomCenterOf(pos);
        Mob nearest = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(r), m -> !m.isPassenger() && SeatEntity.wantsToSit(m)).stream()
                .min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(centre))).orElse(null);
        if (nearest instanceof net.minecraft.world.entity.PathfinderMob walker && walker.getNavigation().isDone()) {
            walker.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
        }
    }

    static boolean occupied(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(SeatEntity.class, new AABB(pos), e -> !e.getPassengers().isEmpty()).isEmpty();
    }
}

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
 * A passenger seat. Players right-click to sit; leashed or recruited mobs brought close sit down by themselves
 * (checked once per second per seat, only while a player is nearby). Seated mobs come along when the ship takes off.
 */
public class PassengerSeatBlock extends ShipPartBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 8, 15);
    private static final int CHECK_INTERVAL = 20;

    public PassengerSeatBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && !occupied(server, pos) && !player.isPassenger()) {
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
        if (occupied(level, pos) || !level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 16)) return;
        int r = InterstellarConfig.SERVER.seatPickupRadius.get();
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(r), m -> !m.isPassenger() && SeatEntity.wantsToSit(m));
        if (!mobs.isEmpty()) {
            Mob mob = mobs.get(0);
            if (mob.isLeashed()) mob.dropLeash();
            SeatEntity.seat(level, pos, mob);
        }
    }

    static boolean occupied(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(SeatEntity.class, new AABB(pos), e -> !e.getPassengers().isEmpty()).isEmpty();
    }
}

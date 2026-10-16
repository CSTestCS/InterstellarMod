package com.cstestcs.interstellar.ship;

import com.cstestcs.interstellar.race.RaceData;
import com.cstestcs.interstellar.registry.ModAttachments;
import com.cstestcs.interstellar.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible helper that lets players and mobs sit on a {@link PassengerSeatBlock} while the ship is blocks.
 * It removes itself as soon as it is empty or its seat block is gone.
 */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static boolean seat(ServerLevel level, BlockPos pos, Entity rider) {
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.setPos(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5);
        level.addFreshEntity(seat);
        if (rider.isPassenger()) rider.stopRiding();
        return rider.startRiding(seat, true, true);
    }

    /**
     * Mobs that take a free seat: anything calm enough (not fighting, not a monster unless recruited) and small
     * enough to sit.
     */
    public static boolean wantsToSit(Mob mob) {
        if (!mob.isAlive() || mob.isAggressive() || mob.getTarget() != null || mob.isPassenger() || mob.isVehicle()) return false;
        if (mob.getBbHeight() > 2.6f || mob.getBbWidth() > 1.6f) return false;
        RaceData race = mob.getData(ModAttachments.RACE);
        if (mob instanceof net.minecraft.world.entity.monster.Enemy && !race.isRecruited()) return false;
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(ValueInput in) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        BlockPos at = BlockPos.containing(getX(), getY(), getZ());
        net.minecraft.world.level.block.state.BlockState below = level().getBlockState(at);
        if (getPassengers().isEmpty() || !(below.getBlock() instanceof PassengerSeatBlock) || below.getValue(PassengerSeatBlock.COLLAPSED)) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }
}

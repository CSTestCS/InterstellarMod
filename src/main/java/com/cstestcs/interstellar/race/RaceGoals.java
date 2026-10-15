package com.cstestcs.interstellar.race;

import com.cstestcs.interstellar.registry.ModAttachments;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Small AI goals added to raced mobs. */
public final class RaceGoals {
    private RaceGoals() {}

    static @Nullable Player owner(PathfinderMob mob) {
        UUID id = mob.getData(ModAttachments.RACE).owner().orElse(null);
        return id == null || !(mob.level() instanceof ServerLevel level) ? null : level.getPlayerByUUID(id);
    }

    /** Recruited mobs follow the player who won them over, teleporting along if left far behind. */
    public static final class FollowRecruiter extends Goal {
        private final PathfinderMob mob;
        private @Nullable Player owner;
        private int recalc;

        public FollowRecruiter(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            owner = owner(mob);
            return owner != null && !mob.isPassenger() && !mob.isLeashed() && mob.distanceToSqr(owner) > 36 && mob.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && !mob.isPassenger() && mob.distanceToSqr(owner) > 9 && mob.getTarget() == null;
        }

        @Override
        public void tick() {
            if (owner == null) return;
            mob.getLookControl().setLookAt(owner, 10f, mob.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = 10;
            if (mob.distanceToSqr(owner) > 24 * 24 && owner.onGround()) {
                mob.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                mob.getNavigation().stop();
            } else {
                mob.getNavigation().moveTo(owner, 1.1);
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }

    /** Recruited mobs attack whatever attacked their recruiter. */
    public static final class DefendRecruiter extends Goal {
        private final PathfinderMob mob;

        public DefendRecruiter(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Player owner = owner(mob);
            if (owner == null) return false;
            LivingEntity attacker = owner.getLastHurtByMob();
            return attacker != null && attacker != mob && attacker.isAlive() && owner.tickCount - owner.getLastHurtByMobTimestamp() < 100;
        }

        @Override
        public void start() {
            Player owner = owner(mob);
            if (owner != null) mob.setTarget(owner.getLastHurtByMob());
        }
    }
}

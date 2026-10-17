package com.cstestcs.interstellar.alien;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Melee attack that steps aside while the mob holds a gun or a ranged weapon, so the gun and ranged AIs (which keep
 * their distance and strafe) are not overridden by a charge.
 */
public class WeaponMeleeGoal extends MeleeAttackGoal {
    public WeaponMeleeGoal(PathfinderMob mob, double speed) {
        super(mob, speed, false);
    }

    private boolean armedForRange() {
        return GunAttackGoal.hasGun(mob) || RangedItemGoal.hasRanged(mob);
    }

    @Override
    public boolean canUse() {
        return !armedForRange() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !armedForRange() && super.canContinueToUse();
    }
}

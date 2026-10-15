package com.cstestcs.interstellar.alien;

import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.gun.GunItem;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Ranged combat for any mob holding a gun: keeps a comfortable distance, strafes, fires according to the gun's fire
 * mode, and pauses to reload when its magazine is spent. Mobs have unlimited magazines, but not unlimited rounds
 * per magazine.
 */
public class GunAttackGoal extends Goal {
    private final PathfinderMob mob;
    private final double speed;
    private final float range;
    private int cooldown, shotsLeft = -1, reload, seeTime, strafeTime;
    private boolean strafeLeft, strafeBack;

    public GunAttackGoal(PathfinderMob mob, double speed, float range) {
        this.mob = mob;
        this.speed = speed;
        this.range = range;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean hasGun(Mob mob) {
        return mob.getMainHandItem().getItem() instanceof GunItem && GunItem.stats(mob.getMainHandItem()) != null;
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && hasGun(mob);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() || !mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        seeTime = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        ItemStack gun = mob.getMainHandItem();
        GunRegistry.Stats stats = GunItem.stats(gun);
        if (stats == null) return;
        float effectiveRange = (float) Math.min(range, stats.get(GunData.Stat.RANGE) * 0.8);
        double dist = mob.distanceToSqr(target);
        boolean sees = mob.getSensing().hasLineOfSight(target);
        seeTime = sees ? Math.max(0, seeTime) + 1 : Math.min(0, seeTime) - 1;
        if (dist <= effectiveRange * effectiveRange && seeTime >= 10) {
            mob.getNavigation().stop();
            strafeTime++;
        } else {
            mob.getNavigation().moveTo(target, speed);
            strafeTime = -1;
        }
        if (strafeTime >= 20) {
            if (mob.getRandom().nextFloat() < 0.3f) strafeLeft = !strafeLeft;
            if (mob.getRandom().nextFloat() < 0.3f) strafeBack = !strafeBack;
            strafeTime = 0;
        }
        if (strafeTime > -1) {
            if (dist > effectiveRange * effectiveRange * 0.75) strafeBack = false;
            else if (dist < effectiveRange * effectiveRange * 0.25) strafeBack = true;
            mob.getMoveControl().strafe(strafeBack ? -0.5f : 0.5f, strafeLeft ? 0.5f : -0.5f);
        }
        mob.getLookControl().setLookAt(target, 30f, 30f);
        mob.setAggressive(true);

        if (reload > 0) {
            reload--;
            return;
        }
        if (shotsLeft < 0) shotsLeft = stats.magazine();
        if (--cooldown > 0 || !sees || seeTime < 5) return;
        // Aim: look straight at the target before firing.
        mob.lookAt(target, 360f, 360f);
        int burst = stats.fireMode() == GunData.FireMode.BURST ? (int) Math.round(stats.get(GunData.Stat.BURST)) : 1;
        GunItem.fire(mob.level(), mob, gun, stats);
        shotsLeft -= 1;
        // Mobs fire a bit slower than players so automatic weapons stay survivable.
        cooldown = stats.fireMode() == GunData.FireMode.AUTO ? stats.cooldownTicks() + 1 : Math.max(stats.cooldownTicks(), 10);
        if (stats.fireMode() == GunData.FireMode.CHARGE) cooldown += (int) (stats.get(GunData.Stat.CHARGE_TIME) * 20);
        if (burst > 1 && shotsLeft % burst != 0) cooldown = 2;
        if (shotsLeft <= 0) {
            reload = stats.reloadTicks() + 10;
            shotsLeft = -1;
        }
    }
}

package com.cstestcs.interstellar.alien;

import com.cstestcs.interstellar.gun.GunItem;
import com.cstestcs.interstellar.ship.ShipWeapons;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.phys.Vec3;

/**
 * Ranged combat with ordinary weapons for mobs that have no ranged AI of their own (aliens, raced mobs): bows are
 * drawn, crossbows charged, tridents and throwables (snowballs, splash potions, wind charges...) thrown. Keeps its
 * distance and strafes like the gun AI. Mobs never run out of arrows.
 */
public class RangedItemGoal extends Goal {
    private final PathfinderMob mob;
    private final double speed;
    private int cooldown, seeTime, strafeTime, drawing = -1;
    private boolean strafeLeft, strafeBack;

    public RangedItemGoal(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Whether a held item is a ranged weapon this goal knows how to use. */
    public static boolean isRanged(ItemStack s) {
        if (s.isEmpty() || s.getItem() instanceof GunItem) return false;
        return s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem || s.is(Items.TRIDENT) || s.getItem() instanceof ProjectileItem;
    }

    public static boolean hasRanged(Mob mob) {
        return !(mob instanceof RangedAttackMob) && isRanged(mob.getMainHandItem());
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && hasRanged(mob);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        mob.stopUsingItem();
        seeTime = 0;
        drawing = -1;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Ticks to draw or charge before a shot, and range kept from the target. */
    private int drawTime(ItemStack s) {
        if (s.getItem() instanceof BowItem) return 20;
        if (s.getItem() instanceof CrossbowItem) return 25;
        if (s.is(Items.TRIDENT)) return 15;
        return 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || !(mob.level() instanceof ServerLevel level)) return;
        ItemStack weapon = mob.getMainHandItem();
        float range = weapon.is(Items.TRIDENT) || weapon.getItem() instanceof ProjectileItem && !(weapon.getItem() instanceof BowItem) ? 12f : 16f;
        double dist = mob.distanceToSqr(target);
        boolean sees = mob.getSensing().hasLineOfSight(target);
        seeTime = sees ? Math.max(0, seeTime) + 1 : Math.min(0, seeTime) - 1;
        if (dist <= range * range && seeTime >= 10) {
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
            if (dist > range * range * 0.75) strafeBack = false;
            else if (dist < range * range * 0.25) strafeBack = true;
            mob.getMoveControl().strafe(strafeBack ? -0.5f : 0.5f, strafeLeft ? 0.5f : -0.5f);
        }
        mob.getLookControl().setLookAt(target, 30f, 30f);
        mob.setAggressive(true);
        if (--cooldown > 0) return;
        if (!sees || seeTime < 5 || dist > range * range * 2.2) {
            if (drawing >= 0) {
                mob.stopUsingItem();
                drawing = -1;
            }
            return;
        }
        int draw = drawTime(weapon);
        if (drawing < 0) {
            drawing = 0;
            if (draw > 0) mob.startUsingItem(InteractionHand.MAIN_HAND);
        }
        if (++drawing < draw) return;
        // Release: aim at the chest, a little high for distance.
        mob.lookAt(target, 360f, 360f);
        Vec3 eye = mob.getEyePosition();
        Vec3 aim = target.getEyePosition().subtract(0, target.getBbHeight() * 0.3, 0).add(0, Math.sqrt(dist) * 0.06, 0);
        Vec3 dir = aim.subtract(eye).normalize();
        mob.stopUsingItem();
        mob.swing(InteractionHand.MAIN_HAND);
        ShipWeapons.fire(level, mob, eye.add(dir.scale(0.6)), dir, weapon, 1f);
        drawing = -1;
        cooldown = Math.max(10, ShipWeapons.cooldown(weapon)) + mob.getRandom().nextInt(10);
    }
}

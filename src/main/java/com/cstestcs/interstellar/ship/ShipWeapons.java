package com.cstestcs.interstellar.ship;

import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.gun.GunFiring;
import com.cstestcs.interstellar.gun.GunItem;
import com.cstestcs.interstellar.gun.GunProjectile;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** What spacecraft cannons can be loaded with, how big the barrel gets, and how each load fires. */
public final class ShipWeapons {
    private ShipWeapons() {}

    public static boolean canLoad(ItemStack stack) {
        return stack.getItem() instanceof GunItem && GunItem.stats(stack) != null || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof ProjectileItem || stack.is(Items.TNT);
    }

    /** Weapons fire forever; everything else is ammunition used up one per shot. */
    public static boolean consumesAmmo(ItemStack stack) {
        return !(stack.getItem() instanceof GunItem || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem
                || stack.is(Items.TRIDENT));
    }

    /** Barrel size 0 (rod) .. 3 (full block). Empty cannons show the thinnest barrel. */
    public static int firepower(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.TNT)) return 3;
        if (stack.getItem() instanceof GunItem) {
            GunRegistry.Stats stats = GunItem.stats(stack);
            double dmg = stats == null ? 0 : stats.get(GunData.Stat.DAMAGE) * Math.max(1, stats.get(GunData.Stat.PELLETS));
            return dmg >= 18 ? 3 : dmg >= 8 ? 2 : 1;
        }
        if (stack.is(Items.FIRE_CHARGE)) return 2;
        if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || stack.is(Items.TRIDENT) || stack.is(Items.WIND_CHARGE)) return 1;
        return 0;
    }

    /** Ticks between shots. */
    public static int cooldown(ItemStack stack) {
        if (stack.isEmpty()) return 6;
        if (stack.getItem() instanceof GunItem) {
            GunRegistry.Stats stats = GunItem.stats(stack);
            return stats == null ? 10 : Math.max(2, (int) Math.round(1200.0 / Math.max(20, stats.get(GunData.Stat.FIRE_RATE))));
        }
        if (stack.is(Items.TNT)) return 40;
        if (stack.getItem() instanceof CrossbowItem) return 25;
        if (stack.getItem() instanceof BowItem || stack.is(Items.TRIDENT)) return 15;
        if (stack.is(Items.FIRE_CHARGE) || stack.is(Items.WIND_CHARGE)) return 10;
        return 6;
    }

    /**
     * Fires one shot of {@code load} from {@code muzzle} along {@code dir}.
     *
     * @param scale ship scale (shells and guns shrink with the ship in space)
     * @return whether a shot left the barrel (never for an empty cannon)
     */
    public static boolean fire(ServerLevel level, @Nullable LivingEntity shooter, Vec3 muzzle, Vec3 dir, ItemStack load, float scale) {
        if (load.isEmpty()) return false;
        if (load.getItem() instanceof GunItem) {
            GunRegistry.Stats stats = GunItem.stats(load);
            if (stats == null) return false;
            int pellets = (int) Math.max(1, stats.get(GunData.Stat.PELLETS));
            for (int i = 0; i < pellets; i++) {
                Vec3 d = pellets == 1 ? dir : dir.add(level.getRandom().nextGaussian() * 0.04, level.getRandom().nextGaussian() * 0.04,
                        level.getRandom().nextGaussian() * 0.04).normalize();
                GunProjectile.spawn(level, shooter, muzzle, d, stats, GunFiring.color(stats));
            }
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1f, 0.7f);
            return true;
        }
        if (load.is(Items.TNT)) {
            PrimedTnt tnt = new PrimedTnt(level, muzzle.x, muzzle.y - 0.5, muzzle.z, shooter);
            tnt.setDeltaMovement(dir.scale(1.4));
            tnt.setFuse(40);
            level.addFreshEntity(tnt);
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.TNT_PRIMED, SoundSource.NEUTRAL, 1f, 1f);
            return true;
        }
        ItemStack ammo = load;
        float power = 1.6f;
        if (load.getItem() instanceof BowItem) {
            ammo = new ItemStack(Items.ARROW);
            power = 3.0f;
        } else if (load.getItem() instanceof CrossbowItem) {
            ammo = new ItemStack(Items.ARROW);
            power = 3.15f;
        } else if (load.is(Items.TRIDENT)) {
            power = 2.5f;
        } else if (load.getItem() instanceof net.minecraft.world.item.ArrowItem) {
            power = 3.0f;
        }
        if (!(ammo.getItem() instanceof ProjectileItem projectileItem)) return false;
        Direction facing = Direction.getApproximateNearest(dir.x, dir.y, dir.z);
        Projectile p = projectileItem.asProjectile(level, muzzle, ammo.copyWithCount(1), facing);
        if (shooter != null) p.setOwner(shooter);
        if (p instanceof AbstractArrow arrow) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            if (load.getItem() instanceof CrossbowItem) arrow.setCritArrow(true);
        }
        projectileItem.shoot(p, dir.x, dir.y, dir.z, power, 1.0f);
        level.addFreshEntity(p);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, load.getItem() instanceof CrossbowItem ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.DISPENSER_LAUNCH,
                SoundSource.NEUTRAL, 1f, 1f);
        return true;
    }
}

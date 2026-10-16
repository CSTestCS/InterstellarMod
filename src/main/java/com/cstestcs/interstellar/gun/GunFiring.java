package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shot logic shared by players and mobs. Ballistic rounds are hitscan (one ray per pellet, no entities spawned);
 * energy, plasma, needle and explosive rounds are {@link GunProjectile}s so they can be seen and dodged.
 */
public final class GunFiring {
    private GunFiring() {}

    public static void shoot(ServerLevel level, LivingEntity shooter, GunRegistry.Stats stats, int color) {
        RandomSource rng = shooter.getRandom();
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        int pellets = (int) Math.round(stats.get(GunData.Stat.PELLETS));
        double spread = stats.get(GunData.Stat.SPREAD) * (shooter.isCrouching() ? 0.6 : 1.0);
        if (shooter instanceof Player p && p.isUsingItem() && stats.get(GunData.Stat.ZOOM) > 1.5 && p.isCrouching()) spread *= 0.4;
        for (int i = 0; i < pellets; i++) {
            Vec3 dir = spread(look, spread, rng);
            if (stats.ammo() == GunData.AmmoType.BALLISTIC) hitscan(level, shooter, eye, dir, stats, color);
            else GunProjectile.spawn(level, shooter, eye.add(dir.scale(0.6)), dir, stats, color);
        }
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), sound(stats), SoundSource.PLAYERS,
                stats.effects().contains("silent") ? 0.2f : 1.0f, 0.9f + rng.nextFloat() * 0.2f);
    }

    static Vec3 spread(Vec3 dir, double degrees, RandomSource rng) {
        if (degrees <= 0) return dir;
        double r = Math.toRadians(degrees);
        return dir.add(rng.triangle(0, r), rng.triangle(0, r), rng.triangle(0, r)).normalize();
    }

    private static void hitscan(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, GunRegistry.Stats stats, int color) {
        double range = stats.get(GunData.Stat.RANGE);
        boolean pierce = stats.effects().contains("pierce");
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        int hits = 0;
        Vec3 start = from;
        while (hits < (pierce ? 3 : 1)) {
            final Vec3 s = start;
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, shooter, s, end, new AABB(s, end).inflate(1),
                    e -> e.isAlive() && e.isPickable() && !e.isSpectator() && e != shooter && !shooter.isPassengerOfSameVehicle(e), 0.3f);
            if (hit == null) break;
            applyHit(level, shooter, hit.getEntity(), hit.getLocation(), stats, 1.0f);
            start = hit.getLocation().add(dir.scale(0.5));
            hits++;
            if (!pierce) end = hit.getLocation();
        }
        tracer(level, from.add(dir.scale(1.0)), end, color);
        if (block.getType() != HitResult.Type.MISS && hits == 0) {
            level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 3, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private static void tracer(ServerLevel level, Vec3 from, Vec3 to, int color) {
        double len = from.distanceTo(to);
        int n = Mth.clamp((int) (len / 2), 2, 24);
        DustParticleOptions dust = new DustParticleOptions(color, 0.6f);
        for (int i = 0; i < n; i++) {
            Vec3 p = from.lerp(to, i / (double) n);
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** Applies damage and material/part effects to whatever a shot or projectile hit. */
    public static void applyHit(ServerLevel level, LivingEntity shooter, Entity target, Vec3 at, GunRegistry.Stats stats, float multiplier) {
        float damage = (float) stats.get(GunData.Stat.DAMAGE) * multiplier;
        if (target instanceof LivingEntity living && at.y > living.getEyeY() - 0.3) damage *= (float) stats.get(GunData.Stat.HEADSHOT);
        damage *= 1 + (float) stats.get(GunData.Stat.ARMOR_PIERCE) * 0.5f;
        DamageSource source = shooter instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(shooter);
        boolean hurt = target.hurtServer(level, source, damage);
        if (!hurt || !(target instanceof LivingEntity living)) return;
        living.setInvulnerableTime(0); // automatic weapons would otherwise lose most of their damage
        applyEffects(level, shooter, living, at, stats, damage, source);
    }

    /** On-hit effects of the weapon's parts and materials (fire, freezing, poison, chain lightning...). */
    public static void applyEffects(ServerLevel level, LivingEntity shooter, LivingEntity living, Vec3 at, GunRegistry.Stats stats, float damage,
            DamageSource source) {
        Entity target = living;
        int power = 1 + (int) stats.get(GunData.Stat.EFFECT_POWER);
        List<String> fx = stats.effects();
        if (fx.contains("ignite")) living.igniteForSeconds(2f + power);
        if (fx.contains("freeze")) {
            living.setTicksFrozen(Math.min(living.getTicksFrozen() + 40 * power, 300));
            effect(living, MobEffects.SLOWNESS, 40 * power, 1);
        }
        if (fx.contains("poison")) effect(living, MobEffects.POISON, 30 * power, 0);
        if (fx.contains("wither")) effect(living, MobEffects.WITHER, 30 * power, 0);
        if (fx.contains("levitate")) effect(living, MobEffects.LEVITATION, 10 * power, 0);
        if (fx.contains("blind")) effect(living, MobEffects.BLINDNESS, 20 * power, 0);
        if (fx.contains("knockback")) living.push(living.position().subtract(shooter.position()).normalize().scale(0.4 * power));
        if (fx.contains("shatter") && living.getRandom().nextInt(5) == 0) {
            target.hurtServer(level, source, damage * 0.5f);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.2);
        }
        if (fx.contains("chain")) {
            int chained = 0;
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, living.getBoundingBox().inflate(4), e -> e != living && e != shooter && e.isAlive())) {
                near.hurtServer(level, source, damage * 0.4f);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, near.getX(), near.getY(0.5), near.getZ(), 6, 0.2, 0.3, 0.2, 0.1);
                if (++chained >= 2) break;
            }
        }
    }

    private static void effect(LivingEntity e, Holder<MobEffect> effect, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(effect, ticks, amp));
    }

    private static SoundEvent sound(GunRegistry.Stats stats) {
        return switch (stats.ammo()) {
            case BALLISTIC -> stats.get(GunData.Stat.DAMAGE) > 15 ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.FIREWORK_ROCKET_BLAST;
            case PLASMA -> SoundEvents.BLAZE_SHOOT;
            case ENERGY -> SoundEvents.BEACON_DEACTIVATE;
            case NEEDLE -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case EXPLOSIVE -> SoundEvents.FIREWORK_ROCKET_LAUNCH;
        };
    }

    /** Projectile and tracer colour: the barrel's (or receiver's/core's) material colour, else a default per ammo type. */
    public static int color(GunRegistry.Stats stats) {
        for (GunData.Slot s : new GunData.Slot[] { GunData.Slot.CORE, GunData.Slot.BARREL, GunData.Slot.RECEIVER }) {
            Integer c = stats.colors().get(s);
            if (c != null) return c;
        }
        return switch (stats.ammo()) {
            case PLASMA -> 0x39C6FF;
            case ENERGY -> 0xFFE14D;
            case NEEDLE -> 0xE98CFF;
            case EXPLOSIVE -> 0xFF7A1A;
            default -> 0xFFF2B0;
        };
    }
}

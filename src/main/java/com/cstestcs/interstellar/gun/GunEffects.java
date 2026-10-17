package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.gun.EffectCatalog;
import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Every weapon effect ({@link EffectCatalog}). Parts and materials each bring their own, so changing a single
 * option changes how the weapon fights: what a hit does to the target, how hard it hits, how blades swing, how the
 * gun fires and how its rounds travel and land.
 */
@EventBusSubscriber(modid = Interstellar.MODID)
public final class GunEffects {
    /** Damage dealt by an effect (chain, echo, sweep...) must not trigger more effects or multipliers. */
    private static final ThreadLocal<Boolean> SECONDARY = ThreadLocal.withInitial(() -> false);
    private static final List<Delayed> QUEUE = new ArrayList<>();

    private record Delayed(ServerLevel level, long at, Runnable task) {}

    private GunEffects() {}

    public static boolean secondary() {
        return SECONDARY.get();
    }

    /** Runs {@code r} with effect triggering disabled (damage it deals is "secondary"). */
    public static void quietly(Runnable r) {
        boolean was = SECONDARY.get();
        SECONDARY.set(true);
        try {
            r.run();
        } finally {
            SECONDARY.set(was);
        }
    }

    static void later(ServerLevel level, int ticks, Runnable task) {
        synchronized (QUEUE) {
            QUEUE.add(new Delayed(level, level.getGameTime() + ticks, task));
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        List<Delayed> due = new ArrayList<>();
        synchronized (QUEUE) {
            for (Iterator<Delayed> it = QUEUE.iterator(); it.hasNext();) {
                Delayed d = it.next();
                if (d.level.getGameTime() >= d.at) {
                    due.add(d);
                    it.remove();
                }
            }
            if (QUEUE.size() > 4096) QUEUE.clear(); // something is spamming: drop rather than leak
        }
        for (Delayed d : due) quietly(d.task);
    }

    static int power(GunRegistry.Stats stats) {
        return 1 + (int) stats.get(GunData.Stat.EFFECT_POWER);
    }

    // ------------------------------------------------------------------ damage

    /** Multiplier from DAMAGE effects (and lunges) for a hit on {@code target}. */
    public static float damageMultiplier(GunRegistry.Stats stats, LivingEntity attacker, LivingEntity target, boolean melee) {
        float m = 1;
        if (stats.has("execute") && target.getHealth() < target.getMaxHealth() * 0.3f) m *= 1.75f;
        if (stats.has("armor_shred")) m *= 1 + Math.min(0.6f, target.getArmorValue() * 0.035f);
        if (stats.has("volatile")) m *= 1.25f;
        if (stats.has("mark") && target.hasEffect(MobEffects.GLOWING)) m *= 1.3f;
        if (melee && stats.has("lunge") && (attacker.isSprinting() || !attacker.onGround())) m *= 1.4f;
        if (melee && stats.has("slam") && attacker.fallDistance > 1.5) m *= 1.5f;
        return m;
    }

    // ------------------------------------------------------------------ on hit

    /** HIT effects: what the hit does to the target (and, on kills, to the attacker). */
    public static void onHit(ServerLevel level, LivingEntity shooter, LivingEntity living, Vec3 at, GunRegistry.Stats stats, float damage,
            DamageSource source, int color) {
        if (secondary()) return;
        quietly(() -> hit(level, shooter, living, at, stats, damage, source, color));
    }

    private static void hit(ServerLevel level, LivingEntity shooter, LivingEntity living, Vec3 at, GunRegistry.Stats stats, float damage,
            DamageSource source, int color) {
        int power = power(stats);
        var rng = living.getRandom();
        Vec3 away = horizontal(living.position().subtract(shooter.position()));
        if (stats.has("ignite")) living.igniteForSeconds(2f + power);
        if (stats.has("freeze")) {
            living.setTicksFrozen(Math.min(living.getTicksFrozen() + 40 * power, 300));
            effect(living, MobEffects.SLOWNESS, 40 * power, 1);
            level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.02);
        }
        if (stats.has("poison")) effect(living, MobEffects.POISON, 30 * power, 0);
        if (stats.has("wither")) effect(living, MobEffects.WITHER, 30 * power, 0);
        if (stats.has("levitate")) effect(living, MobEffects.LEVITATION, 10 * power, 0);
        if (stats.has("blind")) effect(living, MobEffects.BLINDNESS, 20 * power, 0);
        if (stats.has("slow")) effect(living, MobEffects.SLOWNESS, 40 * power, 2);
        if (stats.has("weaken")) effect(living, MobEffects.WEAKNESS, 60 * power, 0);
        if (stats.has("nausea")) effect(living, MobEffects.NAUSEA, 60 * power, 0);
        if (stats.has("famish")) effect(living, MobEffects.HUNGER, 80 * power, 1);
        if (stats.has("darkness")) {
            effect(living, MobEffects.DARKNESS, 50 * power, 0);
            level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.02);
        }
        if (stats.has("mark")) effect(living, MobEffects.GLOWING, 100, 0);
        if (stats.has("knockback")) push(living, away.scale(0.45 * power).add(0, 0.15, 0));
        if (stats.has("pull")) push(living, away.scale(-0.5 * power).add(0, 0.1, 0));
        if (stats.has("launch")) push(living, new Vec3(0, 0.55 + 0.12 * power, 0));
        if (stats.has("stagger")) {
            living.setDeltaMovement(Vec3.ZERO);
            effect(living, MobEffects.SLOWNESS, 15, 4);
            effect(living, MobEffects.MINING_FATIGUE, 30, 2);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.3, at.z, 5, 0.2, 0.1, 0.2, 0.05);
        }
        if (stats.has("shatter") && rng.nextInt(5) == 0) {
            living.hurtServer(level, source, damage * 0.5f);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.25, 0.25, 0.25, 0.3);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
        if (stats.has("chain")) {
            int chained = 0;
            for (LivingEntity near : others(level, living, shooter, 4)) {
                near.hurtServer(level, source, damage * 0.4f);
                arc(level, living.position().add(0, living.getBbHeight() / 2, 0), near.position().add(0, near.getBbHeight() / 2, 0), color);
                if (++chained >= 1 + power) break;
            }
        }
        if (stats.has("leech")) shooter.heal(Math.max(0.5f, damage * 0.15f));
        if (stats.has("warp") && rng.nextInt(3) == 0) {
            living.randomTeleport(living.getX() + rng.nextInt(9) - 4, living.getY() + rng.nextInt(3), living.getZ() + rng.nextInt(9) - 4, true,
                    s -> true);
            level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 20, 0.3, 0.5, 0.3, 0.3);
        }
        if (stats.has("lightning") && rng.nextInt(6) == 0) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setPos(living.position());
                level.addFreshEntity(bolt);
            }
        }
        if (stats.has("echo")) {
            float echo = damage * 0.35f;
            later(level, 12, () -> {
                if (!living.isAlive()) return;
                living.setInvulnerableTime(0);
                living.hurtServer(level, source, echo);
                level.sendParticles(ParticleTypes.SONIC_BOOM, living.getX(), living.getY(0.5), living.getZ(), 1, 0, 0, 0, 0);
            });
        }
        if (stats.has("bleed")) {
            for (int i = 1; i <= 2 + power; i++) {
                later(level, 15 * i, () -> {
                    if (!living.isAlive()) return;
                    living.setInvulnerableTime(0);
                    living.hurtServer(level, level.damageSources().generic(), 1);
                    level.sendParticles(new DustParticleOptions(0x8A0303, 1f), living.getX(), living.getY(0.6), living.getZ(), 4, 0.2, 0.3, 0.2, 0);
                });
            }
        }
        if (stats.has("disarm") && rng.nextInt(6) == 0 && living instanceof Mob mob && !mob.getMainHandItem().isEmpty()) {
            ItemStack held = mob.getMainHandItem().copy();
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            mob.spawnAtLocation(level, held);
        }
        if (stats.has("shockwave")) {
            for (LivingEntity near : others(level, living, shooter, 3)) {
                near.hurtServer(level, source, damage * 0.25f);
                push(near, horizontal(near.position().subtract(living.position())).scale(0.6).add(0, 0.2, 0));
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, living.getX(), living.getY() + 0.2, living.getZ(), 6, 1.2, 0.05, 1.2, 0);
        }
        if (stats.has("plague")) {
            effect(living, MobEffects.POISON, 40 * power, 1);
            for (LivingEntity near : others(level, living, shooter, 3)) effect(near, MobEffects.POISON, 30 * power, 0);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 15, 1, 0.5, 1, 0);
        }
        boolean killed = !living.isAlive() || living.isDeadOrDying();
        if (killed && stats.has("reaper")) {
            shooter.heal(2 + power);
            effect(shooter, MobEffects.ABSORPTION, 200, 0);
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 6, 0.2, 0.3, 0.2, 0.03);
        }
        if (killed && stats.has("adrenaline")) {
            effect(shooter, MobEffects.SPEED, 80, 1);
            effect(shooter, MobEffects.HASTE, 80, 1);
        }
    }

    // ------------------------------------------------------------------ impacts

    /** IMPACT effects where a round lands, on an entity or a block. */
    public static void impact(ServerLevel level, LivingEntity shooter, Vec3 at, GunRegistry.Stats stats, int color) {
        if (secondary()) return;
        quietly(() -> {
            int power = power(stats);
            if (stats.has("concussive")) {
                for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(2.5),
                        e -> e != shooter && e.isAlive())) {
                    push(near, horizontal(near.position().subtract(at)).scale(0.7).add(0, 0.3, 0));
                }
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4f, 1.6f);
            }
            if (stats.has("cluster")) {
                var rng = level.getRandom();
                for (int i = 0; i < 2 + power; i++) {
                    Vec3 p = at.add(rng.nextGaussian() * 1.8, 0.3, rng.nextGaussian() * 1.8);
                    later(level, 4 + i * 3, () -> level.explode(shooter, p.x, p.y, p.z, 0.9f, net.minecraft.world.level.Level.ExplosionInteraction.NONE));
                }
            }
            if (stats.has("gravity_well")) {
                for (int i = 0; i < 6; i++) {
                    later(level, i * 4, () -> {
                        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(5),
                                e -> e != shooter && e.isAlive())) {
                            push(near, at.subtract(near.position()).normalize().scale(0.25));
                        }
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 12, 1.5, 1.5, 1.5, 0.05);
                    });
                }
            }
        });
    }

    // ------------------------------------------------------------------ melee

    /** MELEE effects after a blade hit lands (sweeps, slams, pursuing lunges, piercing thrusts). */
    public static void melee(ServerLevel level, LivingEntity attacker, LivingEntity target, GunRegistry.Stats stats, float damage, DamageSource source,
            int color) {
        if (secondary()) return;
        quietly(() -> {
            Vec3 facing = horizontal(attacker.getLookAngle());
            if (stats.has("sweep")) {
                for (LivingEntity near : others(level, target, attacker, 2.5)) {
                    if (horizontal(near.position().subtract(attacker.position())).dot(facing) < 0.2) continue; // only in front
                    near.hurtServer(level, source, damage * 0.5f);
                    push(near, facing.scale(0.3));
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 3, 0.6, 0.1, 0.6, 0);
            }
            if (stats.has("lunge")) push(attacker, horizontal(target.position().subtract(attacker.position())).scale(0.45));
            if (stats.has("slam")) {
                boolean falling = attacker.fallDistance > 1.5;
                double r = falling ? 3.5 : 1.8;
                for (LivingEntity near : others(level, target, attacker, r)) {
                    near.hurtServer(level, source, damage * (falling ? 0.7f : 0.3f));
                    push(near, new Vec3(0, falling ? 0.6 : 0.3, 0));
                }
                push(target, new Vec3(0, falling ? 0.6 : 0.3, 0));
                level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(), falling ? 3 : 1, r / 3, 0.1, r / 3, 0);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1f, 0.8f);
            }
            if (stats.has("pierce")) {
                Vec3 behind = target.position().add(facing.scale(1.5));
                for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(behind, behind).inflate(1.2, 1, 1.2),
                        e -> e != target && e != attacker && e.isAlive())) {
                    near.hurtServer(level, source, damage * 0.6f);
                    break;
                }
            }
        });
    }

    /** Parry: a lit parry blade turns aside half of a melee blow from the front and shoves the attacker back. */
    public static float parry(ServerLevel level, LivingEntity defender, Entity attacker, float amount) {
        Vec3 to = horizontal(attacker.position().subtract(defender.position()));
        if (to.dot(horizontal(defender.getLookAngle())) < 0.3) return amount;
        if (attacker instanceof LivingEntity l) push(l, to.scale(0.6).add(0, 0.15, 0));
        level.playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, defender.getX() + to.x * 0.6, defender.getEyeY() - 0.3, defender.getZ() + to.z * 0.6,
                10, 0.15, 0.15, 0.15, 0.2);
        return amount * 0.5f;
    }

    // ------------------------------------------------------------------ firing

    /** FIRE effects when a shot leaves the gun: misfires of volatile cores and underbarrel grenades. */
    static void onFire(ServerLevel level, LivingEntity shooter, GunRegistry.Stats stats, Vec3 eye, Vec3 look) {
        var rng = shooter.getRandom();
        if (stats.has("volatile") && rng.nextInt(16) == 0) {
            quietly(() -> shooter.hurtServer(level, level.damageSources().magic(), 1.5f));
            level.sendParticles(ParticleTypes.LARGE_SMOKE, eye.x + look.x, eye.y + look.y, eye.z + look.z, 6, 0.1, 0.1, 0.1, 0.02);
            level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
        if (stats.has("grenade") && rng.nextInt(6) == 0) {
            Map<GunData.Stat, Double> v = new EnumMap<>(GunData.Stat.class);
            v.put(GunData.Stat.DAMAGE, 8.0);
            v.put(GunData.Stat.VELOCITY, 1.3);
            v.put(GunData.Stat.RANGE, 48.0);
            v.put(GunData.Stat.EFFECT_POWER, 1.0);
            GunRegistry.Stats g = new GunRegistry.Stats(v, GunData.FireMode.SEMI, GunData.AmmoType.EXPLOSIVE, List.of("arc"), Map.of(), "grenade",
                    false, 1, true);
            GunProjectile.spawn(level, shooter, eye.add(look.scale(0.6)).add(0, -0.2, 0), look.add(0, 0.15, 0).normalize(), g, 0x7A8C3A);
            level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
    }

    /** Ammo saver: a quarter of shots (or blade strikes) cost no charge. */
    static boolean saveAmmo(GunRegistry.Stats stats, LivingEntity shooter) {
        return stats.has("ammo_saver") && shooter.getRandom().nextInt(4) == 0;
    }

    // ------------------------------------------------------------------ helpers

    private static List<LivingEntity> others(ServerLevel level, LivingEntity around, LivingEntity attacker, double r) {
        return level.getEntitiesOfClass(LivingEntity.class, around.getBoundingBox().inflate(r),
                e -> e != around && e != attacker && e.isAlive() && !attacker.isAlliedTo(e) && !(e instanceof Player p && p.isSpectator()));
    }

    private static void push(LivingEntity e, Vec3 v) {
        e.push(v);
        e.needsSync = true;
    }

    private static Vec3 horizontal(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1e-6 ? Vec3.ZERO : h.normalize();
    }

    private static void arc(ServerLevel level, Vec3 a, Vec3 b, int color) {
        DustParticleOptions dust = new DustParticleOptions(color, 0.7f);
        int n = Math.max(3, (int) (a.distanceTo(b) * 3));
        var rng = level.getRandom();
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.lerp(b, i / (double) n).add(rng.nextGaussian() * 0.08, rng.nextGaussian() * 0.08, rng.nextGaussian() * 0.08);
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static void effect(LivingEntity e, Holder<MobEffect> effect, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(effect, ticks, amp));
    }
}

package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.registry.ModEntities;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Visible bolt for energy, plasma, needle and explosive weapons, and the shell fired by spacecraft cannons.
 * Short-lived and never saved: a projectile in an unloading chunk simply disappears.
 */
public class GunProjectile extends Projectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(GunProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(GunProjectile.class, EntityDataSerializers.FLOAT);
    private GunRegistry.@Nullable Stats stats;
    private int life = 100;
    private boolean homing;
    private float explosion;
    /** Path effects: entities it may still pass through, block bounces left, and the rest. */
    private int pierceLeft, bounces, age;
    private boolean split, accelerate, arc, phase;
    private float damageMul = 1;
    private double startSpeed;
    private final java.util.Set<Integer> struck = new java.util.HashSet<>();

    public GunProjectile(EntityType<? extends GunProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, @Nullable LivingEntity shooter, Vec3 pos, Vec3 dir, GunRegistry.Stats stats, int color) {
        GunProjectile p = new GunProjectile(ModEntities.GUN_PROJECTILE.get(), level);
        p.setOwner(shooter);
        p.stats = stats;
        p.homing = stats.ammo() == GunData.AmmoType.NEEDLE || stats.has("homing");
        p.explosion = stats.ammo() == GunData.AmmoType.EXPLOSIVE ? (float) Math.min(4, 1 + stats.get(GunData.Stat.EFFECT_POWER)) : 0;
        p.pierceLeft = stats.has("pierce") ? 3 : 0;
        p.bounces = stats.has("ricochet") ? 3 : 0;
        p.split = stats.has("split");
        p.accelerate = stats.has("accelerate");
        p.arc = stats.has("arc");
        p.phase = stats.has("phase");
        double speed = stats.get(GunData.Stat.VELOCITY);
        if (p.accelerate) speed *= 0.5; // starts slow, speeds up
        p.startSpeed = speed;
        p.life = (int) Math.min(200, stats.get(GunData.Stat.RANGE) / Math.max(0.2, speed)) + 2;
        p.setPos(pos);
        p.setDeltaMovement(dir.scale(speed));
        p.entityData.set(COLOR, color);
        p.entityData.set(SIZE, p.explosion > 0 ? 0.35f : p.phase ? 0.26f : 0.18f);
        level.addFreshEntity(p);
    }

    /** A smaller copy flying off at an angle (split rounds). */
    private void spawnChild(ServerLevel level, Vec3 dir) {
        GunProjectile c = new GunProjectile(ModEntities.GUN_PROJECTILE.get(), level);
        c.setOwner(getOwner());
        c.stats = stats;
        c.homing = homing;
        c.explosion = explosion * 0.5f;
        c.pierceLeft = pierceLeft;
        c.bounces = bounces;
        c.accelerate = accelerate;
        c.arc = arc;
        c.phase = phase;
        c.damageMul = damageMul * 0.45f;
        c.startSpeed = startSpeed;
        c.life = life;
        c.setPos(position());
        c.setDeltaMovement(dir.scale(getDeltaMovement().length()));
        c.entityData.set(COLOR, color());
        c.entityData.set(SIZE, size() * 0.7f);
        level.addFreshEntity(c);
    }

    /** Spacecraft cannon shell: fixed stats, damage scaled with the ship. */
    public static void spawnShipShell(ServerLevel level, @Nullable LivingEntity pilot, Vec3 pos, Vec3 dir, float damage, float scale) {
        GunRegistry.Stats stats = new GunRegistry.Stats(Map.of(GunData.Stat.DAMAGE, (double) damage, GunData.Stat.RANGE, 160.0,
                GunData.Stat.VELOCITY, 4.0, GunData.Stat.EFFECT_POWER, 1.0), GunData.FireMode.AUTO, GunData.AmmoType.EXPLOSIVE,
                List.of(), Map.of(), "ship", false, 1, true);
        GunProjectile p = new GunProjectile(ModEntities.GUN_PROJECTILE.get(), level);
        if (pilot != null) p.setOwner(pilot);
        p.stats = stats;
        p.explosion = 1.5f * scale;
        p.life = 60;
        p.setPos(pos);
        p.setDeltaMovement(dir.scale(4.0 * Math.max(scale, 0.2)));
        p.entityData.set(COLOR, 0xFF5522);
        p.entityData.set(SIZE, 0.3f * Math.max(scale, 0.2f));
        level.addFreshEntity(p);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0xFFFFFF);
        builder.define(SIZE, 0.2f);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {}

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (level() instanceof ServerLevel server) {
            if (--life <= 0 || stats == null) {
                discard();
                return;
            }
            age++;
            if (homing) steer();
            if (arc) setDeltaMovement(getDeltaMovement().add(0, -0.045, 0));
            if (accelerate && getDeltaMovement().length() < startSpeed * 4) setDeltaMovement(getDeltaMovement().scale(1.09));
            if (split && age == 5) {
                split = false;
                Vec3 d = getDeltaMovement().normalize();
                Vec3 side = d.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
                side = side.normalize().scale(0.22);
                spawnChild(server, d.add(side).normalize());
                spawnChild(server, d.subtract(side).normalize());
                damageMul *= 0.6f;
            }
            motion = getDeltaMovement();
            HitResult hit;
            if (phase) {
                // Phasing rounds pass through blocks: only entities stop them.
                Vec3 from = position(), to = from.add(motion);
                hit = ProjectileUtil.getEntityHitResult(server, this, from, to, getBoundingBox().expandTowards(motion).inflate(1), this::canHitEntity, 0.3f);
                if (hit == null) hit = net.minecraft.world.phys.BlockHitResult.miss(to, net.minecraft.core.Direction.UP, blockPosition());
            } else hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isRemoved()) return;
                motion = getDeltaMovement();
            }
            server.sendParticles(new DustParticleOptions(color(), phase ? 1.2f : 0.8f), getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            if (accelerate && age % 2 == 0) server.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
    }

    private void steer() {
        if (!(getOwner() instanceof LivingEntity owner)) return;
        LivingEntity target = owner instanceof net.minecraft.world.entity.Mob mob ? mob.getTarget() : null;
        if (target == null) {
            List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6),
                    e -> e != owner && e.isAlive() && !owner.isAlliedTo(e));
            if (!near.isEmpty()) target = near.get(0);
        }
        if (target == null) return;
        Vec3 want = target.getBoundingBox().getCenter().subtract(position()).normalize().scale(getDeltaMovement().length());
        setDeltaMovement(getDeltaMovement().lerp(want, 0.15));
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        return super.canHitEntity(target) && target != owner && !(owner != null && owner.isPassengerOfSameVehicle(target))
                && !(target instanceof GunProjectile) && !struck.contains(target.getId());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level() instanceof ServerLevel server && stats != null) {
            LivingEntity shooter = getOwner() instanceof LivingEntity l ? l : null;
            // Accelerating rounds hit harder the farther they flew.
            float mul = damageMul * (accelerate ? Math.min(1.8f, 1 + age * 0.04f) : 1);
            if (shooter != null) GunFiring.applyHit(server, shooter, result.getEntity(), result.getLocation(), stats, mul);
            else result.getEntity().hurtServer(server, damageSources().magic(), (float) stats.get(GunData.Stat.DAMAGE) * mul);
            struck.add(result.getEntity().getId());
            if (pierceLeft-- > 0) return; // flies on through
            land(server, result.getLocation(), shooter);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (phase) return;
        if (level() instanceof ServerLevel server) {
            if (bounces-- > 0) {
                // Ricochet: mirror the motion off the face it hit and lose a little speed.
                Vec3 m = getDeltaMovement();
                net.minecraft.core.Direction.Axis axis = result.getDirection().getAxis();
                m = new Vec3(axis == net.minecraft.core.Direction.Axis.X ? -m.x : m.x, axis == net.minecraft.core.Direction.Axis.Y ? -m.y : m.y,
                        axis == net.minecraft.core.Direction.Axis.Z ? -m.z : m.z).scale(0.85);
                setDeltaMovement(m);
                setPos(result.getLocation().add(m.normalize().scale(0.05)));
                struck.clear();
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, getX(), getY(), getZ(), 4, 0.05, 0.05, 0.05, 0.1);
                return;
            }
            land(server, result.getLocation(), getOwner() instanceof LivingEntity l ? l : null);
        }
        discard();
    }

    private void land(ServerLevel server, Vec3 at, @Nullable LivingEntity shooter) {
        explode(server, at);
        if (shooter != null && stats != null) GunEffects.impact(server, shooter, at, stats, color());
    }

    private void explode(ServerLevel level, Vec3 at) {
        if (explosion <= 0) return;
        // Players' shots break blocks like TNT; shots from mobs and alien ships follow the mobGriefing rule.
        Level.ExplosionInteraction breaks = getOwner() instanceof net.minecraft.world.entity.player.Player
                ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.MOB;
        level.explode(this, at.x, at.y, at.z, explosion, breaks);
    }
}

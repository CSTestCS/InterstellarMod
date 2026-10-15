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
    private @Nullable GunRegistry.Stats stats;
    private int life = 100;
    private boolean homing;
    private float explosion;

    public GunProjectile(EntityType<? extends GunProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, LivingEntity shooter, Vec3 pos, Vec3 dir, GunRegistry.Stats stats, int color) {
        GunProjectile p = new GunProjectile(ModEntities.GUN_PROJECTILE.get(), level);
        p.setOwner(shooter);
        p.stats = stats;
        p.homing = stats.ammo() == GunData.AmmoType.NEEDLE;
        p.explosion = stats.ammo() == GunData.AmmoType.EXPLOSIVE ? (float) Math.min(4, 1 + stats.get(GunData.Stat.EFFECT_POWER)) : 0;
        double speed = stats.get(GunData.Stat.VELOCITY);
        p.life = (int) Math.min(200, stats.get(GunData.Stat.RANGE) / Math.max(0.2, speed)) + 2;
        p.setPos(pos);
        p.setDeltaMovement(dir.scale(speed));
        p.entityData.set(COLOR, color);
        p.entityData.set(SIZE, p.explosion > 0 ? 0.35f : 0.18f);
        level.addFreshEntity(p);
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
            if (homing) steer();
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isRemoved()) return;
            }
            server.sendParticles(new DustParticleOptions(color(), 0.8f), getX(), getY(), getZ(), 1, 0, 0, 0, 0);
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
                && !(target instanceof GunProjectile);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level() instanceof ServerLevel server && stats != null) {
            LivingEntity shooter = getOwner() instanceof LivingEntity l ? l : null;
            if (shooter != null) GunFiring.applyHit(server, shooter, result.getEntity(), result.getLocation(), stats, 1f);
            else result.getEntity().hurtServer(server, damageSources().magic(), (float) stats.get(GunData.Stat.DAMAGE));
            explode(server, result.getLocation());
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (level() instanceof ServerLevel server) explode(server, result.getLocation());
        discard();
    }

    private void explode(ServerLevel level, Vec3 at) {
        if (explosion <= 0) return;
        level.explode(this, at.x, at.y, at.z, explosion, Level.ExplosionInteraction.NONE);
    }
}

package com.cstestcs.interstellar.ship;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.gun.GunProjectile;
import com.cstestcs.interstellar.registry.ModBlocks;
import com.cstestcs.interstellar.space.SpaceTravel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * A spacecraft in entity form. The entity position is the bottom centre of the control panel block; blocks are
 * stored relative to it in {@link ShipStructure}.
 * <ul>
 *     <li>FLYING: always moves along the pilot's look direction like an infinitely boosted elytra. Interact (held)
 *     raises speed from cruise to boost, attack fires the ship's cannons, sneak halts and turns the ship back into
 *     blocks, snapped to the block grid with orientations preserved.</li>
 *     <li>LANDING: descends straight down; once any block is within two blocks of the ground it realigns, extends
 *     the landing gear and becomes blocks again.</li>
 * </ul>
 * Inside the space dimension halting, landing and dismounting are disabled and the ship plus everyone aboard is
 * scaled down (configurable, 0.1 by default).
 */
public class SpacecraftEntity extends Entity implements IEntityWithComplexSpawn {
    public static final byte MODE_FLYING = 0, MODE_LANDING = 1;
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(SpacecraftEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(SpacecraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(SpacecraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> PILOT_ID = SynchedEntityData.defineId(SpacecraftEntity.class, EntityDataSerializers.INT);
    private static final net.minecraft.resources.Identifier SPACE_SCALE_ID = Interstellar.id("space_scale");

    private ShipStructure structure = ShipStructure.EMPTY;
    /** Seat index per passenger (0 = pilot). Server side only. */
    private final Map<UUID, Integer> seatOf = new HashMap<>();
    /** Players who confirmed the "you will be removed from the spacecraft" prompt. */
    private final Set<UUID> confirmedDismount = new HashSet<>();
    private boolean inputBoost, inputFire;
    private int weaponCooldown;
    private int seatScanTimer;

    public SpacecraftEntity(EntityType<? extends SpacecraftEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public static SpacecraftEntity create(ServerLevel level, ShipStructure structure, Vec3 pos, byte mode) {
        SpacecraftEntity ship = new SpacecraftEntity(com.cstestcs.interstellar.registry.ModEntities.SPACECRAFT.get(), level);
        ship.structure = structure;
        ship.setPos(pos);
        ship.setYRot(structure.baseYaw);
        ship.yRotO = structure.baseYaw;
        ship.setMode(mode);
        ship.setBoundingBox(ship.makeBoundingBox(pos));
        return ship;
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODE, MODE_FLYING);
        builder.define(SCALE, 1f);
        builder.define(SPEED, 0f);
        builder.define(PILOT_ID, -1);
    }

    public ShipStructure structure() {
        return structure;
    }

    public byte mode() {
        return entityData.get(MODE);
    }

    public void setMode(byte mode) {
        entityData.set(MODE, mode);
    }

    public float shipScale() {
        return entityData.get(SCALE);
    }

    public void setShipScale(float scale) {
        entityData.set(SCALE, scale);
        setBoundingBox(makeBoundingBox(position()));
    }

    public boolean inSpace() {
        return SpaceTravel.isSpace(level());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        structure = in.read("structure", ShipStructure.CODEC).orElse(ShipStructure.EMPTY);
        setMode((byte) in.getIntOr("mode", MODE_FLYING));
        entityData.set(SCALE, in.getFloatOr("scale", 1f));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.store("structure", ShipStructure.CODEC, structure);
        out.putInt("mode", mode());
        out.putFloat("scale", shipScale());
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        structure.write(buf);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        structure = ShipStructure.read(buf);
        setBoundingBox(makeBoundingBox(position()));
    }

    // ------------------------------------------------------------------ geometry

    /** Local (structure) offset -> world offset for the current yaw, pitch and scale. */
    public Vec3 toWorld(Vec3 local) {
        return toWorld(local, getYRot(), getXRot(), shipScale());
    }

    public Vec3 toWorld(Vec3 local, float yaw, float pitch, float scale) {
        return local.yRot(structure.baseYaw * Mth.DEG_TO_RAD).xRot(-pitch * Mth.DEG_TO_RAD).yRot(-yaw * Mth.DEG_TO_RAD).scale(scale);
    }

    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        if (structure == null || structure.entries.isEmpty()) return new AABB(pos.x - 0.5, pos.y, pos.z - 0.5, pos.x + 0.5, pos.y + 1, pos.z + 0.5);
        AABB local = structure.localBounds().move(-0.5, 0, -0.5);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 c = new Vec3((i & 1) == 0 ? local.minX : local.maxX, (i & 2) == 0 ? local.minY : local.maxY, (i & 4) == 0 ? local.minZ : local.maxZ);
            Vec3 w = toWorld(c, getYRot(), 0, shipScale());
            minX = Math.min(minX, w.x); minY = Math.min(minY, w.y); minZ = Math.min(minZ, w.z);
            maxX = Math.max(maxX, w.x); maxY = Math.max(maxY, w.y); maxZ = Math.max(maxZ, w.z);
        }
        return new AABB(pos.x + minX, pos.y + minY, pos.z + minZ, pos.x + maxX, pos.y + maxY, pos.z + maxZ);
    }

    @Override
    public void setYRot(float yRot) {
        super.setYRot(yRot);
        if (structure != null) setBoundingBox(makeBoundingBox(position()));
    }

    // ------------------------------------------------------------------ passengers

    public Player pilot() {
        if (level().isClientSide()) {
            return level().getEntity(entityData.get(PILOT_ID)) instanceof Player p && p.getVehicle() == this ? p : null;
        }
        for (Entity e : getPassengers()) if (e instanceof Player p && seatOf.getOrDefault(p.getUUID(), -1) == 0) return p;
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < structure.seatCount();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide()) {
            int seat = passenger instanceof Player && freeSeat(0) ? 0 : firstFreeSeat(1);
            seatOf.put(passenger.getUUID(), Math.max(0, seat));
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        Integer seat = seatOf.remove(passenger.getUUID());
        confirmedDismount.remove(passenger.getUUID());
        if (passenger instanceof LivingEntity living) setSpaceScale(living, false);
        if (seat != null && seat == 0 && !level().isClientSide()) {
            // Pilot left or died: the first player passenger takes over the controls.
            for (Entity e : getPassengers()) {
                if (e instanceof Player p) {
                    seatOf.put(p.getUUID(), 0);
                    p.sendOverlayMessage(Component.translatable("message.interstellar.ship.new_pilot"));
                    break;
                }
            }
        }
    }

    private boolean freeSeat(int index) {
        return !seatOf.containsValue(index);
    }

    private int firstFreeSeat(int from) {
        for (int i = from; i < structure.seatCount(); i++) if (freeSeat(i)) return i;
        return -1;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        int seat = seatOf.getOrDefault(passenger.getUUID(), getPassengers().indexOf(passenger));
        if (seat < 0 || seat >= structure.seatCount()) seat = 0;
        BlockPos s = structure.seats.get(seat);
        double sit = seat == 0 ? 0.0 : 0.3; // seats are lower than the pilot's standing spot
        Vec3 w = toWorld(new Vec3(s.getX(), s.getY() + sit, s.getZ()));
        move.accept(passenger, getX() + w.x, getY() + w.y, getZ() + w.z);
    }

    @Override
    public boolean shouldRiderSit() {
        return false; // pilots stand at the controls; seated mobs get the sitting pose from their seat blocks' height
    }

    public void confirmDismount(ServerPlayer player) {
        if (player.getVehicle() != this || inSpace()) return;
        confirmedDismount.add(player.getUUID());
        player.stopRiding();
    }

    /** Called from the mount event: whether a passenger may leave right now. */
    public boolean allowDismount(Entity passenger) {
        if (isRemoved() || level().isClientSide() && !(passenger instanceof Player)) return true;
        if (inSpace()) return false;
        if (passenger instanceof Player p) {
            if (seatOf.getOrDefault(p.getUUID(), -1) == 0 && !level().isClientSide()) return false; // pilot sneaking = halt
            return confirmedDismount.contains(p.getUUID());
        }
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive() || player.getVehicle() == this) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            // Holding a lead: unload seated mobs onto the lead.
            if (player.getItemInHand(hand).is(Items.LEAD)) {
                for (Entity e : List.copyOf(getPassengers())) {
                    if (e instanceof Mob mob && mob.canBeLeashed()) {
                        mob.stopRiding();
                        mob.setLeashedTo(player, true);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            if (!canAddPassenger(player)) return InteractionResult.FAIL;
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // ships are destroyed by turning them back into blocks
    }

    // ------------------------------------------------------------------ input

    public void receiveInput(ServerPlayer from, boolean boost, boolean fire) {
        if (from != pilot()) return;
        inputBoost = boost;
        inputFire = fire;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            Vec3 v = getDeltaMovement();
            if (v.lengthSqr() > 0) move(MoverType.SELF, v);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (structure.entries.isEmpty()) {
            discard();
            return;
        }
        boolean space = inSpace();
        float wantScale = space ? InterstellarConfig.SERVER.spaceScale.get().floatValue() : 1f;
        if (shipScale() != wantScale) setShipScale(wantScale);
        for (Entity e : getPassengers()) if (e instanceof LivingEntity living) setSpaceScale(living, space);

        Player pilot = pilot();
        entityData.set(PILOT_ID, pilot == null ? -1 : pilot.getId());
        if (mode() == MODE_LANDING && !space) {
            tickLanding(level);
        } else {
            if (mode() == MODE_LANDING) setMode(MODE_FLYING);
            tickFlying(level, pilot, space);
        }
        if (isRemoved()) return;

        if (--seatScanTimer <= 0) {
            seatScanTimer = 20;
            pullMobsIntoSeats(level);
        }
        if (!space && getY() > level.getMaxY() + InterstellarConfig.SERVER.spaceEntryHeight.get()) {
            SpaceTravel.enterSpace(this);
        } else if (space) {
            SpaceTravel.checkPlanetEntry(this);
        }
    }

    private void tickFlying(ServerLevel level, Player pilot, boolean space) {
        double min = InterstellarConfig.SERVER.shipMinSpeed.get(), max = InterstellarConfig.SERVER.shipMaxSpeed.get();
        float speed = entityData.get(SPEED);
        if (pilot == null) {
            if (!space) {
                setMode(MODE_LANDING); // nobody at the controls: descend safely
                return;
            }
            speed *= 0.9f;
        } else {
            // Turn towards where the pilot looks (rate-limited so big ships feel heavy).
            float yaw = Mth.approachDegrees(getYRot(), pilot.getYRot(), 6f);
            float pitch = Mth.approach(getXRot(), Mth.clamp(pilot.getXRot(), -80, 80), 4f);
            setYRot(yaw);
            setXRot(pitch);
            double target = inputBoost ? max : min;
            speed += (float) Mth.clamp(target - speed, -0.08, 0.05);
            if (pilot.isShiftKeyDown() && !space) {
                halt(level, pilot);
                return;
            }
            if (inputFire && weaponCooldown <= 0) fireWeapons(level, pilot);
        }
        if (weaponCooldown > 0) weaponCooldown--;
        entityData.set(SPEED, speed);
        Vec3 dir = Vec3.directionFromRotation(getXRot(), getYRot());
        Vec3 motion = dir.scale(speed * (space ? shipScale() * 4 : 1));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
    }

    private void tickLanding(ServerLevel level) {
        setXRot(Mth.approach(getXRot(), 0, 4f));
        double speed = InterstellarConfig.SERVER.landingSpeed.get();
        if (clearanceBelow(level, 2)) {
            settle(level);
            return;
        }
        Vec3 motion = new Vec3(0, -speed, 0);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (verticalCollision) settle(level);
    }

    /** True when any block of the ship is within {@code distance} blocks above a solid block. */
    private boolean clearanceBelow(ServerLevel level, int distance) {
        int q = quarterTurns();
        Rotation rot = ShipStructure.rotationForQuarterTurns(q);
        BlockPos origin = BlockPos.containing(getX(), getY() + 0.01, getZ());
        Set<BlockPos> own = new HashSet<>();
        for (ShipStructure.Entry e : structure.entries) own.add(origin.offset(e.pos().rotate(rot)));
        for (BlockPos p : own) {
            for (int d = 1; d <= distance; d++) {
                BlockPos below = p.below(d);
                if (own.contains(below)) break;
                if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return true;
            }
        }
        return false;
    }

    private int quarterTurns() {
        return Math.round(Mth.wrapDegrees(getYRot() - structure.baseYaw) / 90f);
    }

    /** Sneak while flying: stop, snap to the grid and become blocks again (gear stays retracted, mid-air). */
    public void halt(ServerLevel level, Player pilot) {
        if (!disassemble(level, false)) {
            pilot.sendOverlayMessage(Component.translatable("message.interstellar.ship.obstructed"));
        }
    }

    /** Landing finished: drop onto the ground with extended gear. */
    private void settle(ServerLevel level) {
        if (!disassemble(level, true)) {
            // Obstructed: keep hovering and let the pilot try somewhere else.
            setMode(MODE_FLYING);
            Player pilot = pilot();
            if (pilot != null) pilot.sendOverlayMessage(Component.translatable("message.interstellar.ship.obstructed"));
        }
    }

    /**
     * Turns the ship back into blocks at the nearest grid position and quarter rotation.
     *
     * @return false if the space is obstructed
     */
    public boolean disassemble(ServerLevel level, boolean landing) {
        int q = quarterTurns();
        Rotation rot = ShipStructure.rotationForQuarterTurns(q);
        BlockPos origin = BlockPos.containing(getX(), Math.round(getY()), getZ());
        if (landing) {
            // Lower the ship (at most 3 blocks) until a landing gear block rests on solid ground.
            for (int drop = 0; drop <= 3; drop++) {
                BlockPos o = origin.below(drop);
                if (structure.obstruction(level, o, rot) != null) break;
                origin = o;
                if (gearOnGround(level, o, rot)) break;
            }
        }
        if (structure.obstruction(level, origin, rot) != null) return false;
        List<Entity> riders = new ArrayList<>(getPassengers());
        Map<UUID, Integer> seats = new HashMap<>(seatOf);
        confirmedDismount.addAll(seats.keySet());
        ejectPassengers();
        structure.place(level, origin, rot, landing);
        // Put everyone back where they sat; seated mobs get seat entities so they keep sitting.
        for (Entity e : riders) {
            int seat = seats.getOrDefault(e.getUUID(), 0);
            BlockPos local = structure.seats.get(Math.min(seat, structure.seatCount() - 1)).rotate(rot);
            BlockPos at = origin.offset(local);
            if (seat > 0 && !(e instanceof Player)) {
                SeatEntity.seat(level, at, e);
            } else {
                e.teleportTo(at.getX() + 0.5, at.getY() + (seat == 0 ? 0.0 : 0.5), at.getZ() + 0.5);
            }
        }
        discard();
        return true;
    }

    private boolean gearOnGround(ServerLevel level, BlockPos origin, Rotation rot) {
        for (ShipStructure.Entry e : structure.entries) {
            if (!(e.state().getBlock() instanceof LandingGearBlock)) continue;
            BlockPos below = origin.offset(e.pos().rotate(rot)).below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return true;
        }
        return false;
    }

    private void fireWeapons(ServerLevel level, Player pilot) {
        if (structure.weapons.isEmpty()) return;
        weaponCooldown = 6;
        Vec3 forward = Vec3.directionFromRotation(getXRot(), getYRot());
        for (ShipStructure.Entry w : structure.weapons) {
            Direction facing = w.state().hasProperty(ShipPartBlock.FACING) ? w.state().getValue(ShipPartBlock.FACING) : Direction.NORTH;
            Vec3 local = new Vec3(w.pos().getX(), w.pos().getY() + 0.5, w.pos().getZ()).add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(0.75));
            Vec3 muzzle = position().add(toWorld(local));
            Vec3 dir = toWorld(Vec3.atLowerCornerOf(facing.getUnitVec3i())).normalize();
            // Cannons fire along their facing, biased towards where the ship flies so spread-out guns converge.
            dir = dir.add(forward).normalize();
            GunProjectile.spawnShipShell(level, pilot, muzzle, dir, 12f * Math.max(0.25f, shipScale()), shipScale());
        }
    }

    /** Leashed or recruited mobs near an empty seat climb aboard. */
    private void pullMobsIntoSeats(ServerLevel level) {
        if (getPassengers().size() >= structure.seatCount()) return;
        int radius = InterstellarConfig.SERVER.seatPickupRadius.get();
        for (int i = 1; i < structure.seatCount(); i++) {
            if (!freeSeat(i)) continue;
            Vec3 seatPos = position().add(toWorld(Vec3.atBottomCenterOf(structure.seats.get(i)).subtract(0.5, 0, 0.5)));
            AABB area = new AABB(seatPos, seatPos).inflate(radius * Math.max(shipScale(), 0.25));
            for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> !m.isPassenger() && SeatEntity.wantsToSit(m))) {
                if (mob.isLeashed()) mob.dropLeash();
                mob.startRiding(this, true, true);
                seatOf.put(mob.getUUID(), i);
                break;
            }
        }
    }

    private static void setSpaceScale(LivingEntity living, boolean on) {
        AttributeInstance scale = living.getAttribute(Attributes.SCALE);
        if (scale == null) return;
        boolean has = scale.getModifier(SPACE_SCALE_ID) != null;
        if (on && !has) {
            double s = InterstellarConfig.SERVER.spaceScale.get();
            scale.addTransientModifier(new AttributeModifier(SPACE_SCALE_ID, s - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!on && has) {
            scale.removeModifier(SPACE_SCALE_ID);
        }
    }

    /** Seat index of a passenger (server side), used when moving the ship between dimensions. */
    public Map<UUID, Integer> seatMap() {
        return seatOf;
    }

    public void restoreSeat(Entity passenger, int seat) {
        seatOf.put(passenger.getUUID(), seat);
    }
}

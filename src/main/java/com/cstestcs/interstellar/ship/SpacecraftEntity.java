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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.serialization.Codec;
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
 * <p>
 * Land vehicles (built on wheels) use DRIVING instead: they roll along the ground like a horse that cannot jump
 * (forward/back keys, steering follows the driver's view), climb one-block steps, and park - turn back into blocks -
 * when the driver gets out while every wheel stands over a vehicle pad. Anywhere else they stay an entity.
 */
public class SpacecraftEntity extends Entity implements IEntityWithComplexSpawn {
    public static final byte MODE_FLYING = 0, MODE_LANDING = 1, MODE_DRIVING = 2;
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
    private int seatScanTimer;
    /** Per-cannon cooldowns and cached loads (index into structure.weapons). */
    private int[] cannonCooldown = new int[0];
    private ItemStack[] cannonLoad = new ItemStack[0];
    /** Seats (by index) that are folded away: nobody sits down there until a player opens them again. */
    private final Set<Integer> collapsedSeats = new HashSet<>();
    /** The driver of a land vehicle got out: park on the next tick if the vehicle stands on vehicle pads. */
    private boolean pendingPark;
    /** Flown by an alien crew (spawned in space): an AI pilot instead of a player. */
    private boolean alienCrewed;
    private int aiTurnTimer, idleTicks;
    /** Lost its pilot in space with nobody to take over: heading for the nearest planet to crash-land there. */
    private boolean crashing;
    private @org.jspecify.annotations.Nullable Vec3 crashTarget;
    private int crashRetarget;
    private double fallSpeed;
    private float aiYaw, aiPitch;

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
        ship.setMode(structure.vehicle ? MODE_DRIVING : mode);
        ship.setBoundingBox(ship.makeBoundingBox(pos));
        ship.initFromStructure();
        return ship;
    }

    /** Folded seats and cannon loads come from the captured blocks. */
    private void initFromStructure() {
        collapsedSeats.clear();
        for (int i = 1; i < structure.seatCount(); i++) {
            BlockPos seat = structure.seats.get(i);
            for (ShipStructure.Entry e : structure.entries) {
                if (e.pos().equals(seat) && e.state().hasProperty(PassengerSeatBlock.COLLAPSED) && e.state().getValue(PassengerSeatBlock.COLLAPSED)) {
                    collapsedSeats.add(i);
                }
            }
        }
        cannonCooldown = new int[structure.weapons.size()];
        cannonLoad = new ItemStack[structure.weapons.size()];
        for (int i = 0; i < cannonLoad.length; i++) cannonLoad[i] = readLoad(structure.weapons.get(i));
    }

    private ItemStack readLoad(ShipStructure.Entry e) {
        if (e.blockEntity().isEmpty() || level().isClientSide()) return ItemStack.EMPTY;
        net.minecraft.nbt.Tag tag = e.blockEntity().get().get(CannonBlockEntity.WEAPON_KEY);
        if (tag == null) return ItemStack.EMPTY;
        return ItemStack.OPTIONAL_CODEC.parse(level().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag)
                .result().orElse(ItemStack.EMPTY);
    }

    /** Writes a cannon's remaining ammunition back into its captured block entity data. */
    private void writeLoad(int i) {
        ShipStructure.Entry e = structure.weapons.get(i);
        if (e.blockEntity().isEmpty()) return;
        var ops = level().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        if (cannonLoad[i].isEmpty()) e.blockEntity().get().remove(CannonBlockEntity.WEAPON_KEY);
        else ItemStack.OPTIONAL_CODEC.encodeStart(ops, cannonLoad[i]).result().ifPresent(t -> e.blockEntity().get().put(CannonBlockEntity.WEAPON_KEY, t));
    }

    public boolean isVehicle() {
        return structure.vehicle;
    }

    public boolean isAlienCrewed() {
        return alienCrewed;
    }

    public void setAlienCrewed(boolean crewed) {
        alienCrewed = crewed;
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
        initFromStructure();
        alienCrewed = in.getBooleanOr("alien_crew", false);
        crashing = in.getBooleanOr("crashing", false);
        in.read("collapsed_seats", Codec.INT.listOf()).ifPresent(l -> {
            collapsedSeats.clear();
            collapsedSeats.addAll(l);
        });
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.store("structure", ShipStructure.CODEC, structure);
        out.putInt("mode", mode());
        out.putFloat("scale", shipScale());
        out.store("collapsed_seats", Codec.INT.listOf(), new ArrayList<>(collapsedSeats));
        out.putBoolean("alien_crew", alienCrewed);
        if (crashing) out.putBoolean("crashing", true);
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
        while (seat > 0 && collapsedSeats.contains(seat)) seat = firstFreeSeat(seat + 1);
            seatOf.put(passenger.getUUID(), Math.max(0, seat));
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        Integer seat = seatOf.remove(passenger.getUUID());
        confirmedDismount.remove(passenger.getUUID());
        // Server only: a dismount the client predicts but the server refuses (in space) must not shrink-reset the
        // client's copy of the attribute, or the passenger would look enormous until the next attribute sync.
        if (!level().isClientSide() && passenger instanceof LivingEntity living) setSpaceScale(living, false);
        if (seat != null && seat == 0 && structure.vehicle && !level().isClientSide()) pendingPark = true;
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
        double sit = seat == 0 && !structure.pilotSeated ? 0.0 : 0.3; // seats are lower than the pilot's standing spot
        Vec3 w = toWorld(new Vec3(s.getX(), s.getY() + sit, s.getZ()));
        move.accept(passenger, getX() + w.x, getY() + w.y, getZ() + w.z);
    }

    @Override
    public boolean shouldRiderSit() {
        return false; // standing pilots stand; riders on seats get the sitting pose from isSeated (render state)
    }

    /** Whether a rider is on a seat block (rather than standing at the controls). Works on both sides. */
    public boolean isSeated(Entity passenger) {
        if (!hasPassenger(passenger)) return false;
        Vec3 local = toLocal(passenger.position().subtract(position()));
        BlockPos at = BlockPos.containing(local.x + 0.5, local.y + 0.05, local.z + 0.5);
        for (int i = structure.pilotSeated ? 0 : 1; i < structure.seatCount(); i++) {
            BlockPos s = structure.seats.get(i);
            if (s.getX() == at.getX() && s.getZ() == at.getZ() && Math.abs(s.getY() - at.getY()) <= 1) return true;
        }
        return false;
    }

    /** Inverse of {@link #toWorld(Vec3)}. */
    public Vec3 toLocal(Vec3 world) {
        float scale = Math.max(1e-4f, shipScale());
        return world.scale(1 / scale).yRot(getYRot() * Mth.DEG_TO_RAD).xRot(getXRot() * Mth.DEG_TO_RAD).yRot(-structure.baseYaw * Mth.DEG_TO_RAD);
    }

    @Override
    public float maxUpStep() {
        return structure.vehicle ? 1.05f : 0f; // vehicles climb one-block steps; nothing else ever jumps
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
        if (structure.vehicle) return true; // vehicles are on the ground: anyone may get out
        if (passenger instanceof Player p) {
            if (seatOf.getOrDefault(p.getUUID(), -1) == 0 && !level().isClientSide()) return false; // pilot sneaking = halt
            return confirmedDismount.contains(p.getUUID());
        }
        return true;
    }

    /**
     * Right-click boards the craft (or opens a folded seat near where you clicked). Shift-right-click folds the seat
     * nearest to where you clicked and throws off whoever sits there.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.getVehicle() == this) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        int seat = seatNear(location);
        if (player.isSecondaryUseActive()) {
            if (seat < 1) return InteractionResult.PASS;
            collapsedSeats.add(seat);
            for (Entity e : List.copyOf(getPassengers())) {
                if (!(e instanceof Player) && seatOf.getOrDefault(e.getUUID(), -1) == seat) e.stopRiding();
            }
            player.sendOverlayMessage(Component.translatable("message.interstellar.seat.collapsed"));
            return InteractionResult.SUCCESS;
        }
        if (seat >= 1 && collapsedSeats.remove(seat)) {
            player.sendOverlayMessage(Component.translatable("message.interstellar.seat.opened"));
            return InteractionResult.SUCCESS;
        }
        if (!canAddPassenger(player)) return InteractionResult.FAIL;
        player.startRiding(this);
        return InteractionResult.SUCCESS;
    }

    /** Seat index whose block is within 1.5 blocks of a clicked point (entity-relative), or -1. */
    private int seatNear(Vec3 location) {
        Vec3 local = toLocal(location);
        int best = -1;
        double bestD = 1.5 * 1.5;
        for (int i = 1; i < structure.seatCount(); i++) {
            BlockPos s = structure.seats.get(i);
            double d = local.distanceToSqr(s.getX(), s.getY() + 0.3, s.getZ());
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
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
        for (int i = 0; i < cannonCooldown.length; i++) if (cannonCooldown[i] > 0) cannonCooldown[i]--;
        if (structure.vehicle) {
            if (mode() != MODE_DRIVING) setMode(MODE_DRIVING);
            tickDriving(level, pilot);
            if (pendingPark && pilot == null) {
                pendingPark = false;
                park(level);
            }
        } else if (mode() == MODE_LANDING && !space) {
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
        if (structure.vehicle) return;
        if (!space && getY() > level.getMaxY() + InterstellarConfig.SERVER.spaceEntryHeight.get()) {
            SpaceTravel.enterSpace(this);
        } else if (space && (!alienCrewed || crashing || countPlayerPassengers() > 0)) {
            SpaceTravel.checkPlanetEntry(this);
        }
    }

    private void tickFlying(ServerLevel level, Player pilot, boolean space) {
        double min = InterstellarConfig.SERVER.shipMinSpeed.get(), max = InterstellarConfig.SERVER.shipMaxSpeed.get();
        float speed = entityData.get(SPEED);
        if (crashing && !space) {
            setMode(MODE_LANDING); // arrived on the planet it was falling towards
            return;
        }
        if (pilot == null && alienCrewed && alienPilot() != null) {
            tickAlienPilot(level, space);
            return;
        }
        if (pilot == null) {
            if (!space) {
                setMode(MODE_LANDING); // nobody at the controls: descend (or crash, see tickLanding)
                return;
            }
            // Adrift in space with nobody to take the controls: fall towards the nearest planet.
            crashing = true;
            if (crashTarget == null || --crashRetarget <= 0) {
                crashRetarget = 40;
                crashTarget = nearestPlanet(level);
            }
            if (crashTarget != null) {
                aim(crashTarget.subtract(position()));
                setYRot(Mth.approachDegrees(getYRot(), aiYaw, 3f));
                setXRot(Mth.approach(getXRot(), Mth.clamp(aiPitch, -80, 80), 3f));
                speed += (float) Mth.clamp(max - speed, -0.08, 0.02);
            } else {
                speed *= 0.9f;
            }
        } else {
            crashing = false;
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
            if (inputFire) fireWeapons(level, pilot);
        }
        entityData.set(SPEED, speed);
        Vec3 dir = Vec3.directionFromRotation(getXRot(), getYRot());
        Vec3 motion = dir.scale(speed * (space ? shipScale() * 4 : 1));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
    }

    /** The alien in the pilot's seat, if the crew still has one. */
    private Mob alienPilot() {
        for (Entity e : getPassengers()) if (e instanceof Mob m && m.isAlive() && seatOf.getOrDefault(m.getUUID(), -1) == 0) return m;
        for (Entity e : getPassengers()) {
            if (e instanceof Mob m && m.isAlive()) {
                seatOf.put(m.getUUID(), 0); // a crew member takes over the controls
                return m;
            }
        }
        return null;
    }

    /**
     * Alien ships wander through space, turn towards players nearby and fire when lined up, keep out of planet
     * atmospheres, and vanish once no player has been near for a minute (or the crew is gone).
     */
    private void tickAlienPilot(ServerLevel level, boolean space) {
        float speed = entityData.get(SPEED);
        Mob alien = alienPilot();
        Player target = level.getNearestPlayer(getX(), getY(), getZ(), 64 * Math.max(shipScale() * 10, 1), p -> p instanceof Player pl && !pl.isSpectator() && !pl.isCreative());
        if (alien == null || level.getNearestPlayer(this, 200) == null) idleTicks++;
        else idleTicks = 0;
        if (idleTicks > 1200 || !space) {
            for (Entity e : List.copyOf(getPassengers())) if (!(e instanceof Player)) e.discard();
            discard();
            return;
        }
        double min = InterstellarConfig.SERVER.shipMinSpeed.get(), max = InterstellarConfig.SERVER.shipMaxSpeed.get();
        if (alien == null) {
            speed *= 0.95f;
        } else {
            Vec3 forward = Vec3.directionFromRotation(getXRot(), getYRot());
            Vec3 avoid = planetAvoidance(level);
            boolean firing = false;
            if (avoid != null) {
                aim(avoid);
                speed += 0.04f;
            } else if (target != null) {
                Vec3 to = target.getEyePosition().subtract(position());
                aim(to);
                double cos = forward.dot(to.normalize());
                firing = cos > 0.97 && to.length() < 48 * Math.max(1, shipScale() * 10);
                speed += (float) Mth.clamp((to.length() > 20 ? max : min) - speed, -0.08, 0.05);
            } else {
                if (--aiTurnTimer <= 0) {
                    aiTurnTimer = 80 + random.nextInt(120);
                    aiYaw = getYRot() + (random.nextFloat() - 0.5f) * 140f;
                    aiPitch = (random.nextFloat() - 0.5f) * 40f;
                }
                speed += (float) Mth.clamp(min - speed, -0.08, 0.05);
            }
            setYRot(Mth.approachDegrees(getYRot(), aiYaw, 4f));
            setXRot(Mth.approach(getXRot(), Mth.clamp(aiPitch, -70, 70), 3f));
            if (firing) fireWeapons(level, alien);
        }
        entityData.set(SPEED, speed);
        Vec3 motion = Vec3.directionFromRotation(getXRot(), getYRot()).scale(speed * (space ? shipScale() * 4 : 1));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
    }

    private void aim(Vec3 dir) {
        if (dir.lengthSqr() < 1e-6) return;
        Vec3 d = dir.normalize();
        aiYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        aiPitch = (float) -(Math.asin(Mth.clamp(d.y, -1, 1)) * Mth.RAD_TO_DEG);
    }

    /** Direction away from the nearest planet atmosphere this ship is about to touch, or null. */
    private Vec3 planetAvoidance(ServerLevel level) {
        var planets = com.cstestcs.interstellar.space.PlanetManager.get(level.getServer());
        for (var p : planets.all()) {
            int[] a = p.atmosphere();
            AABB near = new AABB(a[0], a[1], a[2], a[3], a[4], a[5]).inflate(4);
            if (getBoundingBox().intersects(near)) return position().subtract(near.getCenter());
        }
        for (var g : com.cstestcs.interstellar.core.space.PlanetGenome.near(getX(), getZ(), 32)) {
            AABB b = getBoundingBox().inflate(4);
            if (g.touchesAtmosphere(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)) return position().subtract(g.centerX(), g.centerY(), g.centerZ());
        }
        return null;
    }

    /** Land vehicle: like riding a horse that cannot jump. */
    private void tickDriving(ServerLevel level, Player pilot) {
        float speed = entityData.get(SPEED);
        double max = InterstellarConfig.SERVER.vehicleMaxSpeed.get();
        if (pilot instanceof ServerPlayer driver) {
            net.minecraft.world.entity.player.Input in = driver.getLastClientInput();
            setYRot(Mth.approachDegrees(getYRot(), driver.getYRot(), 7f));
            double target = in.forward() ? max : in.backward() ? -max * 0.4 : 0;
            speed += (float) Mth.clamp(target - speed, -0.06, 0.03);
            if (inputFire) fireWeapons(level, pilot);
        } else {
            speed *= 0.7f;
            if (Math.abs(speed) < 0.005f) speed = 0;
        }
        setXRot(Mth.approach(getXRot(), 0, 4f));
        Vec3 dir = Vec3.directionFromRotation(0, getYRot());
        double vy = onGround() ? -0.08 : (getDeltaMovement().y - 0.08) * 0.98;
        Vec3 motion = new Vec3(dir.x * speed, vy, dir.z * speed);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (horizontalCollision) speed *= 0.5f;
        entityData.set(SPEED, speed);
    }

    /** Driver got out: become blocks if every wheel stands over a vehicle pad, otherwise stay a vehicle entity. */
    private void park(ServerLevel level) {
        Rotation rot = ShipStructure.rotationForQuarterTurns(quarterTurns());
        BlockPos origin = BlockPos.containing(getX(), Math.round(getY()), getZ());
        for (BlockPos o : new BlockPos[] { origin, origin.above(), origin.below() }) {
            if (wheelsOnPads(level, o, rot) && disassemble(level, false, o)) return;
        }
    }

    private boolean wheelsOnPads(ServerLevel level, BlockPos origin, Rotation rot) {
        if (structure.wheels.isEmpty()) return false;
        int bottom = Integer.MAX_VALUE;
        for (ShipStructure.Entry e : structure.entries) bottom = Math.min(bottom, e.pos().getY());
        for (BlockPos w : structure.wheels) {
            if (w.getY() != bottom) continue; // only the wheels the vehicle stands on
            BlockPos below = origin.offset(w.rotate(rot)).below();
            if (!level.getBlockState(below).is(ModBlocks.Tags.VEHICLE_PADS)) return false;
        }
        return true;
    }

    /** Centre of the nearest planet (of a real dimension or procedural), or null if none is in reach. */
    private @org.jspecify.annotations.Nullable Vec3 nearestPlanet(ServerLevel level) {
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (var p : com.cstestcs.interstellar.space.PlanetManager.get(level.getServer()).all()) {
            Vec3 c = new Vec3(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
            double d = c.distanceToSqr(position());
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        for (var g : com.cstestcs.interstellar.core.space.PlanetGenome.near(getX(), getZ(), 1200)) {
            Vec3 c = new Vec3(g.centerX(), g.centerY(), g.centerZ());
            double d = c.distanceToSqr(position());
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    /** Crashing ship hit the ground: an explosion where it struck, then whatever is left becomes blocks. */
    private void crash(ServerLevel level) {
        crashing = false;
        fallSpeed = 0;
        AABB box = getBoundingBox();
        float power = 3f + Math.min(4f, structure.entries.size() / 40f);
        level.explode(this, box.getCenter().x, box.minY, box.getCenter().z, power, Level.ExplosionInteraction.TNT);
        BlockPos origin = BlockPos.containing(getX(), Math.round(getY()), getZ());
        for (int up = 0; up <= 4; up++) {
            if (disassemble(level, false, origin.above(up))) return;
        }
        setMode(MODE_LANDING); // could not settle in the crater: keep descending normally
    }

    private void tickLanding(ServerLevel level) {
        if (crashing) {
            setXRot(Mth.approach(getXRot(), 25f, 2f));
            fallSpeed = Math.min(2.5, fallSpeed + 0.06);
            if (clearanceBelow(level, 1)) {
                crash(level);
                return;
            }
            Vec3 motion = new Vec3(0, -fallSpeed, 0);
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            if (verticalCollision) crash(level);
            return;
        }
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
        return disassemble(level, landing, BlockPos.containing(getX(), Math.round(getY()), getZ()));
    }

    private boolean disassemble(ServerLevel level, boolean landing, BlockPos origin) {
        int q = quarterTurns();
        Rotation rot = ShipStructure.rotationForQuarterTurns(q);
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
        for (int i = 0; i < cannonLoad.length; i++) writeLoad(i);
        structure.place(level, origin, rot, landing);
        // Folded seats stay folded.
        for (int seat : collapsedSeats) {
            if (seat < 1 || seat >= structure.seatCount()) continue;
            BlockPos p = origin.offset(structure.seats.get(seat).rotate(rot));
            BlockState st = level.getBlockState(p);
            if (st.hasProperty(PassengerSeatBlock.COLLAPSED)) level.setBlock(p, st.setValue(PassengerSeatBlock.COLLAPSED, true), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        // Put everyone back where they sat; seated mobs get seat entities so they keep sitting.
        for (Entity e : riders) {
            int seat = seats.getOrDefault(e.getUUID(), 0);
            BlockPos local = structure.seats.get(Math.min(seat, structure.seatCount() - 1)).rotate(rot);
            BlockPos at = origin.offset(local);
            if (seat > 0 && !(e instanceof Player) && !collapsedSeats.contains(seat)) {
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

    /** Every cannon whose cooldown is over fires whatever it is loaded with, along the way it points. */
    private void fireWeapons(ServerLevel level, LivingEntity shooter) {
        for (int i = 0; i < structure.weapons.size() && i < cannonLoad.length; i++) {
            if (cannonCooldown[i] > 0) continue;
            ShipStructure.Entry w = structure.weapons.get(i);
            BlockState st = w.state();
            Direction facing = st.hasProperty(CannonBlock.FACING) ? st.getValue(CannonBlock.FACING)
                    : st.hasProperty(ShipPartBlock.FACING) ? st.getValue(ShipPartBlock.FACING) : Direction.NORTH;
            Vec3 axis = Vec3.atLowerCornerOf(facing.getUnitVec3i());
            Vec3 local = new Vec3(w.pos().getX(), w.pos().getY() + 0.5, w.pos().getZ()).add(axis.scale(0.75));
            Vec3 muzzle = position().add(toWorld(local));
            Vec3 dir = toWorld(axis).normalize();
            ItemStack load = cannonLoad[i];
            if (ShipWeapons.fire(level, shooter, muzzle, dir, load, shipScale())) {
                cannonCooldown[i] = ShipWeapons.cooldown(load);
                if (!load.isEmpty() && ShipWeapons.consumesAmmo(load) && !inSpace()) { // ammunition is unlimited in space
                    load.shrink(1);
                    if (load.isEmpty()) cannonLoad[i] = ItemStack.EMPTY;
                    writeLoad(i);
                }
            }
        }
    }

    /** Mobs that touch a free, unfolded seat sit down in it. */
    private void pullMobsIntoSeats(ServerLevel level) {
        if (getPassengers().size() >= structure.seatCount()) return;
        for (int i = 1; i < structure.seatCount(); i++) {
            if (!freeSeat(i) || collapsedSeats.contains(i)) continue;
            Vec3 seatPos = position().add(toWorld(Vec3.atBottomCenterOf(structure.seats.get(i)).subtract(0.5, 0, 0.5)));
            AABB area = new AABB(seatPos, seatPos).inflate(0.9 * Math.max(shipScale(), 0.25));
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

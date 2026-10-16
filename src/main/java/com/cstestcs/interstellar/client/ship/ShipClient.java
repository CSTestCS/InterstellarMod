package com.cstestcs.interstellar.client.ship;

import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.network.ModPayloads;
import com.cstestcs.interstellar.ship.SpacecraftEntity;
import com.cstestcs.interstellar.space.SpaceTravel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import org.jspecify.annotations.Nullable;

/**
 * Pilot input (interact = boost, attack = fire, sneak = halt), automatic third person while aboard, a camera that
 * pulls back for large ships, and the "you will be removed from the spacecraft" confirmation.
 */
public final class ShipClient {
    private static boolean lastBoost, lastFire;
    private static @Nullable CameraType previousCamera;

    private ShipClient() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ShipClient::onMount);
        NeoForge.EVENT_BUS.addListener(ShipClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(ShipClient::onCameraDistance);
    }

    private static @Nullable SpacecraftEntity ship(@Nullable LocalPlayer player) {
        return player != null && player.getVehicle() instanceof SpacecraftEntity s ? s : null;
    }

    public static void tick(Minecraft mc) {
        SpacecraftEntity ship = ship(mc.player);
        // Automatic third person while aboard a ship in entity form.
        if (ship != null && InterstellarConfig.CLIENT.autoThirdPerson.get()) {
            if (previousCamera == null) {
                previousCamera = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        } else if (ship == null && previousCamera != null) {
            mc.options.setCameraType(previousCamera);
            previousCamera = null;
        }
        if (ship == null || ship.pilot() != mc.player) {
            lastBoost = lastFire = false;
            return;
        }
        boolean boost = mc.options.keyUse.isDown(), fire = mc.options.keyAttack.isDown();
        if (boost != lastBoost || fire != lastFire) {
            ClientPacketDistributor.sendToServer(new ModPayloads.ShipInput(boost, fire));
            lastBoost = boost;
            lastFire = fire;
        }
    }

    /** While piloting, interact and attack drive the ship instead of using items or hitting things. */
    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        SpacecraftEntity ship = ship(mc.player);
        if (ship != null && ship.pilot() == mc.player) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /**
     * Third person aboard a craft pulls back far enough to show all of it (outside space, where ships are full size):
     * the craft's bounding sphere must fit the view, measured from the rider's eye.
     */
    private static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Minecraft mc = Minecraft.getInstance();
        SpacecraftEntity ship = ship(mc.player);
        if (ship == null) return;
        net.minecraft.world.phys.AABB box = ship.getBoundingBox();
        if (SpaceTravel.isSpace(ship.level())) {
            event.setDistance((float) Math.max(event.getDistance(), box.getSize() * 1.2 + 2));
            return;
        }
        double radius = Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize()) / 2;
        double offCentre = mc.player.getEyePosition().distanceTo(box.getCenter());
        double halfFov = Math.toRadians(mc.options.fov().get() / 2.0);
        double fit = radius / Math.tan(halfFov) + offCentre + 1;
        event.setDistance((float) Math.max(event.getDistance(), fit));
    }

    /**
     * Riders lean with the craft: their model is tilted about their feet by the ship's pitch, around the ship's own
     * yaw axis (the same rotation the ship's blocks get).
     */
    public static void tiltRider(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?, ?> event) {
        ShipTilt tilt = event.getRenderState().getRenderData(ShipTilt.KEY);
        if (tilt == null || Math.abs(tilt.pitch()) < 0.01f) return;
        var pose = event.getPoseStack();
        pose.rotateDegrees(com.mojang.math.Axis.YP, -tilt.yaw());
        pose.rotateDegrees(com.mojang.math.Axis.XP, tilt.pitch());
        pose.rotateDegrees(com.mojang.math.Axis.YP, tilt.yaw());
    }

    /** Yaw and pitch of the craft an entity rides, attached to its render state. */
    public record ShipTilt(float yaw, float pitch) {
        public static final net.minecraft.util.context.ContextKey<ShipTilt> KEY = new net.minecraft.util.context.ContextKey<>(
                com.cstestcs.interstellar.Interstellar.id("ship_tilt"));
    }

    /** Called from the render state modifier for every living entity. */
    public static void extract(net.minecraft.world.entity.LivingEntity entity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        if (!(entity.getVehicle() instanceof SpacecraftEntity ship)) {
            state.setRenderData(ShipTilt.KEY, null);
            return;
        }
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        state.setRenderData(ShipTilt.KEY, new ShipTilt(net.minecraft.util.Mth.rotLerp(pt, ship.yRotO, ship.getYRot()),
                net.minecraft.util.Mth.lerp(pt, ship.xRotO, ship.getXRot())));
        if (state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState hs && ship.isSeated(entity)) hs.isPassenger = true;
    }

    /** Client side of the dismount guard: never in space, pilots halt instead, everyone else must confirm. */
    private static void onMount(EntityMountEvent event) {
        if (!event.getLevel().isClientSide() || !event.isDismounting()) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntityMounting() != mc.player || !(event.getEntityBeingMounted() instanceof SpacecraftEntity ship)) return;
        if (ship.isVehicle()) return; // land vehicles: get out whenever you like
        // Only guard the player's own sneak; dismounts ordered by the server (landing, halting) always go through.
        if (!mc.options.keyShift.isDown()) return;
        if (ship.isRemoved() || !mc.player.isAlive() || DismountConfirmScreen.confirmed) {
            DismountConfirmScreen.confirmed = false;
            return;
        }
        event.setCanceled(true);
        if (SpaceTravel.isSpace(ship.level())) {
            mc.player.sendOverlayMessage(Component.translatable("message.interstellar.space.no_dismount"));
        } else if (ship.pilot() != mc.player && !(mc.gui.screen() instanceof DismountConfirmScreen)) {
            mc.gui.setScreen(new DismountConfirmScreen());
        }
    }
}

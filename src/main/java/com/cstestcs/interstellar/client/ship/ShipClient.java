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

    private static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        SpacecraftEntity ship = ship(Minecraft.getInstance().player);
        if (ship == null) return;
        double size = ship.getBoundingBox().getSize();
        event.setDistance((float) Math.max(event.getDistance(), size * 1.2 + 2));
    }

    /** Client side of the dismount guard: never in space, pilots halt instead, everyone else must confirm. */
    private static void onMount(EntityMountEvent event) {
        if (!event.getLevel().isClientSide() || !event.isDismounting()) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntityMounting() != mc.player || !(event.getEntityBeingMounted() instanceof SpacecraftEntity ship)) return;
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

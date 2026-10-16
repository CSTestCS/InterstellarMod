package com.cstestcs.interstellar.space;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.core.space.PlanetLayout;
import com.cstestcs.interstellar.ship.SpacecraftEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Moves spacecraft (with everyone aboard) between dimensions and space. */
public final class SpaceTravel {
    public static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION, Interstellar.id("space"));

    /** True while a ship and its passengers are being moved, so mount/dismount guards let them through. */
    private static boolean transferring;

    private SpaceTravel() {}

    public static boolean isSpace(Level level) {
        return level.dimension().equals(SPACE);
    }

    public static boolean isTransferring() {
        return transferring;
    }

    /** Climbed high enough: appear in space next to the planet of the dimension we came from. */
    public static void enterSpace(SpacecraftEntity ship) {
        MinecraftServer server = ship.level().getServer();
        if (server == null) return;
        ServerLevel space = server.getLevel(SPACE);
        if (space == null) return;
        PlanetManager planets = PlanetManager.get(server);
        planets.ensurePlanets(server);
        PlanetLayout.Planet home = planets.planetFor(ship.level().dimension());
        if (home == null) {
            // Leaving a procedural planet: come out above its cube.
            home = com.cstestcs.interstellar.core.space.PlanetGenome.parse(ship.level().dimension().identifier().toString())
                    .map(com.cstestcs.interstellar.core.space.PlanetGenome::asPlanet).orElse(null);
        }
        Vec3 target;
        if (home != null) {
            int[] a = home.atmosphere();
            target = new Vec3(home.x() + 0.5, a[4] + 3, home.z() + 0.5);
        } else {
            target = new Vec3(0.5, PlanetLayout.PLANE_Y + 40, 0.5);
        }
        announce(transfer(ship, space, target, ship.getYRot()), Component.translatable("message.interstellar.space.entered"));
    }

    /** In space: touching a planet's atmosphere lands you in that dimension, high above the world at X=0 Z=0. */
    public static void checkPlanetEntry(SpacecraftEntity ship) {
        MinecraftServer server = ship.level().getServer();
        if (server == null) return;
        PlanetManager planets = PlanetManager.get(server);
        AABB box = ship.getBoundingBox();
        for (PlanetLayout.Planet p : planets.all()) {
            int[] a = p.atmosphere();
            if (box.intersects(a[0], a[1], a[2], a[3], a[4], a[5])) {
                ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(p.dimension())));
                if (target == null) continue;
                double y = target.getMaxY() + InterstellarConfig.SERVER.planetArrivalHeight.get();
                announce(transfer(ship, target, new Vec3(0.5, y, 0.5), ship.getYRot()),
                        Component.translatable("message.interstellar.space.arrived", p.dimension()));
                return;
            }
        }
        // Procedural planets: their dimension is created the first time a ship arrives.
        for (com.cstestcs.interstellar.core.space.PlanetGenome g : com.cstestcs.interstellar.core.space.PlanetGenome.near(ship.getX(), ship.getZ(),
                box.getXsize() + 32)) {
            if (!g.touchesAtmosphere(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) continue;
            if (!ARRIVING.add(ship.getUUID())) return; // already on its way
            for (Entity e : ship.getPassengers()) {
                if (e instanceof Player pl) pl.sendOverlayMessage(Component.translatable("message.interstellar.space.approaching", g.name()));
            }
            com.cstestcs.interstellar.space.planet.PlanetDimensions.request(server, g, target -> {
                ARRIVING.remove(ship.getUUID());
                if (ship.isRemoved() || !isSpace(ship.level())) return;
                double y = target.getMaxY() + InterstellarConfig.SERVER.planetArrivalHeight.get();
                announce(transfer(ship, target, new Vec3(0.5, y, 0.5), ship.getYRot()),
                        Component.translatable("message.interstellar.space.arrived", g.name()));
            });
            return;
        }
    }

    private static final java.util.Set<UUID> ARRIVING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Teleports a ship and all its passengers, keeping their seats. Returns the ship in the new level. */
    public static SpacecraftEntity transfer(SpacecraftEntity ship, ServerLevel target, Vec3 pos, float yaw) {
        List<Entity> riders = new ArrayList<>(ship.getPassengers());
        Map<UUID, Integer> seats = new HashMap<>(ship.seatMap());
        transferring = true;
        try {
            ship.ejectPassengers();
            Entity moved = ship.teleport(new TeleportTransition(target, pos, Vec3.ZERO, yaw, 0f, TeleportTransition.DO_NOTHING));
            if (!(moved instanceof SpacecraftEntity newShip)) return ship;
            for (Entity rider : riders) {
                Entity r = rider.teleport(new TeleportTransition(target, pos, Vec3.ZERO, rider.getYRot(), rider.getXRot(), TeleportTransition.DO_NOTHING));
                if (r == null) continue;
                r.startRiding(newShip, true, true);
                Integer seat = seats.get(rider.getUUID());
                if (seat != null) newShip.restoreSeat(r, seat);
            }
            return newShip;
        } finally {
            transferring = false;
        }
    }

    private static void announce(SpacecraftEntity ship, Component msg) {
        for (Entity e : ship.getPassengers()) if (e instanceof Player p) p.sendOverlayMessage(msg);
    }
}

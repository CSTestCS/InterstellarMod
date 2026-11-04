package com.cstestcs.interstellar.space;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.core.space.PlanetLayout;
import com.cstestcs.interstellar.network.ModPayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3fc;

/** Sends players in space the planet list with each planet's sky colour, for the glowing atmosphere. */
@EventBusSubscriber(modid = Interstellar.MODID)
public final class PlanetSync {
    private PlanetSync() {}

    /** Each world has its own galaxy: the planets are derived from the world seed. */
    @SubscribeEvent
    static void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        com.cstestcs.interstellar.core.space.PlanetGenome.setGalaxyKey(galaxyKey(event.getServer()));
    }

    /**
     * The world's galaxy key, remembered in the world folder. New worlds derive it from their seed; worlds whose space
     * was already generated with the old shared galaxy keep it, so existing planets are not cut in half.
     */
    static long galaxyKey(MinecraftServer server) {
        java.nio.file.Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        java.nio.file.Path file = root.resolve("interstellar_galaxy.txt");
        try {
            if (java.nio.file.Files.exists(file)) return Long.parseLong(java.nio.file.Files.readString(file).strip());
        } catch (Exception e) {
            Interstellar.LOGGER.warn("Unreadable {}: {}", file, e.toString());
        }
        boolean legacy = false;
        java.nio.file.Path region = root.resolve("dimensions").resolve(Interstellar.MODID).resolve("space").resolve("region");
        try (var files = java.nio.file.Files.list(region)) {
            legacy = files.findAny().isPresent();
        } catch (Exception ignored) {
            // no space generated yet
        }
        long key = legacy ? com.cstestcs.interstellar.core.space.PlanetGenome.legacyKey()
                : com.cstestcs.interstellar.core.space.PlanetGenome.keyForSeed(server.overworld().getSeed());
        try {
            java.nio.file.Files.writeString(file, Long.toString(key));
        } catch (Exception e) {
            Interstellar.LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
        return key;
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            PacketDistributor.sendToPlayer(p, new ModPayloads.Galaxy(com.cstestcs.interstellar.core.space.PlanetGenome.galaxyKey()));
            send(p);
        }
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) send(p);
    }

    public static void send(ServerPlayer player) {
        if (!SpaceTravel.isSpace(player.level())) return;
        MinecraftServer server = player.level().getServer();
        List<ModPayloads.PlanetInfo> list = new ArrayList<>();
        for (PlanetLayout.Planet p : PlanetManager.get(server).all()) {
            list.add(new ModPayloads.PlanetInfo(p.dimension(), p.size(), p.x(), p.y(), p.z(), p.moon(), skyColor(server, p.dimension())));
        }
        PacketDistributor.sendToPlayer(player, new ModPayloads.Planets(list));
    }

    /** The dimension's daytime sky colour (fog colour for skyless dimensions like the nether, purple for the end). */
    static int skyColor(MinecraftServer server, String dimension) {
        ForeignWorlds.World fw = ForeignWorlds.byDimension(dimension);
        if (fw != null) return fw.skyColor();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)));
        if (level == null) return 0x78A7FF;
        int sky = rgb(level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_COLOR));
        if (luminance(sky) > 0.08) return sky;
        int fog = rgb(level.environmentAttributes().getDimensionValue(EnvironmentAttributes.FOG_COLOR));
        if (luminance(fog) > 0.12) return brighten(fog);
        return 0xA070D0;
    }

    private static int rgb(Vector3fc c) {
        return (int) (Math.min(1, c.x()) * 255) << 16 | (int) (Math.min(1, c.y()) * 255) << 8 | (int) (Math.min(1, c.z()) * 255);
    }

    private static double luminance(int c) {
        return (0.3 * ((c >> 16) & 255) + 0.59 * ((c >> 8) & 255) + 0.11 * (c & 255)) / 255.0;
    }

    private static int brighten(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int m = Math.max(1, Math.max(r, Math.max(g, b)));
        double k = 220.0 / m;
        return (int) Math.min(255, r * k) << 16 | (int) Math.min(255, g * k) << 8 | (int) Math.min(255, b * k);
    }
}

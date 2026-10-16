package com.cstestcs.interstellar.space.planet;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.space.PlanetGenome;
import com.cstestcs.interstellar.space.SpaceTravel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development check, only active when the environment variable {@code INTERSTELLAR_PLANET_SELFTEST} is set: creates
 * planets of every terrain family, generates terrain around their spawn, builds planets in space and logs what came
 * out, then stops the server.
 */
@EventBusSubscriber(modid = Interstellar.MODID)
public final class PlanetSelfTest {
    private static final boolean ON = System.getenv("INTERSTELLAR_PLANET_SELFTEST") != null;
    private static int ticks = -1;
    private static PlanetGenome spacePlanet;

    private PlanetSelfTest() {}

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        if (!ON) return;
        MinecraftServer server = event.getServer();
        EnumSet<PlanetGenome.Terrain> seen = EnumSet.noneOf(PlanetGenome.Terrain.class);
        List<PlanetGenome> picks = new ArrayList<>();
        for (int cx = 5; cx < 400 && seen.size() < PlanetGenome.Terrain.values().length; cx++) {
            var g = PlanetGenome.at(cx, 3);
            if (g.isPresent() && seen.add(g.get().terrain())) picks.add(g.get());
        }
        for (PlanetGenome g : picks) {
            long t0 = System.nanoTime();
            ServerLevel level = PlanetDimensions.getOrCreate(server, g);
            if (level == null) {
                Interstellar.LOGGER.error("SELFTEST planet {} could not be created", g);
                continue;
            }
            Map<String, Integer> blocks = new TreeMap<>();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    ChunkAccess c = level.getChunk(x, z);
                    for (int bx = 0; bx < 16; bx += 3) for (int bz = 0; bz < 16; bz += 3) {
                        for (int y = level.getMinY(); y < level.getMaxY(); y += 2) {
                            String id = BuiltInRegistries.BLOCK.getKey(c.getBlockState(new BlockPos(c.getPos().getMinBlockX() + bx, y, c.getPos().getMinBlockZ() + bz)).getBlock()).toString();
                            if (id.endsWith("_ore") || id.contains("ore")) blocks.merge(id, 1, Integer::sum);
                        }
                    }
                }
            }
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0);
            String top = BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(0, y - 1, 0)).getBlock()).toString();
            var gen = (PlanetChunkGenerator) level.getChunkSource().getGenerator();
            List<String> biomes = gen.getBiomeSource().possibleBiomes().stream().map(h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse("?")).toList();
            var monsters = gen.getMobsAt(level, level.structureManager(), MobCategory.MONSTER, new BlockPos(0, y, 0)).unwrap().stream()
                    .map(w -> EntityName.of(w.value().type()) + "x" + w.weight()).toList();
            var creatures = gen.getMobsAt(level, level.structureManager(), MobCategory.CREATURE, new BlockPos(0, y, 0)).unwrap().stream()
                    .map(w -> EntityName.of(w.value().type())).toList();
            Interstellar.LOGGER.info("SELFTEST planet {} terrain={} default={} fluid={} sea={} type={} biomes={} surfaceY={} top={} ores={} monsters={} creatures={} ms={}",
                    g.dimensionId(), g.terrain(), gen.generatorSettings().value().defaultBlock(), gen.generatorSettings().value().defaultFluid(),
                    gen.getSeaLevel(), level.dimensionTypeRegistration().unwrapKey().map(k -> k.identifier().toString()).orElse("?"), biomes, y, top, blocks,
                    monsters, creatures, (System.nanoTime() - t0) / 1_000_000);
        }
        // A planet in space: load its chunk and let the next ticks build it.
        ServerLevel space = server.getLevel(SpaceTravel.SPACE);
        spacePlanet = PlanetGenome.near(3000, 3000, 400).get(0);
        if (space != null) space.getChunk(spacePlanet.x() >> 4, spacePlanet.z() >> 4);
        Interstellar.LOGGER.info("SELFTEST known planets now {}", PlanetDimensions.Known.get(server).paths());
        ticks = 0;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (!ON || ticks < 0) return;
        if (++ticks == 60) {
            ServerLevel space = event.getServer().getLevel(SpaceTravel.SPACE);
            if (space != null) {
                var center = new BlockPos(spacePlanet.x(), spacePlanet.y(), spacePlanet.z());
                Interstellar.LOGGER.info("SELFTEST space planet {} center block {} (expected {})", spacePlanet,
                        BuiltInRegistries.BLOCK.getKey(space.getBlockState(center).getBlock()),
                        PlanetPools.defaultBlock(spacePlanet, space.registryAccess()));
            }
            Interstellar.LOGGER.info("SELFTEST done");
            event.getServer().halt(false);
        }
    }

    private static final class EntityName {
        static String of(net.minecraft.world.entity.EntityType<?> t) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
        }
    }

    @SuppressWarnings("unused")
    private static String biomeName(net.minecraft.core.Holder<Biome> h) {
        return h.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
    }
}

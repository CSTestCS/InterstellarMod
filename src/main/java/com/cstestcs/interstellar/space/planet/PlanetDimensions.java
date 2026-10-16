package com.cstestcs.interstellar.space.planet;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.space.PlanetGenome;
import com.cstestcs.interstellar.core.space.PlanetTypes;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Creates planet dimensions while the server runs, as 20w14∞ did: {@code getLevel} there was a
 * {@code computeIfAbsent} that built a {@code DerivedServerLevel} (sharing the overworld's level data) from the
 * dimension's number the first time anything asked for it. Here the number is the planet's cell in space, the level
 * is built from its {@link PlanetGenome}, and it is added to the server's level map between ticks.
 * <p>
 * Created planets are remembered so they are loaded again when the server restarts (players and things left on them
 * come back); their chunks are saved in the world like any other dimension.
 */
@EventBusSubscriber(modid = Interstellar.MODID)
public final class PlanetDimensions {
    private record Request(PlanetGenome genome, Consumer<ServerLevel> then) {}

    private static final Deque<Request> QUEUE = new ArrayDeque<>();

    private PlanetDimensions() {}

    public static ResourceKey<Level> key(PlanetGenome g) {
        return ResourceKey.create(Registries.DIMENSION, Interstellar.id(g.dimensionPath()));
    }

    /** The planet's level if it exists already. */
    public static @Nullable ServerLevel get(MinecraftServer server, PlanetGenome g) {
        return server.getLevel(key(g));
    }

    /**
     * Calls {@code then} with the planet's level, creating it first if needed. Levels cannot be added while the
     * server iterates them, so creation (and the callback) waits for the end of the current tick.
     */
    public static void request(MinecraftServer server, PlanetGenome g, Consumer<ServerLevel> then) {
        ServerLevel existing = get(server, g);
        if (existing != null) {
            then.accept(existing);
            return;
        }
        synchronized (QUEUE) {
            QUEUE.add(new Request(g, then));
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        List<Request> todo;
        synchronized (QUEUE) {
            if (QUEUE.isEmpty()) return;
            todo = new ArrayList<>(QUEUE);
            QUEUE.clear();
        }
        for (Request r : todo) {
            ServerLevel level = getOrCreate(server, r.genome());
            if (level != null) r.then().accept(level);
        }
    }

    /** Recreates every planet created before, so whatever was left there loads with it. */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        synchronized (QUEUE) {
            QUEUE.clear();
        }
        for (String path : Known.get(server).paths()) {
            PlanetGenome.parse(path).ifPresent(g -> getOrCreate(server, g));
        }
    }

    /** Only call between ticks (or before the first one). */
    static @Nullable ServerLevel getOrCreate(MinecraftServer server, PlanetGenome g) {
        ResourceKey<Level> key = key(g);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) return existing;
        try {
            LevelStem stem = stem(server.registryAccess(), g, server.getWorldGenSettings().options().seed());
            ServerLevel overworld = server.overworld();
            DerivedLevelData data = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
            ServerLevel level = new ServerLevel(server, server.executor, server.storageSource, data, key, stem, false,
                    net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(server.getWorldGenSettings().options().seed()), ImmutableList.of(), false);
            level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
            server.levels.put(key, level);
            server.getPlayerList().addWorldborderListener(level);
            NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
            Known.get(server).add(g.dimensionPath());
            Interstellar.LOGGER.info("Created planet {} as {} ({} terrain)", g.name(), key.identifier(), g.terrain());
            return level;
        } catch (RuntimeException e) {
            Interstellar.LOGGER.error("Could not create planet {} ({})", g.name(), key.identifier(), e);
            return null;
        }
    }

    /** The planet's dimension: a dimension type from the pool and a noise generator built from the genome. */
    static LevelStem stem(RegistryAccess registries, PlanetGenome g, long worldSeed) {
        Holder<DimensionType> type = registries.lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(PlanetTypes.get(g.dimensionType()).id())));
        NoiseGeneratorSettings preset = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(ResourceKey.create(Registries.NOISE_SETTINGS, Identifier.withDefaultNamespace(g.terrain().preset))).value();
        NoiseGeneratorSettings settings = new NoiseGeneratorSettings(preset.noiseSettings(), PlanetPools.defaultBlock(g, registries),
                PlanetPools.defaultFluid(g, registries), preset.noiseRouter(), preset.materialRule(), preset.spawnTarget(), g.seaLevel(), false,
                preset.aquifers(), preset.useLegacyRandomSource(), preset.debugFunctions());
        Holder<NoiseGeneratorSettings> settingsHolder = Holder.direct(settings);
        List<Holder<Biome>> pool = PlanetPools.get(registries).biomes();
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> points = new ArrayList<>();
        for (PlanetGenome.BiomePick pick : g.biomes()) {
            if (pool.isEmpty()) break;
            Holder<Biome> biome = PlanetGenome.pick(pool, pick.roll());
            for (PlanetGenome.Point p : pick.climate()) {
                points.add(Pair.of(Climate.parameters(p.temperature(), p.humidity(), p.continentalness(), p.erosion(), p.depth(), p.weirdness(),
                        p.offset()), biome));
            }
        }
        // Biomes from different worlds and mods may order their features in ways that cannot be combined; drop
        // biomes from the end of the list until the set is consistent (the snapshot never had to).
        while (true) {
            PlanetChunkGenerator generator = new PlanetChunkGenerator(g.dimensionPath(), biomeSource(points, registries), settingsHolder);
            try {
                generator.validate();
                long seed = worldSeed ^ g.seed();
                return new LevelStem(type, generator, OptionalLong.of(seed));
            } catch (RuntimeException e) {
                if (distinctBiomes(points) <= 1) throw e;
                Holder<Biome> last = points.get(points.size() - 1).getSecond();
                points.removeIf(pt -> pt.getSecond().equals(last));
            }
        }
    }

    private static int distinctBiomes(List<Pair<Climate.ParameterPoint, Holder<Biome>>> points) {
        Set<Holder<Biome>> set = new LinkedHashSet<>();
        for (var p : points) set.add(p.getSecond());
        return set.size();
    }

    private static BiomeSource biomeSource(List<Pair<Climate.ParameterPoint, Holder<Biome>>> points, HolderLookup.Provider registries) {
        if (points.isEmpty()) {
            return new FixedBiomeSource(registries.lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS));
        }
        if (distinctBiomes(points) == 1) return new FixedBiomeSource(points.get(0).getSecond());
        return MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(List.copyOf(points)));
    }

    /** Planets created so far (by dimension path), kept with the world. */
    public static final class Known extends SavedData {
        private static final Codec<Known> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("planets").forGetter(k -> new ArrayList<>(k.paths))
        ).apply(i, Known::new));
        public static final SavedDataType<Known> TYPE = new SavedDataType<>(Interstellar.id("planet_dimensions"), level -> new Known(List.of()),
                level -> CODEC);

        private final Set<String> paths = new LinkedHashSet<>();

        private Known(List<String> list) {
            paths.addAll(list);
        }

        public static Known get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        }

        public List<String> paths() {
            return List.copyOf(paths);
        }

        void add(String path) {
            if (paths.add(path)) setDirty();
        }
    }
}

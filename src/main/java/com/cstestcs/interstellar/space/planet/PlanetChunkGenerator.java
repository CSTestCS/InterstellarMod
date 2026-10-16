package com.cstestcs.interstellar.space.planet;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.core.space.PlanetGenome;
import com.cstestcs.interstellar.registry.ModBlocks;
import com.cstestcs.interstellar.registry.ModEntities;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * Terrain of a procedural planet: a noise generator built from the planet's genome (template, default block, fluid,
 * sea level, random biomes), plus what Interstellar adds on top of the snapshot's recipe: veins of the settling
 * species' alien resource and of random vanilla or modded ores set in the planet's own stone, random decorations
 * from {@code #interstellar:planet_features}, and natural spawns of aliens and random creatures from every mod.
 */
public class PlanetChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("planet").forGetter(g -> g.planet),
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings)
    ).apply(i, PlanetChunkGenerator::new));

    public static final TagKey<Feature> PLANET_FEATURES = TagKey.create(Registries.FEATURE, Interstellar.id("planet_features"));

    private final String planet;
    private final @Nullable PlanetGenome genome;
    /** Built on first use from the live registries. */
    private volatile @Nullable Extras extras;

    private record Vein(Feature feature, int count, int minY, int maxY) {}

    private record Extras(List<Vein> veins, List<Holder<Feature>> decorations, Map<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>> spawns) {}

    public PlanetChunkGenerator(String planet, BiomeSource biomeSource, net.minecraft.core.Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.planet = planet;
        this.genome = PlanetGenome.parse(planet).orElse(null);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public String planet() {
        return planet;
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        if (genome == null) return;
        Extras ex = extras(level.registryAccess());
        ChunkPos pos = chunk.getPos();
        RandomSource random = RandomSource.create(level.getSeed() ^ genome.seed() ^ (pos.x() * 341873128712L + pos.z() * 132897987541L));
        int x0 = pos.getMinBlockX(), z0 = pos.getMinBlockZ();
        for (Vein v : ex.veins()) {
            for (int i = 0; i < v.count(); i++) {
                int y = v.minY() + random.nextInt(Math.max(1, v.maxY() - v.minY()));
                place(level, v.feature(), random, new BlockPos(x0 + random.nextInt(16), y, z0 + random.nextInt(16)));
            }
        }
        for (Holder<Feature> deco : ex.decorations()) {
            if (random.nextInt(3) != 0) continue;
            int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            if (y <= level.getMinY() + 1) continue;
            place(level, deco.value(), random, new BlockPos(x, y, z));
        }
    }

    private void place(WorldGenLevel level, Feature feature, RandomSource random, BlockPos pos) {
        try {
            feature.place(level, this, random, pos);
        } catch (RuntimeException e) {
            // A decoration that does not fit this planet (for example one expecting particular blocks) is skipped.
        }
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Level level, StructureManager structureManager, MobCategory category, BlockPos pos) {
        WeightedList<MobSpawnSettings.SpawnerData> base = super.getMobsAt(level, structureManager, category, pos);
        if (genome == null) return base;
        List<Weighted<MobSpawnSettings.SpawnerData>> extra = extras(level.registryAccess()).spawns().get(category);
        if (extra == null || extra.isEmpty()) return base;
        List<Weighted<MobSpawnSettings.SpawnerData>> all = new ArrayList<>(base.unwrap());
        all.addAll(extra);
        return WeightedList.of(all);
    }

    private Extras extras(HolderLookup.Provider registries) {
        Extras e = extras;
        if (e == null) {
            e = buildExtras(registries);
            extras = e;
        }
        return e;
    }

    private Extras buildExtras(HolderLookup.Provider registries) {
        PlanetGenome g = genome;
        PlanetPools.Pools pools = PlanetPools.get(registries);
        BlockState stone = generatorSettings().value().defaultBlock();
        int minY = getMinY(), maxY = getMinY() + getGenDepth();
        List<Vein> veins = new ArrayList<>();
        // The settling species' resource, sometimes another species' too.
        ForeignWorlds.World species = ForeignWorlds.all().get(g.species());
        Block alienOre = ModBlocks.ALIEN_ORES.get(ForeignWorlds.resource(species.theme())).get();
        addVein(veins, registries, alienOre, stone, 8, 9, minY, maxY);
        List<String> others = new ArrayList<>(ModBlocks.ALIEN_ORES.keySet());
        for (int i = 0; i < g.extraAlienOres(); i++) {
            Block other = ModBlocks.ALIEN_ORES.get(PlanetGenome.pick(others, g.alienOreRoll() + i * 7)).get();
            addVein(veins, registries, other, stone, 5, 3, minY, maxY);
        }
        // Metals and every other ore known to the game, vanilla or modded.
        for (int roll : g.oreRolls()) {
            Block ore = PlanetGenome.pick(pools.ores(), roll);
            addVein(veins, registries, ore, stone, 4 + Math.floorMod(roll >> 4, 6), 2 + Math.floorMod(roll >> 8, 8), minY, maxY);
        }
        List<Holder<Feature>> decorations = new ArrayList<>();
        registries.lookup(Registries.FEATURE).flatMap(l -> l.get(PLANET_FEATURES)).ifPresent(set -> {
            List<Holder<Feature>> list = set.stream().sorted(java.util.Comparator.comparing(h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse(""))).toList();
            if (!list.isEmpty()) for (int roll : g.featureRolls()) decorations.add(PlanetGenome.pick(list, roll));
        });
        Map<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>> spawns = new EnumMap<>(MobCategory.class);
        List<Weighted<MobSpawnSettings.SpawnerData>> monsters = new ArrayList<>();
        monsters.add(new Weighted<>(new MobSpawnSettings.SpawnerData(ModEntities.ALIEN.get(), UniformInt.of(1, 4)), g.alienWeight()));
        for (int roll : g.monsterRolls()) {
            if (pools.monsters().isEmpty()) break;
            monsters.add(new Weighted<>(new MobSpawnSettings.SpawnerData(PlanetGenome.pick(pools.monsters(), roll), UniformInt.of(1, 3)), 20));
        }
        spawns.put(MobCategory.MONSTER, monsters);
        List<Weighted<MobSpawnSettings.SpawnerData>> creatures = new ArrayList<>();
        for (int roll : g.creatureRolls()) {
            if (pools.creatures().isEmpty()) break;
            EntityType<?> type = PlanetGenome.pick(pools.creatures(), roll);
            creatures.add(new Weighted<>(new MobSpawnSettings.SpawnerData(type, UniformInt.of(2, 4)), 10));
        }
        spawns.put(MobCategory.CREATURE, creatures);
        return new Extras(List.copyOf(veins), List.copyOf(decorations), spawns);
    }

    /** An ore vein feature, built through the data format so it works with any block. */
    private static void addVein(List<Vein> out, HolderLookup.Provider registries, Block ore, BlockState stone, int size, int count, int minY, int maxY) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:ore");
        json.addProperty("size", Math.max(2, Math.min(32, size)));
        json.addProperty("discard_chance_on_air_exposure", 0.0);
        JsonArray targets = new JsonArray();
        targets.add(target(ore, "minecraft:block_match", "block", BuiltInRegistries.BLOCK.getKey(stone.getBlock()).toString()));
        targets.add(target(ore, "minecraft:tag_match", "tag", "minecraft:stone_ore_replaceables"));
        json.add("targets", targets);
        Feature.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), json).result()
                .ifPresent(f -> out.add(new Vein(f, count, minY, maxY)));
    }

    private static JsonObject target(Block ore, String predicate, String key, String value) {
        JsonObject t = new JsonObject();
        t.addProperty("state", BuiltInRegistries.BLOCK.getKey(ore).toString());
        JsonObject test = new JsonObject();
        test.addProperty("predicate_type", predicate);
        test.addProperty(key, value);
        t.add("target", test);
        return t;
    }
}

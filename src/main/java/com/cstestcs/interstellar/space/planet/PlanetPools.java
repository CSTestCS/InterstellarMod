package com.cstestcs.interstellar.space.planet;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.space.PlanetGenome;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The registry entries planets are made from, like the snapshot's lists of every usable block and biome. Built from
 * the live registries, so every mod's blocks, biomes, ores and creatures take part; packs can steer it with tags:
 * <ul>
 *     <li>{@code #interstellar:planet_excluded_blocks}, {@code #interstellar:planet_excluded_biomes},
 *     {@code #interstellar:planet_excluded_entities}: never used;</li>
 *     <li>{@code #c:ores}, {@code #minecraft:ores} and {@code #interstellar:planet_ores}: ores planets may contain;</li>
 *     <li>{@code #interstellar:planet_features} (worldgen features): decorations planets may scatter.</li>
 * </ul>
 * Entries are sorted by id so the server and every client with the same mods agree on each pick.
 */
public final class PlanetPools {
    public static final TagKey<Block> EXCLUDED_BLOCKS = TagKey.create(Registries.BLOCK, Interstellar.id("planet_excluded_blocks"));
    public static final TagKey<Block> PLANET_ORES = TagKey.create(Registries.BLOCK, Interstellar.id("planet_ores"));
    public static final TagKey<Block> C_ORES = TagKey.create(Registries.BLOCK, Identifier.parse("c:ores"));
    public static final TagKey<Block> MC_ORES = TagKey.create(Registries.BLOCK, Identifier.parse("minecraft:ores"));
    public static final TagKey<Biome> EXCLUDED_BIOMES = TagKey.create(Registries.BIOME, Interstellar.id("planet_excluded_biomes"));
    public static final TagKey<EntityType<?>> EXCLUDED_ENTITIES = TagKey.create(Registries.ENTITY_TYPE, Interstellar.id("planet_excluded_entities"));

    public record Pools(List<Block> blocks, List<Holder<Biome>> biomes, List<Block> ores, List<EntityType<?>> creatures, List<EntityType<?>> monsters) {}

    private static WeakReference<Object> cachedFor = new WeakReference<>(null);
    private static @Nullable Pools cached;

    private PlanetPools() {}

    /** Pools for a registry access (cached until the registries change). */
    public static synchronized Pools get(HolderLookup.Provider registries) {
        if (cached != null && cachedFor.get() == registries) return cached;
        cached = build(registries);
        cachedFor = new WeakReference<>(registries);
        return cached;
    }

    private static Pools build(HolderLookup.Provider registries) {
        List<Block> blocks = new ArrayList<>();
        Set<Block> ores = new LinkedHashSet<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            BlockState s = b.defaultBlockState();
            if (s.is(C_ORES) || s.is(MC_ORES) || s.is(PLANET_ORES)) ores.add(b);
            if (usableAsDefaultBlock(s)) blocks.add(b);
        }
        blocks.sort(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()));
        List<Block> oreList = new ArrayList<>(ores);
        oreList.removeIf(b -> b.defaultBlockState().is(EXCLUDED_BLOCKS));
        oreList.sort(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()));
        if (oreList.isEmpty()) oreList = List.of(Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE, Blocks.COAL_ORE);

        List<Holder<Biome>> biomes = new ArrayList<>();
        registries.lookup(Registries.BIOME).ifPresent(l -> l.listElements().forEach(h -> {
            if (!h.is(EXCLUDED_BIOMES) && !h.key().identifier().equals(Interstellar.id("space"))) biomes.add(h);
        }));
        biomes.sort(Comparator.comparing(h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse("")));

        List<EntityType<?>> creatures = new ArrayList<>(), monsters = new ArrayList<>();
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            if (!t.canSummon() || t.builtInRegistryHolder().is(EXCLUDED_ENTITIES)) continue;
            if (t.getCategory() == MobCategory.CREATURE) creatures.add(t);
            else if (t.getCategory() == MobCategory.MONSTER && !t.equals(com.cstestcs.interstellar.registry.ModEntities.ALIEN.get())) monsters.add(t);
        }
        Comparator<EntityType<?>> byId = Comparator.comparing(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString());
        creatures.sort(byId);
        monsters.sort(byId);
        return new Pools(List.copyOf(blocks), List.copyOf(biomes), List.copyOf(oreList), List.copyOf(creatures), List.copyOf(monsters));
    }

    /**
     * The snapshot used every full, solid block that is not a point of interest. Blocks that fall, tick, hold a block
     * entity, let light through or cannot be broken are left out too, so planets stay stable, dark underground and
     * minable.
     */
    static boolean usableAsDefaultBlock(BlockState s) {
        try {
            if (s.isAir() || s.hasBlockEntity() || s.isRandomlyTicking() || s.getBlock() instanceof FallingBlock) return false;
            if (s.is(EXCLUDED_BLOCKS) || !s.getFluidState().isEmpty()) return false;
            if (s.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 0) return false;
            if (!s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || !s.canOcclude()) return false;
            return net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(s).isEmpty();
        } catch (RuntimeException e) {
            return false; // a modded block that cannot answer without a world
        }
    }

    public static BlockState defaultBlock(PlanetGenome g, HolderLookup.Provider registries) {
        List<Block> blocks = get(registries).blocks();
        return blocks.isEmpty() ? Blocks.STONE.defaultBlockState() : PlanetGenome.pick(blocks, g.blockRoll()).defaultBlockState();
    }

    public static BlockState defaultFluid(PlanetGenome g, HolderLookup.Provider registries) {
        return switch (g.fluid()) {
            case WATER -> Blocks.WATER.defaultBlockState();
            case LAVA -> Blocks.LAVA.defaultBlockState();
            case BLOCK -> {
                List<Block> blocks = get(registries).blocks();
                yield blocks.isEmpty() ? Blocks.WATER.defaultBlockState() : PlanetGenome.pick(blocks, g.fluidRoll()).defaultBlockState();
            }
        };
    }

    public static ResourceKey<Biome> key(Holder<Biome> h) {
        return h.unwrapKey().orElseThrow();
    }
}

package com.cstestcs.interstellar.space;

import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.core.space.PlanetLayout;
import com.cstestcs.interstellar.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Builds a planet out of blocks: a cube whose surface is painted to resemble its dimension (continents and oceans
 * for the overworld, netherrack for the nether, the theme blocks of a foreign world, a stable colour scheme for modded
 * dimensions), wrapped in an invisible glowing atmosphere shell.
 */
public final class PlanetBuilder {
    private PlanetBuilder() {}

    /** Dyed blocks are looked up by id (26.3 no longer exposes every colour as a Blocks constant). */
    private static final String[] MODDED_PALETTE = { "terracotta", "red_terracotta", "orange_terracotta", "yellow_terracotta", "lime_terracotta",
            "cyan_terracotta", "light_blue_terracotta", "purple_terracotta", "magenta_terracotta", "brown_terracotta", "white_terracotta", "gray_terracotta" };

    public static void build(ServerLevel space, PlanetLayout.Planet planet, @Nullable ServerLevel target) {
        int n = planet.size();
        int x0 = planet.minX(), y0 = planet.minY(), z0 = planet.minZ();
        int flags = Block.UPDATE_CLIENTS;
        long seed = planet.dimension().hashCode() * 0x9E3779B97F4A7C15L;
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    boolean surface = x == 0 || y == 0 || z == 0 || x == n - 1 || y == n - 1 || z == n - 1;
                    if (!surface) {
                        space.setBlock(new BlockPos(x0 + x, y0 + y, z0 + z), core(planet.dimension()), flags);
                        continue;
                    }
                    space.setBlock(new BlockPos(x0 + x, y0 + y, z0 + z), surface(planet.dimension(), x, y, z, n, seed), flags);
                }
            }
        }
        // Atmosphere: a hollow cube PlanetLayout.ATMOSPHERE_GAP blocks out from the surface.
        int[] a = planet.atmosphere();
        BlockState atmosphere = ModBlocks.ATMOSPHERE.get().defaultBlockState();
        for (int x = a[0]; x < a[3]; x++) {
            for (int y = a[1]; y < a[4]; y++) {
                for (int z = a[2]; z < a[5]; z++) {
                    boolean shell = x == a[0] || y == a[1] || z == a[2] || x == a[3] - 1 || y == a[4] - 1 || z == a[5] - 1;
                    if (shell) space.setBlock(new BlockPos(x, y, z), atmosphere, flags);
                }
            }
        }
    }

    private static BlockState core(String dim) {
        return switch (dim) {
            case "minecraft:the_nether" -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case "minecraft:the_end" -> Blocks.END_STONE.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    private static BlockState surface(String dim, int x, int y, int z, int n, long seed) {
        int h = hash(x, y, z, seed);
        switch (dim) {
            case "minecraft:overworld": {
                // Polar caps on top and bottom, continents of grass with sandy coasts in blue ocean elsewhere.
                if (y == n - 1 || y == 0) return (h & 3) == 0 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
                double land = continent(x, y, z, n, seed);
                if (land > 0.15) return (h & 7) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                if (land > 0.0) return Blocks.SAND.defaultBlockState();
                return block((h & 3) == 0 ? "minecraft:light_blue_concrete" : "minecraft:blue_concrete", Blocks.WATER).defaultBlockState();
            }
            case "minecraft:the_nether":
                return Blocks.NETHERRACK.defaultBlockState();
            case "minecraft:the_end":
                return (h % 9) == 0 ? Blocks.PURPUR_BLOCK.defaultBlockState() : Blocks.END_STONE.defaultBlockState();
            default:
                break;
        }
        ForeignWorlds.World fw = ForeignWorlds.byDimension(dim);
        if (fw != null) {
            String id = (h % 7) == 0 ? fw.theme().accentBlock : (h % 3) == 0 ? fw.theme().underBlock : fw.theme().topBlock;
            return block(id, Blocks.STONE).defaultBlockState();
        }
        if (dim.contains("moon")) return ((h & 3) == 0 ? Blocks.GRAVEL : block("minecraft:light_gray_concrete_powder", Blocks.GRAVEL)).defaultBlockState();
        // Unknown modded dimension: a stable two-colour scheme picked from its id.
        int base = Math.floorMod((int) (seed >>> 20), MODDED_PALETTE.length);
        int accent = Math.floorMod((int) (seed >>> 40), MODDED_PALETTE.length);
        return block("minecraft:" + MODDED_PALETTE[(h % 5) == 0 ? accent : base], Blocks.TERRACOTTA).defaultBlockState();
    }

    private static double continent(int x, int y, int z, int n, long seed) {
        double c = (n - 1) / 2.0;
        double dx = (x - c) / n, dy = (y - c) / n, dz = (z - c) / n;
        double v = 0;
        for (int i = 0; i < 3; i++) {
            long s = seed + i * 0x632BE59BD9B4E019L;
            double fx = 3 + (s & 3), fy = 3 + ((s >>> 2) & 3), fz = 3 + ((s >>> 4) & 3);
            v += Math.sin(dx * fx * 6.28 + (s >>> 8 & 15)) * Math.cos(dz * fz * 6.28 + (s >>> 12 & 15)) * Math.sin(dy * fy * 6.28 + 1 + (s >>> 16 & 15)) / (i + 1);
        }
        return v;
    }

    private static int hash(int x, int y, int z, long seed) {
        long h = seed ^ (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
        h = (h ^ (h >>> 31)) * 0x7fb5d329728ea185L;
        return (int) ((h ^ (h >>> 27)) & 0x7fffffff);
    }

    private static Block block(String id, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElse(fallback);
    }
}

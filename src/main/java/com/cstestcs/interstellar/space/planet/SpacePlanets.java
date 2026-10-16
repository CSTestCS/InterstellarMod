package com.cstestcs.interstellar.space.planet;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.core.space.PlanetGenome;
import com.cstestcs.interstellar.registry.ModAttachments;
import com.cstestcs.interstellar.registry.ModBlocks;
import com.cstestcs.interstellar.space.SpaceTravel;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Fills space with procedural planets as it is explored: whenever a space chunk loads for the first time, the
 * planets whose cubes (and atmospheres) reach into it are built there, so the galaxy appears around players without
 * ever being generated ahead of them. Each planet is drawn from its genome: its random default block, its seas, and
 * patches of its species' blocks.
 */
@EventBusSubscriber(modid = Interstellar.MODID)
public final class SpacePlanets {
    private static final Deque<Long> PENDING = new ArrayDeque<>();

    private SpacePlanets() {}

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !SpaceTravel.isSpace(level)) return;
        LevelChunk chunk = event.getChunk();
        if (Boolean.TRUE.equals(chunk.getData(ModAttachments.PLANETS_BUILT))) return;
        synchronized (PENDING) {
            PENDING.add(((long) chunk.getPos().x() << 32) | (chunk.getPos().z() & 0xFFFFFFFFL));
        }
    }

    /** Blocks are placed on the next tick of space, not while the chunk is still being loaded. */
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !SpaceTravel.isSpace(level)) return;
        for (int n = 0; n < 16; n++) {
            long packed;
            synchronized (PENDING) {
                if (PENDING.isEmpty()) return;
                packed = PENDING.poll();
            }
            ChunkPos pos = new ChunkPos((int) (packed >> 32), (int) packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null || Boolean.TRUE.equals(chunk.getData(ModAttachments.PLANETS_BUILT))) continue;
            build(level, pos);
            chunk.setData(ModAttachments.PLANETS_BUILT, true);
        }
    }

    static void build(ServerLevel space, ChunkPos chunk) {
        int cx0 = chunk.getMinBlockX(), cz0 = chunk.getMinBlockZ();
        for (PlanetGenome g : PlanetGenome.near(cx0 + 8, cz0 + 8, 40)) {
            int[] a = g.atmosphere();
            if (a[3] <= cx0 || a[0] >= cx0 + 16 || a[5] <= cz0 || a[2] >= cz0 + 16) continue;
            buildPart(space, g, cx0, cz0);
        }
    }

    private static void buildPart(ServerLevel space, PlanetGenome g, int cx0, int cz0) {
        BlockState stone = PlanetPools.defaultBlock(g, space.registryAccess());
        BlockState sea = seaLook(g, space);
        ForeignWorlds.Theme theme = ForeignWorlds.all().get(g.species()).theme();
        BlockState top = block(theme.topBlock), accent = block(theme.accentBlock);
        int flagsBlob = Block.UPDATE_CLIENTS;
        if (g.blob()) {
            // End-like planets: an irregular rock, wrapped in an atmosphere that follows its surface.
            int[] a = g.atmosphere();
            BlockState atmosphere = ModBlocks.ATMOSPHERE.get().defaultBlockState();
            BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
            for (int x = Math.max(a[0], cx0); x < Math.min(a[3], cx0 + 16); x++) {
                for (int z = Math.max(a[2], cz0); z < Math.min(a[5], cz0 + 16); z++) {
                    for (int y = a[1]; y < a[4]; y++) {
                        if (g.solidAt(x, y, z)) {
                            boolean surface = !g.solidAt(x + 1, y, z) || !g.solidAt(x - 1, y, z) || !g.solidAt(x, y + 1, z) || !g.solidAt(x, y - 1, z)
                                    || !g.solidAt(x, y, z + 1) || !g.solidAt(x, y, z - 1);
                            int h = hash(x, y, z, g.seed());
                            BlockState s = !surface ? stone : h % 11 == 0 ? accent : h % 4 == 0 ? top : stone;
                            space.setBlock(q.set(x, y, z), s, flagsBlob);
                        } else if (g.atmosphereShellAt(x, y, z) && space.getBlockState(q.set(x, y, z)).isAir()) {
                            space.setBlock(q, atmosphere, flagsBlob);
                        }
                    }
                }
            }
            return;
        }
        int n = g.size(), x0 = g.x() - n / 2, y0 = g.y() - n / 2, z0 = g.z() - n / 2;
        // More sea the higher the planet's sea level (none for planets without seas).
        double seaShare = sea == null ? -1 : (g.seaLevel() - 40) / 160.0;
        int flags = Block.UPDATE_CLIENTS;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = Math.max(x0, cx0); x < Math.min(x0 + n, cx0 + 16); x++) {
            for (int z = Math.max(z0, cz0); z < Math.min(z0 + n, cz0 + 16); z++) {
                for (int y = y0; y < y0 + n; y++) {
                    int lx = x - x0, ly = y - y0, lz = z - z0;
                    boolean surface = lx == 0 || ly == 0 || lz == 0 || lx == n - 1 || ly == n - 1 || lz == n - 1;
                    BlockState s = stone;
                    if (surface) {
                        int h = hash(lx, ly, lz, g.seed());
                        double land = continent(lx, ly, lz, n, g.seed());
                        if (sea != null && land < seaShare) s = sea;
                        else if (h % 11 == 0) s = accent;
                        else if (h % 4 == 0) s = top;
                    }
                    space.setBlock(p.set(x, y, z), s, flags);
                }
            }
        }
        // Atmosphere shell.
        int[] a = g.atmosphere();
        BlockState air = ModBlocks.ATMOSPHERE.get().defaultBlockState();
        for (int x = Math.max(a[0], cx0); x < Math.min(a[3], cx0 + 16); x++) {
            for (int z = Math.max(a[2], cz0); z < Math.min(a[5], cz0 + 16); z++) {
                for (int y = a[1]; y < a[4]; y++) {
                    boolean shell = x == a[0] || y == a[1] || z == a[2] || x == a[3] - 1 || y == a[4] - 1 || z == a[5] - 1;
                    if (shell && space.getBlockState(p.set(x, y, z)).isAir()) space.setBlock(p, air, flags);
                }
            }
        }
    }

    /** How the planet's seas look from space (liquids would flow out of the cube). */
    private static BlockState seaLook(PlanetGenome g, ServerLevel space) {
        if (g.terrain() == PlanetGenome.Terrain.END || g.terrain() == PlanetGenome.Terrain.FLOATING_ISLANDS) return null;
        return switch (g.fluid()) {
            case WATER -> block("minecraft:blue_concrete");
            case LAVA -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case BLOCK -> PlanetPools.defaultFluid(g, space.registryAccess());
        };
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

    private static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElse(Blocks.STONE).defaultBlockState();
    }
}

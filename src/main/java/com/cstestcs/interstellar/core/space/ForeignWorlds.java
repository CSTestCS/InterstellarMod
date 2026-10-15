package com.cstestcs.interstellar.core.space;

import com.cstestcs.interstellar.core.race.RaceFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Deterministic definitions of the 20w14∞-style foreign worlds. Like the snapshot, every world is derived from a
 * hash: terrain template, blocks, fluid, colours and the alien race living there. They are only reachable as planets
 * in space. This class is the single source of truth for both the generated datapack and the alien races.
 */
public final class ForeignWorlds {
    public static final int COUNT = 12;

    public enum Theme {
        CRYSTAL("crystal", "minecraft:amethyst_block", "minecraft:calcite", "minecraft:budding_amethyst", "minecraft:water"),
        MAGMA("nether", "minecraft:basalt", "minecraft:blackstone", "minecraft:magma_block", "minecraft:lava"),
        VOID("void", "minecraft:end_stone", "minecraft:purpur_block", "minecraft:obsidian", "minecraft:air"),
        FUNGAL("bio", "minecraft:mycelium", "minecraft:mushroom_stem", "minecraft:moss_block", "minecraft:water"),
        FROZEN("cryo", "minecraft:snow_block", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:water"),
        TOXIC("plasma", "minecraft:slime_block", "minecraft:lime_terracotta", "minecraft:green_concrete", "minecraft:water"),
        DUNE("solar", "minecraft:red_sand", "minecraft:red_sandstone", "minecraft:orange_terracotta", "minecraft:lava"),
        REEF("hydro", "minecraft:prismarine", "minecraft:dark_prismarine", "minecraft:sea_lantern", "minecraft:water"),
        RUST("tech", "minecraft:oxidized_copper", "minecraft:tuff", "minecraft:copper_block", "minecraft:water"),
        ECHO("sonic", "minecraft:sculk", "minecraft:deepslate", "minecraft:reinforced_deepslate", "minecraft:water"),
        HONEY("bio", "minecraft:honeycomb_block", "minecraft:yellow_terracotta", "minecraft:honey_block", "minecraft:water"),
        BONE("void", "minecraft:bone_block", "minecraft:smooth_basalt", "minecraft:soul_soil", "minecraft:lava");

        public final String weaponTheme;
        public final String topBlock;
        public final String underBlock;
        public final String accentBlock;
        public final String fluid;

        Theme(String weaponTheme, String top, String under, String accent, String fluid) {
            this.weaponTheme = weaponTheme;
            this.topBlock = top;
            this.underBlock = under;
            this.accentBlock = accent;
            this.fluid = fluid;
        }
    }

    /** Which vanilla noise template the terrain is cloned from (20w14∞ also mixed generator types). */
    public enum Terrain { OVERWORLD, AMPLIFIED, LARGE_BIOMES, CAVES, FLOATING_ISLANDS, FLAT }

    private static final String[] STONES = { "minecraft:stone", "minecraft:deepslate", "minecraft:blackstone", "minecraft:end_stone",
            "minecraft:netherrack", "minecraft:sandstone", "minecraft:terracotta", "minecraft:prismarine", "minecraft:tuff",
            "minecraft:calcite", "minecraft:packed_mud", "minecraft:smooth_basalt", "minecraft:purpur_block", "minecraft:quartz_block" };

    public record World(int index, String id, String displayName, Theme theme, Terrain terrain, String defaultBlock,
            String defaultFluid, int seaLevel, int skyColor, int fogColor, int waterColor, int foliageColor,
            String alienRaceId, String alienName, long seed, int planetSize, boolean hasSkylight, float ambientLight) {

        public String dimensionId() {
            return "interstellar:" + id;
        }
    }

    private static final String[] ON = { "zor", "vex", "qua", "thri", "kel", "mor", "xan", "ul", "ryl", "osh", "ny", "drak", "sel", "vor", "ix", "aem" };
    private static final String[] MID = { "a", "o", "i", "u", "ae", "y", "ee", "ou" };
    private static final String[] CODA = { "th", "n", "x", "r", "sk", "l", "q", "rr", "z", "m", "ph", "k" };

    private static final List<World> WORLDS;

    static {
        List<World> list = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) list.add(make(i));
        WORLDS = List.copyOf(list);
    }

    public static List<World> all() {
        return WORLDS;
    }

    public static World byDimension(String dimensionId) {
        for (World w : WORLDS) if (w.dimensionId().equals(dimensionId)) return w;
        return null;
    }

    private static World make(int i) {
        long seed = RaceFiles.mix(0x14_1_14L * 31 + i * 0x9E3779B97F4A7C15L);
        SplittableRandom r = new SplittableRandom(seed);
        Theme theme = Theme.values()[i % Theme.values().length];
        Terrain terrain = Terrain.values()[r.nextInt(Terrain.values().length)];
        String stone = STONES[r.nextInt(STONES.length)];
        String alien = alienName(r);
        String planetName = planetName(r);
        int sky = randomColor(r), fog = randomColor(r), water = randomColor(r), foliage = randomColor(r);
        int sea = terrain == Terrain.FLOATING_ISLANDS ? -64 : 40 + r.nextInt(40);
        String fluid = theme.fluid.equals("minecraft:air") && terrain != Terrain.FLOATING_ISLANDS ? "minecraft:water" : theme.fluid;
        int size = 3 + r.nextInt(12);
        return new World(i, "foreign_" + i, planetName, theme, terrain, stone, fluid, sea, sky, fog, water, foliage,
                "alien_" + alien.toLowerCase(Locale.ROOT), alien, seed, size, terrain != Terrain.CAVES, terrain == Terrain.CAVES ? 0.1f : 0f);
    }

    private static String alienName(SplittableRandom r) {
        String s = ON[r.nextInt(ON.length)] + MID[r.nextInt(MID.length)] + CODA[r.nextInt(CODA.length)];
        if (r.nextBoolean()) s += MID[r.nextInt(MID.length)] + CODA[r.nextInt(CODA.length)];
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String planetName(SplittableRandom r) {
        String s = ON[r.nextInt(ON.length)] + MID[r.nextInt(MID.length)] + CODA[r.nextInt(CODA.length)] + MID[r.nextInt(MID.length)];
        return Character.toUpperCase(s.charAt(0)) + s.substring(1) + " " + (char) ('A' + r.nextInt(6)) + "-" + (1 + r.nextInt(99));
    }

    private static int randomColor(SplittableRandom r) {
        return (r.nextInt(256) << 16) | (r.nextInt(256) << 8) | r.nextInt(256);
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }
}

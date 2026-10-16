package com.cstestcs.interstellar.core.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything about a procedural planet, derived from its position in space alone, the way 20w14∞ derived a whole
 * dimension from one number ({@code DimensionGenerator} / {@code GeneratedDimension}): a seeded random decides, in
 * a fixed order, the terrain template, sea level, default block and fluid, a random set of biomes with random climate
 * points, ores, decorations and creatures. Nothing is stored; the same cell always yields the same planet, on the
 * server and on every client.
 * <p>
 * Space is cut into square cells of {@link #CELL} blocks. Most cells hold one planet; cells near the origin are left
 * to the planets of real dimensions (overworld, nether, end, modded dimensions). A planet's dimension is created
 * only when a ship first enters its atmosphere, like the snapshot's dimensions were created on first use.
 * <p>
 * Registry choices (blocks, biomes, ores, mobs...) are stored as raw random ints; the game picks entries with them
 * from its registries, so modded content takes part automatically.
 */
public record PlanetGenome(
        int cellX, int cellZ, long seed, String path, String name,
        int x, int y, int z, int size,
        int species, int dimensionType, Terrain terrain, int seaLevel,
        int blockRoll, Fluid fluid, int fluidRoll,
        List<BiomePick> biomes, int alienOreRoll, int extraAlienOres,
        List<Integer> oreRolls, List<Integer> featureRolls, List<Integer> creatureRolls, List<Integer> monsterRolls,
        int alienWeight) {

    public static final int CELL = 160;
    /** No procedural planets this close to the origin: that area belongs to the planets of real dimensions. */
    public static final int RESERVED_RADIUS = 640;
    public static final int MIN_Y = 48, MAX_Y = 208;
    public static final String PREFIX = "planet_";
    private static final Pattern PATH = Pattern.compile(PREFIX + "(m?\\d+)_(m?\\d+)");
    private static final long GALAXY = 0x20_14_1_14L;

    /** Terrain templates (the snapshot picked surface, nether-like or end-like generators). */
    public enum Terrain {
        OVERWORLD("overworld"), AMPLIFIED("amplified"), LARGE_BIOMES("large_biomes"), CAVES("caves"), FLOATING_ISLANDS("floating_islands"),
        NETHER("nether"), END("end");

        /** Vanilla noise settings preset the terrain is built from. */
        public final String preset;

        Terrain(String preset) {
            this.preset = preset;
        }
    }

    /** What fills the seas: like the snapshot, usually water or lava, sometimes any block. */
    public enum Fluid { WATER, LAVA, BLOCK }

    /** A biome picked by a random int, with one or more random climate points. */
    public record BiomePick(int roll, List<Point> climate) {}

    /** A multi-noise climate point (offset weighs it against the others). */
    public record Point(float temperature, float humidity, float continentalness, float erosion, float depth, float weirdness, float offset) {}

    public String dimensionPath() {
        return path;
    }

    public String dimensionId() {
        return "interstellar:" + path;
    }

    private static final java.util.Map<Long, PlanetShape> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    /** The planet's shape in space (cached). */
    public PlanetShape shape() {
        if (SHAPES.size() > 4096) SHAPES.clear();
        return SHAPES.computeIfAbsent(seed, k -> new PlanetShape(x - size / 2, y - size / 2, z - size / 2, size, blob(), seed));
    }

    /** Half-open bounds of the atmosphere around the planet. */
    public int[] atmosphere() {
        return shape().atmosphere();
    }

    /** End-like planets are irregular rocks rather than cubes, with an atmosphere that follows their surface. */
    public boolean blob() {
        return terrain == Terrain.END;
    }

    public double centerX() {
        return shape().centerX();
    }

    public double centerY() {
        return shape().centerY();
    }

    public double centerZ() {
        return shape().centerZ();
    }

    public double surfaceDistance(double px, double py, double pz) {
        return shape().surfaceDistance(px, py, pz);
    }

    public boolean solidAt(int bx, int by, int bz) {
        return shape().solidAt(bx, by, bz);
    }

    public boolean atmosphereShellAt(int bx, int by, int bz) {
        return shape().atmosphereShellAt(bx, by, bz);
    }

    public boolean touchesAtmosphere(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return shape().touchesAtmosphere(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public PlanetLayout.Planet asPlanet() {
        return new PlanetLayout.Planet(dimensionId(), size, x, y, z, false);
    }

    public static boolean isPlanet(String dimensionId) {
        return parse(dimensionId).isPresent();
    }

    /** The planet behind a dimension id such as {@code interstellar:planet_12_m4}. */
    public static Optional<PlanetGenome> parse(String dimensionId) {
        String p = dimensionId.startsWith("interstellar:") ? dimensionId.substring("interstellar:".length()) : dimensionId;
        Matcher m = PATH.matcher(p);
        if (!m.matches()) return Optional.empty();
        return at(coord(m.group(1)), coord(m.group(2)));
    }

    private static final java.util.Map<Long, Optional<PlanetGenome>> CELLS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The planet of a cell, if the cell has one (cached: asked every tick by ships and every frame by the sky). */
    public static Optional<PlanetGenome> at(int cellX, int cellZ) {
        long key = ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
        Optional<PlanetGenome> cached = CELLS.get(key);
        if (cached != null) return cached;
        if (CELLS.size() > 8192) CELLS.clear();
        Optional<PlanetGenome> made = compute(cellX, cellZ);
        CELLS.put(key, made);
        return made;
    }

    private static Optional<PlanetGenome> compute(int cellX, int cellZ) {
        double cx = (cellX + 0.5) * CELL, cz = (cellZ + 0.5) * CELL;
        if (cx * cx + cz * cz < (double) RESERVED_RADIUS * RESERVED_RADIUS) return Optional.empty();
        long seed = mix(GALAXY ^ (cellX * 0x9E3779B97F4A7C15L) ^ (cellZ * 0xC2B2AE3D27D4EB4FL));
        Random r = new Random(seed);
        if (r.nextInt(10) < 3) return Optional.empty(); // empty space
        return Optional.of(generate(cellX, cellZ, seed, r));
    }

    /** Planets whose cells overlap a square of {@code radius} blocks around a point. */
    public static List<PlanetGenome> near(double px, double pz, double radius) {
        List<PlanetGenome> out = new ArrayList<>();
        int x0 = (int) Math.floor((px - radius) / CELL), x1 = (int) Math.floor((px + radius) / CELL);
        int z0 = (int) Math.floor((pz - radius) / CELL), z1 = (int) Math.floor((pz + radius) / CELL);
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) at(cx, cz).ifPresent(out::add);
        return out;
    }

    /** The planet whose atmosphere contains the point, if any. */
    public static Optional<PlanetGenome> atmosphereAt(double px, double py, double pz) {
        for (PlanetGenome g : near(px, pz, 24)) {
            if (g.touchesAtmosphere(px, py, pz, px, py, pz)) return Optional.of(g);
        }
        return Optional.empty();
    }

    private static PlanetGenome generate(int cellX, int cellZ, long seed, Random r) {
        // Placement in space: anywhere in the cell that keeps the atmosphere inside it.
        int size = 3 + r.nextInt(12);
        int margin = size / 2 + PlanetLayout.ATMOSPHERE_GAP + 4;
        int x = cellX * CELL + margin + r.nextInt(CELL - 2 * margin);
        int z = cellZ * CELL + margin + r.nextInt(CELL - 2 * margin);
        int y = MIN_Y + r.nextInt(MAX_Y - MIN_Y);
        String name = name(r);
        int species = r.nextInt(ForeignWorlds.COUNT);
        int dimType = r.nextInt(PlanetTypes.COUNT);

        // Generator, as in GeneratedDimension: one of three families, then that family's random settings.
        Terrain terrain = switch (r.nextInt(3)) {
            case 0 -> new Terrain[] { Terrain.OVERWORLD, Terrain.OVERWORLD, Terrain.AMPLIFIED, Terrain.LARGE_BIOMES, Terrain.CAVES,
                    Terrain.FLOATING_ISLANDS }[r.nextInt(6)];
            case 1 -> Terrain.NETHER;
            default -> Terrain.END;
        };
        // OverworldGeneratorSettings(Random): sea level nextInt(128); nether/end keep theirs.
        int sea = switch (terrain) {
            case NETHER -> 32;
            case END, FLOATING_ISLANDS -> -64;
            default -> r.nextInt(128);
        };
        // ChunkGeneratorSettings: default block is any full block; the fluid is water or lava four times in five, else any block.
        int blockRoll = r.nextInt(Integer.MAX_VALUE);
        Fluid fluid;
        int fluidRoll = 0;
        if (r.nextInt(5) != 2) {
            fluid = r.nextBoolean() ? Fluid.WATER : Fluid.LAVA;
        } else {
            fluid = Fluid.BLOCK;
            fluidRoll = r.nextInt(Integer.MAX_VALUE);
        }

        // Biomes: IntStream.range(2, nextInt(15)) random registry entries, each with a list of random climate points;
        // then more random biomes while nextBoolean().
        List<BiomePick> biomes = new ArrayList<>();
        int count = Math.max(1, r.nextInt(15) - 2);
        for (int i = 0; i < count; i++) biomes.add(new BiomePick(r.nextInt(Integer.MAX_VALUE), climatePoints(r)));
        while (r.nextBoolean()) biomes.add(new BiomePick(r.nextInt(Integer.MAX_VALUE), climatePoints(r)));

        // Interstellar additions: the species' own resource, sometimes other alien ores, metals and modded ores,
        // decorations and creatures (all picked from registries), and how common aliens are.
        int alienOreRoll = r.nextInt(Integer.MAX_VALUE);
        int extraAlienOres = r.nextInt(3);
        List<Integer> ores = rolls(r, 3 + r.nextInt(5));
        List<Integer> features = rolls(r, r.nextInt(6));
        List<Integer> creatures = rolls(r, 1 + r.nextInt(5));
        List<Integer> monsters = rolls(r, r.nextInt(4));
        int alienWeight = 40 + r.nextInt(160);
        String path = PREFIX + coordName(cellX) + "_" + coordName(cellZ);
        return new PlanetGenome(cellX, cellZ, seed, path, name, x, y, z, size, species, dimType, terrain, sea, blockRoll, fluid, fluidRoll,
                List.copyOf(biomes), alienOreRoll, extraAlienOres, ores, features, creatures, monsters, alienWeight);
    }

    /** Biome.ClimateParameters.randomList: at least one point, more while nextBoolean(). */
    private static List<Point> climatePoints(Random r) {
        List<Point> out = new ArrayList<>();
        do {
            out.add(new Point(r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, 0f,
                    r.nextFloat() * 2 - 1, r.nextFloat() / 2));
        } while (r.nextBoolean());
        return List.copyOf(out);
    }

    private static List<Integer> rolls(Random r, int n) {
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(r.nextInt(Integer.MAX_VALUE));
        return List.copyOf(out);
    }

    private static final String[] ON = { "zor", "vex", "qua", "thri", "kel", "mor", "xan", "ul", "ryl", "osh", "ny", "drak", "sel", "vor", "ix", "aem",
            "bel", "cy", "fen", "gor", "hal", "jun", "lo", "pri", "tau", "wy" };
    private static final String[] MID = { "a", "o", "i", "u", "ae", "y", "ee", "ou", "ia", "e" };
    private static final String[] CODA = { "th", "n", "x", "r", "sk", "l", "q", "rr", "z", "m", "ph", "k", "s", "v" };

    private static String name(Random r) {
        String s = ON[r.nextInt(ON.length)] + MID[r.nextInt(MID.length)] + CODA[r.nextInt(CODA.length)];
        if (r.nextBoolean()) s += MID[r.nextInt(MID.length)];
        return Character.toUpperCase(s.charAt(0)) + s.substring(1) + " " + (char) ('A' + r.nextInt(26)) + "-" + (1 + r.nextInt(999));
    }

    private static String coordName(int c) {
        return c < 0 ? "m" + (-(long) c) : Integer.toString(c);
    }

    private static int coord(String s) {
        return s.startsWith("m") ? -Integer.parseInt(s.substring(1)) : Integer.parseInt(s);
    }

    /** Picks an element with a stored roll. */
    public static <T> T pick(List<T> list, int roll) {
        return list.get(Math.floorMod(roll, list.size()));
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s (%s, %d %d %d, %s)", name, path, x, y, z, terrain);
    }
}

package com.cstestcs.interstellar.core.space;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lays out one block-built planet per dimension in the space dimension. Deterministic for a given set of dimension
 * ids, so planets never move when a server restarts; new (modded) dimensions are appended in their own slots.
 * <p>
 * Planets sit on a flat spiral around the origin at {@link #PLANE_Y}. Moons ("moon" in the path) orbit the first
 * planet of the same namespace. Each planet is a cube of {@code size} blocks wrapped in a hollow cubic atmosphere
 * shell {@link #ATMOSPHERE_GAP} blocks away.
 */
public final class PlanetLayout {
    public static final int PLANE_Y = 128;
    public static final int ATMOSPHERE_GAP = 2;
    public static final int SPACING = 28;
    private static final java.util.Map<Planet, PlanetShape> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    public record Planet(String dimension, int size, int x, int y, int z, boolean moon) {
        /** Lowest corner of the planet cube. */
        public int minX() { return x - size / 2; }
        public int minY() { return y - size / 2; }
        public int minZ() { return z - size / 2; }

        /** Half-open bounds of the atmosphere (inclusive min, exclusive max). */
        public int[] atmosphere() {
            return shape().atmosphere();
        }

        public boolean insideAtmosphere(double px, double py, double pz) {
            return shape().touchesAtmosphere(px, py, pz, px, py, pz);
        }

        /** The End is an irregular rock, like the end-like procedural planets; other planets are cubes. */
        public PlanetShape shape() {
            if (SHAPES.size() > 1024) SHAPES.clear();
            return SHAPES.computeIfAbsent(this, p -> new PlanetShape(p.minX(), p.minY(), p.minZ(), p.size, isEndLike(p.dimension), p.dimension.hashCode()));
        }
    }

    /** Planet size heuristic; explicit overrides win. */
    public static int sizeFor(String dim, double coordinateScale, Map<String, Integer> overrides) {
        Integer o = overrides.get(dim);
        if (o != null) return Math.max(1, Math.min(16, o));
        String path = dim.substring(dim.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        switch (dim) {
            case "minecraft:overworld": return 16;
            case "minecraft:the_nether": return 1;
            case "minecraft:the_end": return 8;
            default: break;
        }
        ForeignWorlds.World fw = ForeignWorlds.byDimension(dim);
        if (fw != null) return fw.planetSize();
        if (path.contains("moon") || path.contains("asteroid")) return 1;
        if (coordinateScale > 1.5) return 1; // nether-like, compressed dimensions are small
        // Stable pseudo-random 4..12 for unknown modded dimensions.
        int h = dim.hashCode() * 0x9E3779B1;
        return 4 + Math.floorMod(h >>> 7, 9);
    }

    /** Position of the n-th main planet on the spiral. */
    public static int[] spiralPosition(int slot) {
        double ang = slot * 2.399963; // golden angle
        double rad = SPACING * (1.5 + Math.sqrt(slot) * 1.6);
        return new int[] { (int) Math.round(Math.cos(ang) * rad), PLANE_Y, (int) Math.round(Math.sin(ang) * rad) };
    }

    /** Position of the n-th (1-based) moon of a host planet. */
    public static int[] moonPosition(Planet host, int n) {
        double ang = n * 2.399963;
        int r = host.size() / 2 + ATMOSPHERE_GAP * 2 + 6 + n * 3;
        return new int[] { host.x() + (int) Math.round(Math.cos(ang) * r), PLANE_Y + (n % 2 == 0 ? 4 : -4), host.z() + (int) Math.round(Math.sin(ang) * r) };
    }

    /** Dimensions whose planet is an irregular rock rather than a cube. */
    public static boolean isEndLike(String dim) {
        return dim.equals("minecraft:the_end") || dim.toLowerCase(Locale.ROOT).contains("asteroid");
    }

    public static boolean isMoonName(String dim) {
        return dim.toLowerCase(Locale.ROOT).contains("moon");
    }

    public static List<Planet> layout(Map<String, Integer> sizes) {
        List<String> dims = new ArrayList<>(sizes.keySet());
        // Vanilla first, then everything else alphabetically for stability.
        dims.sort(Comparator.comparing((String d) -> !d.startsWith("minecraft:")).thenComparing(d -> d));
        List<Planet> out = new ArrayList<>();
        Map<String, Planet> firstInNamespace = new HashMap<>();
        Map<String, Integer> moonsOf = new HashMap<>();
        int slot = 0;
        for (String d : dims) {
            int size = sizes.get(d);
            String ns = d.substring(0, Math.max(0, d.indexOf(':')));
            boolean moon = d.toLowerCase(Locale.ROOT).contains("moon") && firstInNamespace.containsKey(ns);
            if (moon) {
                Planet host = firstInNamespace.get(ns);
                int n = moonsOf.merge(host.dimension(), 1, Integer::sum);
                int[] m = moonPosition(host, n);
                out.add(new Planet(d, size, m[0], m[1], m[2], true));
                continue;
            }
            int[] sp = spiralPosition(slot);
            Planet p = new Planet(d, size, sp[0], sp[1], sp[2], false);
            out.add(p);
            firstInNamespace.putIfAbsent(ns, p);
            slot++;
        }
        return out;
    }
}

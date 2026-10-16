package com.cstestcs.interstellar.core.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the blocks that make up a spacecraft and validates it, independently of Minecraft's world classes.
 * <p>
 * Starting at the control panel, every connected (6-neighbour) non-air block is collected. Landing gear marks the
 * bottom of the ship: the scan never continues below a landing gear block, so the ground the ship stands on is
 * not included; wheels do the same for land vehicles, and vehicle pads (the ground vehicles are assembled on) are
 * never part of a craft. Anything else touching the ship (terrain, trees) would be included, which normally exceeds the block
 * limit and is reported as touching the world.
 */
public final class ShipScanner {
    public enum Kind { AIR, SOLID, UNMOVABLE, LANDING_GEAR, GENERATOR, CONTROL_PANEL, LIFE_SUPPORT, SEAT, WEAPON, THRUSTER, WHEEL, VEHICLE_PAD }

    @FunctionalInterface
    public interface BlockQuery {
        Kind kind(int x, int y, int z);
    }

    public enum Problem { TOO_MANY_BLOCKS, NO_GENERATOR, NO_CONTROL_PANEL, NO_LIFE_SUPPORT, NO_LANDING_GEAR, UNMOVABLE_BLOCK, NOT_ON_LANDING_GEAR,
        NOT_ON_WHEELS, GEAR_AND_WHEELS }

    public record Result(List<long[]> blocks, Map<Kind, Integer> counts, List<Problem> problems, int minY, long[] unmovable) {
        public boolean ok() {
            return problems.isEmpty();
        }

        public int count(Kind k) {
            return counts.getOrDefault(k, 0);
        }

        /** Land vehicle (built on wheels) rather than a spacecraft (built on landing gear). */
        public boolean vehicle() {
            return count(Kind.WHEEL) > 0;
        }
    }

    private static final int[][] DIRS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

    private ShipScanner() {}

    /**
     * @param limit maximum number of blocks; the scan stops early once exceeded.
     */
    public static Result scan(int sx, int sy, int sz, int limit, BlockQuery world) {
        LongSet seen = new LongSet();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        List<long[]> blocks = new ArrayList<>();
        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        List<Problem> problems = new ArrayList<>();
        long[] unmovable = null;
        queue.add(new long[] { sx, sy, sz });
        seen.add(pack(sx, sy, sz));
        int minY = Integer.MAX_VALUE;
        boolean overflow = false;
        while (!queue.isEmpty()) {
            long[] p = queue.poll();
            int x = (int) p[0], y = (int) p[1], z = (int) p[2];
            Kind k = world.kind(x, y, z);
            if (k == Kind.AIR || k == Kind.VEHICLE_PAD) continue; // vehicle pads are the ground vehicles are built on
            if (k == Kind.UNMOVABLE) {
                unmovable = p;
                continue;
            }
            blocks.add(p);
            counts.merge(k, 1, Integer::sum);
            minY = Math.min(minY, y);
            if (blocks.size() > limit) {
                overflow = true;
                break;
            }
            for (int[] d : DIRS) {
                if ((k == Kind.LANDING_GEAR || k == Kind.WHEEL) && d[1] == -1) continue; // gear and wheels are the bottom
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (seen.add(pack(nx, ny, nz))) queue.add(new long[] { nx, ny, nz });
            }
        }
        if (overflow) problems.add(Problem.TOO_MANY_BLOCKS);
        if (unmovable != null) problems.add(Problem.UNMOVABLE_BLOCK);
        int gear = counts.getOrDefault(Kind.LANDING_GEAR, 0), wheels = counts.getOrDefault(Kind.WHEEL, 0);
        if (counts.getOrDefault(Kind.GENERATOR, 0) == 0) problems.add(Problem.NO_GENERATOR);
        if (counts.getOrDefault(Kind.CONTROL_PANEL, 0) == 0) problems.add(Problem.NO_CONTROL_PANEL);
        // Land vehicles never leave the atmosphere, so only spacecraft need life support.
        if (wheels == 0 && counts.getOrDefault(Kind.LIFE_SUPPORT, 0) == 0) problems.add(Problem.NO_LIFE_SUPPORT);
        if (gear > 0 && wheels > 0) problems.add(Problem.GEAR_AND_WHEELS);
        else if (gear == 0 && wheels == 0) problems.add(Problem.NO_LANDING_GEAR);
        else if (!overflow) {
            // Every lowest block must be gear (or wheels): the craft has to be built on top of them.
            Kind bottom = wheels > 0 ? Kind.WHEEL : Kind.LANDING_GEAR;
            for (long[] b : blocks) {
                if (b[1] == minY && world.kind((int) b[0], (int) b[1], (int) b[2]) != bottom) {
                    problems.add(wheels > 0 ? Problem.NOT_ON_WHEELS : Problem.NOT_ON_LANDING_GEAR);
                    break;
                }
            }
        }
        return new Result(blocks, counts, problems, minY == Integer.MAX_VALUE ? sy : minY, unmovable);
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Minimal open-addressing long hash set (avoids boxing during large scans). */
    static final class LongSet {
        private long[] keys = new long[1024];
        private boolean[] used = new boolean[1024];
        private int size;

        boolean add(long k) {
            if (size * 2 >= keys.length) grow();
            int mask = keys.length - 1;
            int i = (int) (mix(k) & mask);
            while (used[i]) {
                if (keys[i] == k) return false;
                i = (i + 1) & mask;
            }
            used[i] = true;
            keys[i] = k;
            size++;
            return true;
        }

        private void grow() {
            long[] ok = keys;
            boolean[] ou = used;
            keys = new long[ok.length * 2];
            used = new boolean[ok.length * 2];
            size = 0;
            for (int i = 0; i < ok.length; i++) if (ou[i]) add(ok[i]);
        }

        private static long mix(long z) {
            z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
            return z ^ (z >>> 33);
        }
    }
}

package com.cstestcs.interstellar.core.space;

import java.util.Random;

/**
 * The shape of a planet in space: a cube, or (for end-like worlds) an irregular rock whose radius varies with
 * direction. Both carry an atmosphere {@link PlanetLayout#ATMOSPHERE_GAP} blocks out from the surface; for rocks it
 * follows the surface. Immutable and cheap to query (no allocation per call).
 */
public final class PlanetShape {
    private final int x0, y0, z0, size;
    private final double cx, cy, cz, half;
    private final boolean blob;
    /** Lobes: unit direction (x, y, z) and strength, flattened. */
    private final double[] lobes;

    /**
     * @param x0   lowest corner of the planet's cube (the rock is centred in that cube)
     * @param seed decides the rock's lumps
     */
    public PlanetShape(int x0, int y0, int z0, int size, boolean blob, long seed) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.size = size;
        this.blob = blob;
        this.half = size / 2.0;
        this.cx = x0 + half;
        this.cy = y0 + half;
        this.cz = z0 + half;
        this.lobes = blob ? makeLobes(seed) : new double[0];
    }

    private static double[] makeLobes(long seed) {
        Random r = new Random(seed ^ 0xB10BL);
        int n = 4 + r.nextInt(4);
        double[] out = new double[n * 4];
        double total = 0;
        for (int i = 0; i < n; i++) {
            double ux = r.nextGaussian(), uy = r.nextGaussian(), uz = r.nextGaussian();
            double len = Math.max(1e-6, Math.sqrt(ux * ux + uy * uy + uz * uz));
            out[i * 4] = ux / len;
            out[i * 4 + 1] = uy / len;
            out[i * 4 + 2] = uz / len;
            out[i * 4 + 3] = r.nextDouble() * 2 - 1;
            total += Math.abs(out[i * 4 + 3]);
        }
        double k = 0.66 / Math.max(total, 1e-6); // lumps up to about +-30 %
        for (int i = 0; i < n; i++) out[i * 4 + 3] *= k;
        return out;
    }

    public boolean blob() {
        return blob;
    }

    public double centerX() {
        return cx;
    }

    public double centerY() {
        return cy;
    }

    public double centerZ() {
        return cz;
    }

    public int size() {
        return size;
    }

    /** Radius of the rock in a direction (unit vector), as a multiple of half the cube size (0.7 to 1.3). */
    public double radiusFactor(double ux, double uy, double uz) {
        double f = 1;
        for (int i = 0; i < lobes.length; i += 4) {
            double dot = ux * lobes[i] + uy * lobes[i + 1] + uz * lobes[i + 2];
            f += lobes[i + 3] * dot * dot * dot;
        }
        return Math.max(0.7, Math.min(1.3, f));
    }

    /** Radius of the rock (blocks) in a direction. */
    public double radius(double ux, double uy, double uz) {
        return half * radiusFactor(ux, uy, uz);
    }

    /** Approximate distance (blocks) of a point from the surface; negative inside. */
    public double surfaceDistance(double px, double py, double pz) {
        double dx = px - cx, dy = py - cy, dz = pz - cz;
        if (!blob) return Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) - half;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return -half;
        return len - half * radiusFactor(dx / len, dy / len, dz / len);
    }

    public boolean solidAt(int bx, int by, int bz) {
        if (!blob) return bx >= x0 && by >= y0 && bz >= z0 && bx < x0 + size && by < y0 + size && bz < z0 + size;
        return surfaceDistance(bx + 0.5, by + 0.5, bz + 0.5) <= 0;
    }

    /** Half-open bounds of the atmosphere: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public int[] atmosphere() {
        int g = PlanetLayout.ATMOSPHERE_GAP;
        if (blob) {
            int r = (int) Math.ceil(size * 0.65) + g + 1;
            int ix = (int) Math.floor(cx), iy = (int) Math.floor(cy), iz = (int) Math.floor(cz);
            return new int[] { ix - r, iy - r, iz - r, ix + r + 1, iy + r + 1, iz + r + 1 };
        }
        return new int[] { x0 - g, y0 - g, z0 - g, x0 + size + g, y0 + size + g, z0 + size + g };
    }

    /** Whether the block is part of the invisible atmosphere shell (one block thick). */
    public boolean atmosphereShellAt(int bx, int by, int bz) {
        if (!blob) {
            int[] a = atmosphere();
            boolean inBox = bx >= a[0] && by >= a[1] && bz >= a[2] && bx < a[3] && by < a[4] && bz < a[5];
            return inBox && (bx == a[0] || by == a[1] || bz == a[2] || bx == a[3] - 1 || by == a[4] - 1 || bz == a[5] - 1);
        }
        double d = surfaceDistance(bx + 0.5, by + 0.5, bz + 0.5);
        int g = PlanetLayout.ATMOSPHERE_GAP;
        return d > g - 1 && d <= g;
    }

    /** Whether a box touches the atmosphere or anything inside it. */
    public boolean touchesAtmosphere(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int[] a = atmosphere();
        if (maxX < a[0] || minX > a[3] || maxY < a[1] || minY > a[4] || maxZ < a[2] || minZ > a[5]) return false;
        if (!blob) return maxX > a[0] && minX < a[3] && maxY > a[1] && minY < a[4] && maxZ > a[2] && minZ < a[5];
        double px = Math.max(minX, Math.min(cx, maxX)), py = Math.max(minY, Math.min(cy, maxY)), pz = Math.max(minZ, Math.min(cz, maxZ));
        return surfaceDistance(px, py, pz) <= PlanetLayout.ATMOSPHERE_GAP;
    }

    /** Whether a block position lies within the atmosphere (the planet and the air around it, protected from explosions). */
    public boolean protects(int bx, int by, int bz) {
        int[] a = atmosphere();
        if (bx < a[0] || by < a[1] || bz < a[2] || bx >= a[3] || by >= a[4] || bz >= a[5]) return false;
        return !blob || surfaceDistance(bx + 0.5, by + 0.5, bz + 0.5) <= PlanetLayout.ATMOSPHERE_GAP;
    }
}

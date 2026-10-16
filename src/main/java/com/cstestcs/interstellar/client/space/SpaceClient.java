package com.cstestcs.interstellar.client.space;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.space.PlanetLayout;
import com.cstestcs.interstellar.network.ModPayloads;
import com.cstestcs.interstellar.space.SpaceTravel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;

/**
 * Client side of the space dimension: a night sky without sun or moon, and the soft glow of every planet's
 * atmosphere, tinted with the sky colour of the dimension the planet stands for.
 */
public final class SpaceClient {
    private static volatile List<ModPayloads.PlanetInfo> planets = List.of();
    /** Glow shells from the surface outwards: extra distance (blocks) and opacity. */
    private static final float[][] GLOW = { { 0.15f, 0.30f }, { 0.6f, 0.20f }, { 1.2f, 0.14f }, { 1.9f, 0.10f }, { 2.7f, 0.07f }, { 3.6f, 0.045f },
            { 4.6f, 0.025f } };

    private SpaceClient() {}

    public static void receive(ModPayloads.Planets payload) {
        planets = List.copyOf(payload.planets());
    }

    public static void clear() {
        planets = List.of();
    }

    public static void registerSky(RegisterCustomEnvironmentEffectRendererEvent event) {
        event.registerSkyboxRenderer(Interstellar.id("space"), new CustomSkyboxRenderer() {
            @Override
            public boolean renderSky(LevelRenderState level, SkyRenderState sky, org.joml.Matrix4fc modelView, com.mojang.renderpearl.api.buffers.GpuBufferSlice fog) {
                // The overworld's night sky. Sun and moon sit straight below (dimension attributes), hidden behind the
                // dark lower disc, which is always drawn here; the stars stay.
                sky.skybox = DimensionType.Skybox.OVERWORLD;
                sky.shouldRenderDarkDisc = true;
                return false;
            }
        });
    }

    public static void submitGlow(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !SpaceTravel.isSpace(mc.level)) return;
        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        double maxDist = Math.max(128, mc.options.getEffectiveRenderDistance() * 16);
        PoseStack pose = event.getPoseStack();
        for (ModPayloads.PlanetInfo info : planets) {
            glow(event, pose, cam, maxDist, new PlanetLayout.Planet(info.dimension(), info.size(), info.x(), info.y(), info.z(), info.moon()), info.color());
        }
        // Procedural planets are computed here from their cells, exactly as the server builds them.
        for (com.cstestcs.interstellar.core.space.PlanetGenome g : nearby(cam.x, cam.z, maxDist)) {
            int color = visible(com.cstestcs.interstellar.core.space.PlanetTypes.get(g.dimensionType()).skyColor());
            if (g.blob()) glowBlob(event, pose, cam, maxDist, g, color);
            else glow(event, pose, cam, maxDist, g.asPlanet(), color);
        }
    }

    /** Outer faces of the glow shells around an irregular planet, in world coordinates (x, y, z per corner, 4 corners per face). */
    private static final java.util.Map<String, float[][]> BLOB_GLOW = new java.util.concurrent.ConcurrentHashMap<>();

    private static float[][] blobGlow(com.cstestcs.interstellar.core.space.PlanetGenome g) {
        if (BLOB_GLOW.size() > 256) BLOB_GLOW.clear();
        return BLOB_GLOW.computeIfAbsent(g.dimensionPath(), k -> {
            float scale = Math.max(0.6f, g.size() / 8f);
            float[][] layers = new float[GLOW.length][];
            for (int i = 0; i < GLOW.length; i++) {
                double t = GLOW[i][0] * scale + 0.3;
                int v = i < 3 ? 1 : 2; // coarser voxels for the faint outer shells
                layers[i] = shellFaces(g, t, v);
            }
            return layers;
        });
    }

    /** Faces of the voxel region within {@code t} blocks of the planet's surface that face outwards. */
    private static float[] shellFaces(com.cstestcs.interstellar.core.space.PlanetGenome g, double t, int v) {
        double r = g.size() * 0.65 + t + 2 * v;
        int n = (int) Math.ceil(2 * r / v) + 2;
        double ox = g.centerX() - r, oy = g.centerY() - r, oz = g.centerZ() - r;
        boolean[] in = new boolean[n * n * n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) for (int k = 0; k < n; k++) {
            in[(i * n + j) * n + k] = g.surfaceDistance(ox + (i + 0.5) * v, oy + (j + 0.5) * v, oz + (k + 0.5) * v) <= t;
        }
        it.unimi.dsi.fastutil.floats.FloatArrayList out = new it.unimi.dsi.fastutil.floats.FloatArrayList();
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) for (int k = 0; k < n; k++) {
            if (!in[(i * n + j) * n + k]) continue;
            for (int[] d : dirs) {
                int a = i + d[0], b = j + d[1], c = k + d[2];
                boolean outside = a < 0 || b < 0 || c < 0 || a >= n || b >= n || c >= n || !in[(a * n + b) * n + c];
                if (!outside) continue;
                float x0 = (float) (ox + i * v), y0 = (float) (oy + j * v), z0 = (float) (oz + k * v), s = v;
                float[] q = switch (d[0] * 1 + d[1] * 2 + d[2] * 3) {
                    case 1 -> new float[] { x0 + s, y0, z0, x0 + s, y0 + s, z0, x0 + s, y0 + s, z0 + s, x0 + s, y0, z0 + s };
                    case -1 -> new float[] { x0, y0, z0, x0, y0, z0 + s, x0, y0 + s, z0 + s, x0, y0 + s, z0 };
                    case 2 -> new float[] { x0, y0 + s, z0, x0, y0 + s, z0 + s, x0 + s, y0 + s, z0 + s, x0 + s, y0 + s, z0 };
                    case -2 -> new float[] { x0, y0, z0, x0 + s, y0, z0, x0 + s, y0, z0 + s, x0, y0, z0 + s };
                    case 3 -> new float[] { x0, y0, z0 + s, x0 + s, y0, z0 + s, x0 + s, y0 + s, z0 + s, x0, y0 + s, z0 + s };
                    default -> new float[] { x0, y0, z0, x0, y0 + s, z0, x0 + s, y0 + s, z0, x0 + s, y0, z0 };
                };
                out.addElements(out.size(), q);
            }
        }
        return out.toFloatArray();
    }

    private static void glowBlob(SubmitCustomGeometryEvent event, PoseStack pose, Vec3 cam, double maxDist,
            com.cstestcs.interstellar.core.space.PlanetGenome g, int color) {
        if (cam.distanceToSqr(g.centerX(), g.centerY(), g.centerZ()) > maxDist * maxDist) return;
        float r = ((color >> 16) & 255) / 255f, gr = ((color >> 8) & 255) / 255f, b = (color & 255) / 255f;
        float[][] layers = blobGlow(g);
        float cx = (float) cam.x, cy = (float) cam.y, cz = (float) cam.z;
        event.getSubmitNodeCollector().submitCustomGeometry(pose, RenderTypes.lightning(), (last, vc) -> {
            Matrix4f m = last.pose();
            for (int l = 0; l < layers.length; l++) {
                float a = GLOW[l][1];
                float[] f = layers[l];
                for (int i = 0; i + 11 < f.length; i += 12) {
                    quad(vc, m, f[i] - cx, f[i + 1] - cy, f[i + 2] - cz, f[i + 3] - cx, f[i + 4] - cy, f[i + 5] - cz, f[i + 6] - cx, f[i + 7] - cy,
                            f[i + 8] - cz, f[i + 9] - cx, f[i + 10] - cy, f[i + 11] - cz, r, gr, b, a);
                }
            }
        });
    }

    private static double cachedX = Double.NaN, cachedZ, cachedRadius;
    private static List<com.cstestcs.interstellar.core.space.PlanetGenome> cachedNear = List.of();

    private static List<com.cstestcs.interstellar.core.space.PlanetGenome> nearby(double x, double z, double radius) {
        int cell = com.cstestcs.interstellar.core.space.PlanetGenome.CELL;
        if (Double.isNaN(cachedX) || Math.abs(x - cachedX) > cell / 2.0 || Math.abs(z - cachedZ) > cell / 2.0 || radius != cachedRadius) {
            cachedNear = com.cstestcs.interstellar.core.space.PlanetGenome.near(x, z, radius + cell);
            cachedX = x;
            cachedZ = z;
            cachedRadius = radius;
        }
        return cachedNear;
    }

    /** Dark sky colours would make an invisible glow: lift them. */
    private static int visible(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int m = Math.max(r, Math.max(g, b));
        if (m >= 160) return c;
        double k = 200.0 / Math.max(1, m);
        return (int) Math.min(255, r * k + 20) << 16 | (int) Math.min(255, g * k + 20) << 8 | (int) Math.min(255, b * k + 20);
    }

    private static void glow(SubmitCustomGeometryEvent event, PoseStack pose, Vec3 cam, double maxDist, PlanetLayout.Planet p, int color) {
        if (cam.distanceToSqr(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5) > maxDist * maxDist) return;
        float r = ((color >> 16) & 255) / 255f, g = ((color >> 8) & 255) / 255f, b = (color & 255) / 255f;
        double x0 = p.minX() - cam.x, y0 = p.minY() - cam.y, z0 = p.minZ() - cam.z;
        double x1 = x0 + p.size(), y1 = y0 + p.size(), z1 = z0 + p.size();
        float scale = Math.max(0.6f, p.size() / 8f);
        event.getSubmitNodeCollector().submitCustomGeometry(pose, RenderTypes.lightning(), (last, vc) -> {
            Matrix4f m = last.pose();
            for (float[] layer : GLOW) {
                float e = layer[0] * scale;
                box(vc, m, x0 - e, y0 - e, z0 - e, x1 + e, y1 + e, z1 + e, r, g, b, layer[1]);
            }
        });
    }

    private static void box(VertexConsumer vc, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b,
            float a) {
        float ax = (float) x0, ay = (float) y0, az = (float) z0, bx = (float) x1, by = (float) y1, bz = (float) z1;
        quad(vc, m, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az, r, g, b, a); // north
        quad(vc, m, bx, ay, bz, ax, ay, bz, ax, by, bz, bx, by, bz, r, g, b, a); // south
        quad(vc, m, ax, ay, bz, ax, ay, az, ax, by, az, ax, by, bz, r, g, b, a); // west
        quad(vc, m, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az, r, g, b, a); // east
        quad(vc, m, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz, r, g, b, a); // up
        quad(vc, m, ax, ay, bz, bx, ay, bz, bx, ay, az, ax, ay, az, r, g, b, a); // down
    }

    /** Both windings, so the glow shows from inside the atmosphere too. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float r, float g, float b, float a) {
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
    }
}

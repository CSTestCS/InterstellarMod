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
            if (g.blob()) glowBlob(event, pose, cam, maxDist, g.dimensionPath(), g.shape(), color);
            else glow(event, pose, cam, maxDist, g.asPlanet(), color);
        }
    }

    /**
     * Glow around an irregular planet: smooth shells that follow its surface (a sphere mesh whose radius in every
     * direction is the rock's radius plus the shell's distance), cached per planet in world coordinates.
     */
    private static final java.util.Map<String, float[][]> BLOB_GLOW = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int LON = 24, LAT = 14;

    private static float[][] blobGlow(String key, com.cstestcs.interstellar.core.space.PlanetShape shape) {
        if (BLOB_GLOW.size() > 256) BLOB_GLOW.clear();
        return BLOB_GLOW.computeIfAbsent(key, k -> {
            float scale = Math.max(0.6f, shape.size() / 8f);
            float[][] layers = new float[GLOW.length][];
            for (int l = 0; l < GLOW.length; l++) {
                double t = 0.6 + GLOW[l][0] * scale;
                float[] grid = new float[(LAT + 1) * (LON + 1) * 3];
                for (int i = 0; i <= LAT; i++) {
                    double phi = Math.PI * i / LAT;
                    for (int j = 0; j <= LON; j++) {
                        double theta = 2 * Math.PI * j / LON;
                        double ux = Math.sin(phi) * Math.cos(theta), uy = Math.cos(phi), uz = Math.sin(phi) * Math.sin(theta);
                        double r = shape.radius(ux, uy, uz) + t;
                        int o = (i * (LON + 1) + j) * 3;
                        grid[o] = (float) (shape.centerX() + ux * r);
                        grid[o + 1] = (float) (shape.centerY() + uy * r);
                        grid[o + 2] = (float) (shape.centerZ() + uz * r);
                    }
                }
                float[] quads = new float[LAT * LON * 12];
                int q = 0;
                for (int i = 0; i < LAT; i++) {
                    for (int j = 0; j < LON; j++) {
                        int[] corners = { i * (LON + 1) + j, (i + 1) * (LON + 1) + j, (i + 1) * (LON + 1) + j + 1, i * (LON + 1) + j + 1 };
                        for (int c : corners) {
                            quads[q++] = grid[c * 3];
                            quads[q++] = grid[c * 3 + 1];
                            quads[q++] = grid[c * 3 + 2];
                        }
                    }
                }
                layers[l] = quads;
            }
            return layers;
        });
    }

    private static void glowBlob(SubmitCustomGeometryEvent event, PoseStack pose, Vec3 cam, double maxDist, String key,
            com.cstestcs.interstellar.core.space.PlanetShape shape, int color) {
        if (cam.distanceToSqr(shape.centerX(), shape.centerY(), shape.centerZ()) > maxDist * maxDist) return;
        float r = ((color >> 16) & 255) / 255f, gr = ((color >> 8) & 255) / 255f, b = (color & 255) / 255f;
        float[][] layers = blobGlow(key, shape);
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
        if (p.shape().blob()) {
            glowBlob(event, pose, cam, maxDist, p.dimension(), p.shape(), color);
            return;
        }
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

    public static void box(VertexConsumer vc, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b,
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

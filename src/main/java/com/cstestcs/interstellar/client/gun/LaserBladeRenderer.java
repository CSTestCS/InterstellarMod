package com.cstestcs.interstellar.client.gun;

import com.cstestcs.interstellar.client.space.SpaceClient;
import com.cstestcs.interstellar.core.gun.GunBuild;
import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.gun.GunDataManager;
import com.cstestcs.interstellar.gun.GunFiring;
import com.cstestcs.interstellar.gun.GunItem;
import com.cstestcs.interstellar.registry.ModComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The blade of a laser or energy sword: a white-hot core wrapped in soft additive glow shells in the colour of the
 * blade's power source, drawn the same way as the glow of planet atmospheres. Each blade family has its own shape. Shown only while the blade has charge.
 * The blade rises along +Y from the top of the hilt, in item model space.
 */
public final class LaserBladeRenderer implements SpecialModelRenderer<LaserBladeRenderer.Blade> {
    /** Colour (RGB), length in blocks, shape (saber, dagger, broad, glaive, prongs, scythe, hammer). */
    public record Blade(int color, float length, String style) {}

    /** Where the hilt's emitter ends, in item model space (blocks). */
    private static final float BASE = 11 / 16f;
    /** Glow shells around each blade piece: how far they reach (blocks) and their opacity. */
    private static final float[][] GLOW = { { 0.4f / 16f, 0.55f }, { 0.9f / 16f, 0.32f }, { 1.5f / 16f, 0.2f }, { 2.3f / 16f, 0.12f }, { 3.3f / 16f, 0.07f } };

    /** One straight piece of blade: core box (blocks, item space). */
    private record Piece(float x0, float y0, float z0, float x1, float y1, float z1) {}

    static List<Piece> shape(Blade blade) {
        float len = blade.length(), c = 0.5f, w = 0.7f / 16f;
        List<Piece> out = new ArrayList<>();
        switch (blade.style()) {
            case "dagger" -> {
                // Short, tapering to a point.
                int n = 4;
                for (int i = 0; i < n; i++) {
                    float hw = w * (1.3f - i * 0.28f);
                    out.add(new Piece(c - hw, BASE + len * i / n, c - hw * 0.6f, c + hw, BASE + len * (i + 1) / n, c + hw * 0.6f));
                }
            }
            case "broad" -> out.add(new Piece(c - w * 3, BASE, c - w * 0.6f, c + w * 3, BASE + len, c + w * 0.6f));
            case "glaive" -> {
                // Blades out of both ends of a long hilt.
                out.add(new Piece(c - w, BASE, c - w, c + w, BASE + len, c + w));
                out.add(new Piece(c - w, -9 / 16f - len * 0.75f, c - w, c + w, -9 / 16f, c + w));
            }
            case "prongs" -> {
                // Two blades splaying apart, like a split energy sword.
                int n = 6;
                for (int side = -1; side <= 1; side += 2) {
                    for (int i = 0; i < n; i++) {
                        float off = side * (1.2f / 16f + 1.6f / 16f * (float) Math.sin(Math.PI * (i + 0.5) / n));
                        float hw = w * (i == n - 1 ? 0.6f : 0.9f);
                        out.add(new Piece(c + off - hw, BASE + len * i / n, c - hw, c + off + hw, BASE + len * (i + 1) / n, c + hw));
                    }
                }
            }
            case "scythe" -> {
                // A short emitter shaft, then a long blade curving out and down.
                float top = BASE + len * 0.35f;
                out.add(new Piece(c - w * 0.8f, BASE, c - w * 0.8f, c + w * 0.8f, top, c + w * 0.8f));
                int n = 8;
                float reach = len * 0.9f;
                for (int i = 0; i < n; i++) {
                    double a0 = Math.PI * 0.5 * i / n, a1 = Math.PI * 0.5 * (i + 1) / n;
                    float x0 = c + (float) Math.sin(a0) * reach, x1 = c + (float) Math.sin(a1) * reach;
                    float y0 = top - reach * 0.55f + (float) Math.cos(a0) * reach * 0.55f, y1 = top - reach * 0.55f + (float) Math.cos(a1) * reach * 0.55f;
                    float hw = w * (1.2f - i * 0.1f);
                    out.add(new Piece(Math.min(x0, x1) - hw * 0.5f, Math.min(y0, y1) - hw, c - hw * 0.5f, Math.max(x0, x1) + hw * 0.5f, Math.max(y0, y1) + hw, c + hw * 0.5f));
                }
            }
            case "hammer" -> {
                // A glowing shaft topped with a heavy head.
                float top = BASE + len;
                out.add(new Piece(c - w * 0.7f, BASE, c - w * 0.7f, c + w * 0.7f, top, c + w * 0.7f));
                out.add(new Piece(c - 5 / 16f, top, c - 2.2f / 16f, c + 5 / 16f, top + 4 / 16f, c + 2.2f / 16f));
            }
            default -> out.add(new Piece(c - w, BASE, c - w, c + w, BASE + len, c + w)); // saber
        }
        return out;
    }

    @Override
    public void submit(@Nullable Blade blade, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
        if (blade == null) return;
        int c = blade.color();
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        List<Piece> pieces = shape(blade);
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, vc) -> {
            Matrix4f m = pose.pose();
            for (Piece p : pieces) {
                // Core: nearly white, tinted by the colour.
                SpaceClient.box(vc, m, p.x0, p.y0, p.z0, p.x1, p.y1, p.z1, 0.75f + r * 0.25f, 0.75f + g * 0.25f, 0.75f + b * 0.25f, 0.95f);
            }
            for (float[] layer : GLOW) {
                float e = layer[0];
                // Shells of overlapping pieces add up; fade them with the number of pieces so long shapes stay soft.
                float a = layer[1] / (float) Math.sqrt(pieces.size());
                for (Piece p : pieces) SpaceClient.box(vc, m, p.x0 - e, p.y0 - e * 0.4f, p.z0 - e, p.x1 + e, p.y1 + e * 0.6f, p.z1 + e, r, g, b, a);
            }
        });
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        out.accept(new Vector3f(-0.6f, -1.6f, 0.2f));
        out.accept(new Vector3f(1.6f, BASE + 2.8f, 0.8f));
    }

    @Override
    public @Nullable Blade extractArgument(ItemStack stack) {
        GunRegistry.Stats stats = GunItem.stats(stack);
        if (stats == null || stats.fireMode() != GunData.FireMode.MELEE || GunItem.state(stack).ammo() <= 0) return null;
        GunBuild build = stack.get(ModComponents.GUN_BUILD.get());
        GunData.Archetype arch = build == null ? null : GunDataManager.registry().archetype(build.archetype());
        String style = arch == null || arch.bladeStyle() == null ? "saber" : arch.bladeStyle();
        return new Blade(bright(GunFiring.color(stats)), (float) Math.max(0.4, Math.min(2.5, stats.get(GunData.Stat.RANGE))), style);
    }

    /** Dark material colours would barely glow: lift them while keeping their hue. */
    private static int bright(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int max = Math.max(r, Math.max(g, b));
        if (max >= 200) return c;
        double k = 230.0 / Math.max(1, max);
        return (int) Math.min(255, r * k + 10) << 16 | (int) Math.min(255, g * k + 10) << 8 | (int) Math.min(255, b * k + 10);
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Blade> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Blade> bake(SpecialModelRenderer.BakingContext context) {
            return new LaserBladeRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}

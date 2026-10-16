package com.cstestcs.interstellar.client.gun;

import com.cstestcs.interstellar.client.space.SpaceClient;
import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.gun.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
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
 * blade's core material, drawn the same way as the glow of planet atmospheres. Shown only while the blade has charge.
 * The blade rises along +Y from the top of the hilt, in item model space.
 */
public final class LaserBladeRenderer implements SpecialModelRenderer<LaserBladeRenderer.Blade> {
    /** Colour (RGB), length in blocks. */
    public record Blade(int color, float length) {}

    /** Where the hilt's emitter ends, in item model space (blocks). */
    private static final float BASE = 11 / 16f;
    /** Half widths of the core and the glow shells (blocks) and their opacity. */
    private static final float CORE = 0.7f / 16f;
    private static final float[][] GLOW = { { 1.1f / 16f, 0.55f }, { 1.6f / 16f, 0.32f }, { 2.2f / 16f, 0.2f }, { 3.0f / 16f, 0.12f }, { 4.0f / 16f, 0.07f } };

    @Override
    public void submit(@Nullable Blade blade, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
        if (blade == null) return;
        int c = blade.color();
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float top = BASE + blade.length(), cx = 0.5f, cz = 0.5f;
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, vc) -> {
            Matrix4f m = pose.pose();
            // Core: nearly white, tinted by the colour.
            SpaceClient.box(vc, m, cx - CORE, BASE, cz - CORE, cx + CORE, top, cz + CORE, 0.75f + r * 0.25f, 0.75f + g * 0.25f, 0.75f + b * 0.25f, 0.95f);
            for (float[] layer : GLOW) {
                float e = layer[0];
                SpaceClient.box(vc, m, cx - e, BASE - e * 0.3f, cz - e, cx + e, top + e * 0.6f, cz + e, r, g, b, layer[1]);
            }
        });
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        float e = GLOW[GLOW.length - 1][0];
        out.accept(new Vector3f(0.5f - e, BASE, 0.5f - e));
        out.accept(new Vector3f(0.5f + e, BASE + 1.6f, 0.5f + e));
    }

    @Override
    public @Nullable Blade extractArgument(ItemStack stack) {
        GunRegistry.Stats stats = GunItem.stats(stack);
        if (stats == null || stats.fireMode() != GunData.FireMode.MELEE || GunItem.state(stack).ammo() <= 0) return null;
        Integer color = stats.colors().get(GunData.Slot.CORE);
        return new Blade(bright(color == null ? 0x40A0FF : color), (float) Math.max(0.4, Math.min(2.5, stats.get(GunData.Stat.RANGE))));
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

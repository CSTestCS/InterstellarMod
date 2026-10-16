package com.cstestcs.interstellar.core.image;

import java.util.List;

/**
 * Re-lays mob textures that do not use the 64x64 player skin layout (villagers, illagers, witches, the thin-limbed
 * skeleton family) into that layout, so custom bodies can wear them. Each box of the source model is copied face by
 * face onto the matching biped box, scaled to the biped box size. Villager robes become the jacket and the upper
 * part of the pants overlays, and the nose goes to an unused corner of the skin ({@link #NOSE}).
 */
public final class SkinConverter {
    private SkinConverter() {}

    public enum Layout { VILLAGER, ILLAGER, THIN_BIPED }

    /** Where the nose box (2x4x2) is placed in the converted skin. */
    public static final int[] NOSE = { 56, 16, 2, 4, 2 };

    /** Alpha-composites same-layout layers (base texture first, then outfit overlays) into one image. */
    public static Image composite(List<Image> layers) {
        Image base = layers.get(0);
        Image out = new Image(base.width, base.height);
        for (Image layer : layers) {
            for (int y = 0; y < out.height; y++) {
                for (int x = 0; x < out.width; x++) {
                    int sx = x * layer.width / out.width, sy = y * layer.height / out.height;
                    if (sy >= layer.height) continue;
                    out.argb[y * out.width + x] = over(out.argb[y * out.width + x], layer.get(sx, sy));
                }
            }
        }
        return out;
    }

    static int over(int dst, int src) {
        int sa = src >>> 24;
        if (sa == 255) return src;
        if (sa == 0) return dst;
        int da = dst >>> 24;
        int oa = sa + da * (255 - sa) / 255;
        if (oa == 0) return 0;
        int r = (((src >> 16) & 255) * sa + ((dst >> 16) & 255) * da * (255 - sa) / 255) / oa;
        int g = (((src >> 8) & 255) * sa + ((dst >> 8) & 255) * da * (255 - sa) / 255) / oa;
        int b = ((src & 255) * sa + (dst & 255) * da * (255 - sa) / 255) / oa;
        return oa << 24 | r << 16 | g << 8 | b;
    }

    public static Image toBiped(Image src, Layout layout) {
        Image dst = new Image(64, 64);
        double k = src.width / 64.0;
        Converter c = new Converter(src, dst, k);
        switch (layout) {
            case VILLAGER, ILLAGER -> {
                c.box(0, 0, 8, 10, 8, SkinLayout.Part.HEAD, false);
                c.box(32, 0, 8, layout == Layout.ILLAGER ? 12 : 10, 8, SkinLayout.Part.HAT, false);
                c.boxTo(24, 0, 2, 4, 2, NOSE[0], NOSE[1], NOSE[2], NOSE[3], NOSE[4], false);
                c.box(16, 20, 8, 12, 6, SkinLayout.Part.BODY, false);
                robe(c);
                if (layout == Layout.ILLAGER) {
                    c.box(40, 46, 4, 12, 4, SkinLayout.Part.RIGHT_ARM, false);
                    c.box(40, 46, 4, 12, 4, SkinLayout.Part.LEFT_ARM, true);
                } else {
                    c.box(44, 22, 4, 8, 4, SkinLayout.Part.RIGHT_ARM, false);
                    c.box(44, 22, 4, 8, 4, SkinLayout.Part.LEFT_ARM, true);
                }
                c.box(0, 22, 4, 12, 4, SkinLayout.Part.RIGHT_LEG, false);
                c.box(0, 22, 4, 12, 4, SkinLayout.Part.LEFT_LEG, true);
            }
            case THIN_BIPED -> {
                c.box(0, 0, 8, 8, 8, SkinLayout.Part.HEAD, false);
                c.box(32, 0, 8, 8, 8, SkinLayout.Part.HAT, false);
                c.box(16, 16, 8, 12, 4, SkinLayout.Part.BODY, false);
                c.box(40, 16, 2, 12, 2, SkinLayout.Part.RIGHT_ARM, false);
                c.box(40, 16, 2, 12, 2, SkinLayout.Part.LEFT_ARM, true);
                c.box(0, 16, 2, 12, 2, SkinLayout.Part.RIGHT_LEG, false);
                c.box(0, 16, 2, 12, 2, SkinLayout.Part.LEFT_LEG, true);
            }
        }
        return dst;
    }

    /** Villager/illager robe (0,38 8x20x6): top 12 rows become the jacket, the rest the top of the pants. */
    private static void robe(Converter c) {
        int u = 0, v = 38, w = 8, h = 20, d = 6;
        SkinLayout.Part jacket = SkinLayout.Part.JACKET;
        for (SkinLayout.Face f : new SkinLayout.Face[] { SkinLayout.Face.FRONT, SkinLayout.Face.BACK, SkinLayout.Face.LEFT, SkinLayout.Face.RIGHT }) {
            int[] s = SkinLayout.faceRect(u, v, w, h, d, f);
            int[] t = SkinLayout.faceRect(jacket, f);
            c.rect(s[0], s[1], s[2], s[1] + 12, t[0], t[1], t[2], t[3], false);
        }
        int[] top = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.TOP);
        int[] jt = SkinLayout.faceRect(jacket, SkinLayout.Face.TOP);
        c.rect(top[0], top[1], top[2], top[3], jt[0], jt[1], jt[2], jt[3], false);
        // Skirt over the upper legs: front/back halves per leg, outer sides per leg.
        int[] fr = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.FRONT);
        int[] bk = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.BACK);
        int[] rs = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.RIGHT);
        int[] ls = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.LEFT);
        int y0 = fr[1] + 12, y1 = fr[3];
        skirt(c, SkinLayout.Part.RIGHT_PANTS, SkinLayout.Face.FRONT, fr[0], y0, fr[0] + 4, y1);
        skirt(c, SkinLayout.Part.LEFT_PANTS, SkinLayout.Face.FRONT, fr[0] + 4, y0, fr[2], y1);
        skirt(c, SkinLayout.Part.LEFT_PANTS, SkinLayout.Face.BACK, bk[0], y0, bk[0] + 4, y1);
        skirt(c, SkinLayout.Part.RIGHT_PANTS, SkinLayout.Face.BACK, bk[0] + 4, y0, bk[2], y1);
        skirt(c, SkinLayout.Part.RIGHT_PANTS, SkinLayout.Face.RIGHT, rs[0], y0, rs[2], y1);
        skirt(c, SkinLayout.Part.LEFT_PANTS, SkinLayout.Face.LEFT, ls[0], y0, ls[2], y1);
    }

    private static void skirt(Converter c, SkinLayout.Part pants, SkinLayout.Face face, int x0, int y0, int x1, int y1) {
        int[] t = SkinLayout.faceRect(pants, face);
        c.rect(x0, y0, x1, y1, t[0], t[1], t[2], t[1] + (y1 - y0), false);
    }

    private static final class Converter {
        final Image src, dst;
        final double k;

        Converter(Image src, Image dst, double k) {
            this.src = src;
            this.dst = dst;
            this.k = k;
        }

        void box(int u, int v, int w, int h, int d, SkinLayout.Part to, boolean mirror) {
            boxTo(u, v, w, h, d, to.u, to.v, to.w, to.h, to.d, mirror);
        }

        /** Copies every face of a source box onto a destination box; mirrored copies swap left/right and flip. */
        void boxTo(int u, int v, int w, int h, int d, int du, int dv, int dw, int dh, int dd, boolean mirror) {
            for (SkinLayout.Face f : SkinLayout.Face.values()) {
                SkinLayout.Face from = !mirror ? f : f == SkinLayout.Face.LEFT ? SkinLayout.Face.RIGHT : f == SkinLayout.Face.RIGHT ? SkinLayout.Face.LEFT : f;
                int[] s = SkinLayout.faceRect(u, v, w, h, d, from);
                int[] t = SkinLayout.faceRect(du, dv, dw, dh, dd, f);
                rect(s[0], s[1], s[2], s[3], t[0], t[1], t[2], t[3], mirror);
            }
        }

        /** Nearest-neighbour copy of a source rectangle (64-unit coordinates) into a destination rectangle. */
        void rect(int sx0, int sy0, int sx1, int sy1, int tx0, int ty0, int tx1, int ty1, boolean flip) {
            int tw = tx1 - tx0, th = ty1 - ty0;
            if (tw <= 0 || th <= 0 || sx1 <= sx0 || sy1 <= sy0) return;
            for (int ty = 0; ty < th; ty++) {
                for (int tx = 0; tx < tw; tx++) {
                    double fu = (tx + 0.5) / tw, fv = (ty + 0.5) / th;
                    if (flip) fu = 1 - fu;
                    int px = (int) Math.floor((sx0 + fu * (sx1 - sx0)) * k), py = (int) Math.floor((sy0 + fv * (sy1 - sy0)) * k);
                    if (px < 0 || py < 0 || px >= src.width || py >= src.height) continue;
                    dst.set(tx0 + tx, ty0 + ty, src.get(px, py));
                }
            }
        }
    }
}

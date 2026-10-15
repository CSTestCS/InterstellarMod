package com.cstestcs.interstellar.core.image;

/**
 * The standard 64x64 biped skin layout ("box UV"). For a box of size w,h,d whose UV origin is (u,v):
 * <pre>
 *   top    (u+d,       v)    w x d        bottom (u+d+w,   v)    w x d
 *   right  (u,         v+d)  d x h        front  (u+d,     v+d)  w x h
 *   left   (u+d+w,     v+d)  d x h        back   (u+d+w+d, v+d)  w x h
 * </pre>
 */
public final class SkinLayout {
    private SkinLayout() {}

    public enum Part {
        HEAD(0, 0, 8, 8, 8),
        HAT(32, 0, 8, 8, 8),
        BODY(16, 16, 8, 12, 4),
        JACKET(16, 32, 8, 12, 4),
        RIGHT_ARM(40, 16, 4, 12, 4),
        RIGHT_SLEEVE(40, 32, 4, 12, 4),
        LEFT_ARM(32, 48, 4, 12, 4),
        LEFT_SLEEVE(48, 48, 4, 12, 4),
        RIGHT_LEG(0, 16, 4, 12, 4),
        RIGHT_PANTS(0, 32, 4, 12, 4),
        LEFT_LEG(16, 48, 4, 12, 4),
        LEFT_PANTS(0, 48, 4, 12, 4);

        public final int u, v, w, h, d;

        Part(int u, int v, int w, int h, int d) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
            this.d = d;
        }
    }

    /** Faces of a box in the order used by {@link #faceRect}. */
    public enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    /** @return {u0, v0, u1, v1} in pixels for the given face of a box UV region. */
    public static int[] faceRect(int u, int v, int w, int h, int d, Face face) {
        return switch (face) {
            case TOP -> new int[] { u + d, v, u + d + w, v + d };
            case BOTTOM -> new int[] { u + d + w, v, u + d + w + w, v + d };
            case RIGHT -> new int[] { u, v + d, u + d, v + d + h };
            case FRONT -> new int[] { u + d, v + d, u + d + w, v + d + h };
            case LEFT -> new int[] { u + d + w, v + d, u + d + w + d, v + d + h };
            case BACK -> new int[] { u + d + w + d, v + d, u + d + w + d + w, v + d + h };
        };
    }

    public static int[] faceRect(Part p, Face face) {
        return faceRect(p.u, p.v, p.w, p.h, p.d, face);
    }

    /** The otherwise unused top-left 8x8 block that supplies the tail colour/texture. */
    public static final int[] TAIL_REGION = { 0, 0, 8, 8 };
}

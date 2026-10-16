package com.cstestcs.interstellar.core.body;

import com.cstestcs.interstellar.core.fbx.FbxScene;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Maps the standard Mixamo humanoid bones onto the six vanilla biped parts, and maps vanilla part geometry (armor,
 * overlays, any HumanoidModel layer) onto the custom body so it bends with the limbs.
 * <p>
 * Each limb is a two-segment chain J0 -> J1 -> J2 (shoulder/elbow/wrist, hip/knee/ankle, neck/mid-spine/hips).
 * A vanilla vertex's height along its part maps onto the measured extent of the custom limb's mesh along the chain,
 * and its cross-section offset onto the measured cross-section of the segment it lands on. The resulting bind-pose point is skinned with weights that blend across the joint, exactly like the mesh.
 */
public final class HumanoidRig {
    public enum Limb { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    private static final String[][] NAMES = {
            /* HEAD */ { "Head" },
            /* BODY */ { "Neck", "Spine2", "Spine1", "Spine", "Hips" },
            /* RIGHT_ARM */ { "RightArm", "RightForeArm", "RightHand" },
            /* LEFT_ARM */ { "LeftArm", "LeftForeArm", "LeftHand" },
            /* RIGHT_LEG */ { "RightUpLeg", "RightLeg", "RightFoot" },
            /* LEFT_LEG */ { "LeftUpLeg", "LeftLeg", "LeftFoot" } };

    /**
     * Vanilla nominal part geometry, part-local (relative to pivot, pixels): top and bottom y along the part, centre x,
     * half width, half depth.
     */
    private static final float[][] NOMINAL = {
            /* HEAD */ { -8, 0, 0, 4, 4 },
            /* BODY */ { 0, 12, 0, 4, 2 },
            /* RIGHT_ARM */ { -2, 10, -1, 2, 2 },
            /* LEFT_ARM */ { -2, 10, 1, 2, 2 },
            /* RIGHT_LEG */ { 0, 12, 0, 2, 2 },
            /* LEFT_LEG */ { 0, 12, 0, 2, 2 } };

    public static final class Chain {
        public int a = -1, b = -1, c = -1;      // bones owning upper segment, lower segment, end (hand/foot/hips)
        public final Vector3d j0 = new Vector3d(), j1 = new Vector3d(), j2 = new Vector3d(); // MC bind positions
        public final Quaterniond qa = new Quaterniond(), qb = new Quaterniond();          // vanilla +Y -> segment dir
        /** Overall thickness relative to vanilla (upper segment), for diagnostics. */
        public double sx = 1, sz = 1;
        /** Segment lengths and the measured extent of the limb's mesh along the chain (arc length from j0). */
        public double len1 = 1, len2 = 1, tMin = 0, tMax = 2;
        /** Per segment: cross-section centre (side, front) and half extents (side, front), in the segment frame. */
        public final double[][] cross = { { 0, 0, 2, 2 }, { 0, 0, 2, 2 } };
        public boolean valid;
    }

    public final int head, neck, hips, spine, chest;
    public final int leftArm, leftForeArm, leftHand, rightArm, rightForeArm, rightHand;
    public final int leftUpLeg, leftLeg, leftFoot, rightUpLeg, rightLeg, rightFoot;
    private final Chain[] chains = new Chain[Limb.values().length];
    private final BodyModel body;

    HumanoidRig(BodyModel body, FbxScene scene) {
        this.body = body;
        head = body.bone("Head");
        neck = body.bone("Neck");
        hips = body.bone("Hips");
        spine = first(body, "Spine", "Spine1", "Hips");
        chest = first(body, "Spine2", "Spine1", "Spine");
        leftArm = body.bone("LeftArm");
        leftForeArm = body.bone("LeftForeArm");
        leftHand = body.bone("LeftHand");
        rightArm = body.bone("RightArm");
        rightForeArm = body.bone("RightForeArm");
        rightHand = body.bone("RightHand");
        leftUpLeg = body.bone("LeftUpLeg");
        leftLeg = body.bone("LeftLeg");
        leftFoot = body.bone("LeftFoot");
        rightUpLeg = body.bone("RightUpLeg");
        rightLeg = body.bone("RightLeg");
        rightFoot = body.bone("RightFoot");
        for (Limb l : Limb.values()) chains[l.ordinal()] = buildChain(l);
    }

    private static int first(BodyModel b, String... names) {
        for (String n : names) {
            int i = b.bone(n);
            if (i >= 0) return i;
        }
        return -1;
    }

    public boolean isHumanoid() {
        return head >= 0 && chains[Limb.BODY.ordinal()].valid && chains[Limb.RIGHT_ARM.ordinal()].valid
                && chains[Limb.LEFT_ARM.ordinal()].valid && chains[Limb.RIGHT_LEG.ordinal()].valid && chains[Limb.LEFT_LEG.ordinal()].valid;
    }

    public Chain chain(Limb limb) {
        return chains[limb.ordinal()];
    }

    private Chain buildChain(Limb limb) {
        Chain ch = new Chain();
        if (limb == Limb.HEAD) {
            ch.a = ch.b = ch.c = head;
            if (head >= 0) {
                ch.j0.set(body.bindPositionMc(head));
                ch.j1.set(ch.j0).add(0, 4, 0);
                ch.j2.set(ch.j0).add(0, 8, 0);
                ch.valid = true;
            }
            return ch;
        }
        int j0, j1, j2;
        if (limb == Limb.BODY) {
            j0 = first(body, "Neck", "Head", "Spine2");
            j1 = first(body, "Spine1", "Spine", "Spine2");
            j2 = hips;
            ch.a = chest;
            ch.b = spine;
            ch.c = hips;
        } else {
            String[] n = NAMES[limb.ordinal()];
            j0 = body.bone(n[0]);
            j1 = body.bone(n[1]);
            j2 = body.bone(n[2]);
            ch.a = j0;
            ch.b = j1;
            ch.c = j2;
        }
        if (j0 < 0 || j1 < 0 || j2 < 0 || ch.a < 0 || ch.b < 0) return ch;
        ch.j0.set(body.bindPositionMc(j0));
        ch.j1.set(body.bindPositionMc(j1));
        ch.j2.set(body.bindPositionMc(j2));
        if (ch.j0.distance(ch.j1) < 1e-4 || ch.j1.distance(ch.j2) < 1e-4) return ch;
        ch.qa.rotationTo(new Vector3d(0, 1, 0), new Vector3d(ch.j1).sub(ch.j0).normalize());
        ch.qb.rotationTo(new Vector3d(0, 1, 0), new Vector3d(ch.j2).sub(ch.j1).normalize());
        measure(ch, limb);
        ch.valid = true;
        return ch;
    }

    /**
     * Measures the custom limb from the vertices its bones dominate: how far the mesh extends along the chain (so
     * plates cover the limb from end to end, wherever the joints sit inside it) and each segment's cross-section.
     */
    private void measure(Chain ch, Limb limb) {
        ch.len1 = ch.j0.distance(ch.j1);
        ch.len2 = ch.j1.distance(ch.j2);
        Vector3d d1 = new Vector3d(ch.j1).sub(ch.j0).normalize(), d2 = new Vector3d(ch.j2).sub(ch.j1).normalize();
        Vector3d[] side = { ch.qa.transform(new Vector3d(1, 0, 0)), ch.qb.transform(new Vector3d(1, 0, 0)) };
        Vector3d[] front = { ch.qa.transform(new Vector3d(0, 0, 1)), ch.qb.transform(new Vector3d(0, 0, 1)) };
        double tMin = Double.MAX_VALUE, tMax = -Double.MAX_VALUE;
        double[][] mm = { { Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE },
                { Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE } };
        int[] count = new int[2];
        Vector3d p = new Vector3d(), r = new Vector3d();
        for (int v = 0; v < body.vertexCount; v++) {
            int dom = -1;
            float best = 0;
            for (int k = 0; k < 4; k++) {
                float w = body.weights[v * 4 + k];
                if (w > best) {
                    best = w;
                    dom = body.joints[v * 4 + k];
                }
            }
            if (dom < 0 || (dom != ch.a && dom != ch.b && dom != ch.c)) continue;
            p.set(body.pos[v * 3], body.pos[v * 3 + 1], body.pos[v * 3 + 2]);
            double u1 = new Vector3d(p).sub(ch.j0).dot(d1), u2 = new Vector3d(p).sub(ch.j1).dot(d2);
            // Segment: by owning bone, falling back to the nearer projection for shared bones.
            int seg = dom == ch.a && dom != ch.b ? 0 : 1;
            if (dom == ch.a && dom == ch.b) seg = u1 < ch.len1 ? 0 : 1;
            double t = seg == 0 ? u1 : ch.len1 + u2;
            if (seg == 0 && u1 > ch.len1) t = ch.len1;
            tMin = Math.min(tMin, t);
            tMax = Math.max(tMax, t);
            if (seg == 0) r.set(p).sub(ch.j0).fma(-u1, d1);
            else r.set(p).sub(ch.j1).fma(-u2, d2);
            double s = r.dot(side[seg]), f = r.dot(front[seg]);
            mm[seg][0] = Math.min(mm[seg][0], s);
            mm[seg][1] = Math.max(mm[seg][1], s);
            mm[seg][2] = Math.min(mm[seg][2], f);
            mm[seg][3] = Math.max(mm[seg][3], f);
            count[seg]++;
        }
        float[] nom = NOMINAL[limb.ordinal()];
        if (count[0] + count[1] >= 4 && tMax - tMin > 1) {
            ch.tMin = tMin;
            ch.tMax = tMax;
        } else {
            ch.tMin = 0;
            ch.tMax = ch.len1 + ch.len2;
        }
        for (int seg = 0; seg < 2; seg++) {
            int src = count[seg] >= 4 ? seg : count[1 - seg] >= 4 ? 1 - seg : -1;
            if (src < 0) {
                ch.cross[seg][0] = 0;
                ch.cross[seg][1] = 0;
                ch.cross[seg][2] = nom[3];
                ch.cross[seg][3] = nom[4];
                continue;
            }
            double[] m = mm[src];
            ch.cross[seg][0] = (m[0] + m[1]) / 2;
            ch.cross[seg][1] = (m[2] + m[3]) / 2;
            ch.cross[seg][2] = clamp((m[1] - m[0]) / 2, nom[3] * 0.35, nom[3] * 4);
            ch.cross[seg][3] = clamp((m[3] - m[2]) / 2, nom[4] * 0.35, nom[4] * 4);
        }
        ch.sx = ch.cross[0][2] / nom[3];
        ch.sz = ch.cross[0][3] / nom[4];
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Output of {@link #map}: bind-pose MC position and up to three bone influences. */
    public static final class Mapped {
        public final Vector3d pos = new Vector3d();
        public final int[] bone = new int[3];
        public final float[] weight = new float[3];
    }

    /**
     * Maps a vertex of a vanilla part (part-local pixel coordinates, i.e. relative to the part's pivot) onto the
     * custom body's bind pose.
     *
     * @param slim whether the vanilla arm is the 3px slim variant (shifts the nominal centre)
     */
    public void map(Limb limb, double x, double y, double z, boolean slim, Mapped out) {
        Chain ch = chains[limb.ordinal()];
        float[] nom = NOMINAL[limb.ordinal()];
        if (limb == Limb.HEAD || !ch.valid) {
            int b = limb == Limb.HEAD || ch.a < 0 ? head : ch.a;
            out.pos.set(ch.j0).add(x, y, z);
            set(out, b, 1, -1, 0, -1, 0);
            return;
        }
        double cx = nom[2], hw = nom[3], hd = nom[4];
        if (slim && (limb == Limb.RIGHT_ARM || limb == Limb.LEFT_ARM)) {
            cx *= 0.5;
            hw = 1.5;
        }
        // Along the limb: the vanilla part's length maps onto the mesh's extent; anything beyond it (armour
        // inflation) keeps its absolute size.
        double coreY = Math.max(nom[0], Math.min(nom[1], y));
        double t = ch.tMin + (coreY - nom[0]) / (nom[1] - nom[0]) * (ch.tMax - ch.tMin) + (y - coreY);
        boolean upper = t < ch.len1;
        Vector3d base = new Vector3d();
        if (upper) {
            new Vector3d(ch.j1).sub(ch.j0).normalize().mul(t, base).add(ch.j0);
        } else {
            new Vector3d(ch.j2).sub(ch.j1).normalize().mul(t - ch.len1, base).add(ch.j1);
        }
        // Across: the vanilla cross-section is scaled onto the segment's measured one (blended near the joint).
        double total = Math.max(1e-6, ch.tMax - ch.tMin);
        double blend = Math.max(0, Math.min(1, (t - (ch.len1 - total * 0.12)) / (total * 0.24)));
        double cs = lerp(ch.cross[0][0], ch.cross[1][0], blend), cf = lerp(ch.cross[0][1], ch.cross[1][1], blend);
        double hs = lerp(ch.cross[0][2], ch.cross[1][2], blend), hf = lerp(ch.cross[0][3], ch.cross[1][3], blend);
        double dx = x - cx, coreX = Math.max(-hw, Math.min(hw, dx));
        double coreZ = Math.max(-hd, Math.min(hd, z));
        double side = cs + coreX / hw * hs + (dx - coreX);
        double front = cf + coreZ / hd * hf + (z - coreZ);
        Vector3d off = (upper ? ch.qa : ch.qb).transform(new Vector3d(side, 0, front));
        out.pos.set(base).add(off);
        // Weights: upper bone, blend across the middle joint, lower bone, slight blend into the end bone.
        double w = total * 0.1;
        if (t <= ch.len1 - w) set(out, ch.a, 1, -1, 0, -1, 0);
        else if (t < ch.len1 + w) {
            float k = (float) ((t - (ch.len1 - w)) / (2 * w));
            set(out, ch.a, 1 - k, ch.b, k, -1, 0);
        } else {
            double end = ch.len1 + ch.len2;
            if (ch.c < 0 || t < end - w) set(out, ch.b, 1, -1, 0, -1, 0);
            else {
                float k = (float) Math.min(0.5, (t - (end - w)) / (2 * w));
                set(out, ch.b, 1 - k, ch.c, k, -1, 0);
            }
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void set(Mapped o, int b0, float w0, int b1, float w1, int b2, float w2) {
        o.bone[0] = Math.max(b0, 0);
        o.weight[0] = b0 < 0 ? 0 : w0;
        o.bone[1] = Math.max(b1, 0);
        o.weight[1] = b1 < 0 ? 0 : w1;
        o.bone[2] = Math.max(b2, 0);
        o.weight[2] = b2 < 0 ? 0 : w2;
    }
}

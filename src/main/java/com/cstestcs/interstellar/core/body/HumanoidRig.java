package com.cstestcs.interstellar.core.body;

import com.cstestcs.interstellar.core.fbx.FbxScene;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Maps the standard Mixamo humanoid bones onto the six vanilla biped parts, and maps vanilla part geometry (armor,
 * overlays, any HumanoidModel layer) onto the custom body so it bends with the limbs.
 * <p>
 * Each limb is a two-segment chain J0 -> J1 -> J2 (shoulder/elbow/wrist, hip/knee/ankle, neck/mid-spine/hips).
 * A vanilla vertex at height y along the part maps to {@code s = y / length}; s in [0, 0.5] runs along the upper
 * segment, [0.5, 1] along the lower one, and the cross-section offset is scaled to the custom limb's measured
 * thickness. The resulting bind-pose point is skinned with weights that blend across the joint, exactly like the mesh.
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

    /** Vanilla nominal part geometry, part-local (relative to pivot): length along +y, centre x, half widths. */
    private static final float[][] NOMINAL = {
            /* HEAD */ { 8, 0, 4, 4 },
            /* BODY */ { 12, 0, 4, 2 },
            /* RIGHT_ARM */ { 10, -1, 2, 2 },
            /* LEFT_ARM */ { 10, 1, 2, 2 },
            /* RIGHT_LEG */ { 12, 0, 2, 2 },
            /* LEFT_LEG */ { 12, 0, 2, 2 } };

    public static final class Chain {
        public int a = -1, b = -1, c = -1;      // bones owning upper segment, lower segment, end (hand/foot/hips)
        public final Vector3d j0 = new Vector3d(), j1 = new Vector3d(), j2 = new Vector3d(); // MC bind positions
        public final Quaterniond qa = new Quaterniond(), qb = new Quaterniond();          // vanilla +Y -> segment dir
        public double sx = 1, sz = 1;
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

    /** Measures the custom limb's cross-section from vertices dominated by the chain's bones. */
    private void measure(Chain ch, Limb limb) {
        Vector3d side = ch.qa.transform(new Vector3d(1, 0, 0));
        Vector3d front = ch.qa.transform(new Vector3d(0, 0, 1));
        Vector3d axis = new Vector3d(ch.j1).sub(ch.j0).normalize();
        double minS = Double.MAX_VALUE, maxS = -Double.MAX_VALUE, minF = Double.MAX_VALUE, maxF = -Double.MAX_VALUE;
        Vector3d p = new Vector3d();
        int n = 0;
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
            if (dom != ch.a && !(limb == Limb.BODY && dom == ch.b)) continue;
            p.set(body.pos[v * 3], body.pos[v * 3 + 1], body.pos[v * 3 + 2]).sub(ch.j0);
            p.fma(-p.dot(axis), axis); // remove the along-axis component
            double s = p.dot(side), f = p.dot(front);
            minS = Math.min(minS, s);
            maxS = Math.max(maxS, s);
            minF = Math.min(minF, f);
            maxF = Math.max(maxF, f);
            n++;
        }
        if (n < 4) return;
        float[] nom = NOMINAL[limb.ordinal()];
        ch.sx = clamp((maxS - minS) / (2 * nom[2]), 0.35, 4);
        ch.sz = clamp((maxF - minF) / (2 * nom[3]), 0.35, 4);
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
        double cx = nom[1];
        if (slim && (limb == Limb.RIGHT_ARM || limb == Limb.LEFT_ARM)) cx *= 0.5;
        double s = y / nom[0];
        Vector3d base = new Vector3d();
        Quaterniond q;
        if (s < 0.5) {
            new Vector3d(ch.j0).lerp(ch.j1, s / 0.5, base);
            q = ch.qa;
        } else {
            new Vector3d(ch.j1).lerp(ch.j2, (s - 0.5) / 0.5, base);
            q = ch.qb;
        }
        Vector3d off = q.transform(new Vector3d((x - cx) * ch.sx, 0, z * ch.sz));
        out.pos.set(base).add(off);
        // Weights: upper bone, blend across the middle joint, lower bone, slight blend into the end bone.
        if (s <= 0.4) set(out, ch.a, 1, -1, 0, -1, 0);
        else if (s < 0.6) {
            float t = (float) ((s - 0.4) / 0.2);
            set(out, ch.a, 1 - t, ch.b, t, -1, 0);
        } else if (s < 0.92 || ch.c < 0) set(out, ch.b, 1, -1, 0, -1, 0);
        else {
            float t = (float) Math.min(0.5, (s - 0.92) / 0.16);
            set(out, ch.b, 1 - t, ch.c, t, -1, 0);
        }
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

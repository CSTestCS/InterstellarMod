package com.cstestcs.interstellar.core.body;

import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Produces bone global matrices (FBX space) for a body from up to two blended animation clips plus vanilla-derived
 * overrides. Without clips the body is driven entirely by the vanilla biped angles, so a body works out of the box
 * even with an empty customanimations folder.
 * <p>
 * Every bone gets a world-space rotation delta D relative to its bind rotation; positions always come from the
 * target body's own bone lengths, so proportions are preserved whatever skeleton the clip was authored on.
 */
public final class PoseSolver {
    /** Vanilla biped angles in MC model space (radians) and override flags. */
    public static final class Input {
        public float headYaw, headPitch;
        public final float[] rightArm = new float[3], leftArm = new float[3], rightLeg = new float[3], leftLeg = new float[3];
        public float bodyXRot, bodyYRot;
        /** Use vanilla arm angles even when a clip plays (attacking, using or aiming an item). */
        public boolean vanillaRightArm, vanillaLeftArm;
        /** Use vanilla legs even when a clip plays (riding). */
        public boolean vanillaLegs;
    }

    private PoseSolver() {}

    /**
     * @param a     current clip sample (nullable)
     * @param b     previous clip sample being blended out (nullable)
     * @param blend weight of {@code a}, 0..1
     */
    public static void solve(BodyModel body, AnimationClip.Sample a, AnimationClip.Sample b, float blend, Input in, Matrix4d[] out) {
        int n = body.boneCount();
        HumanoidRig rig = body.rig;
        Quaterniond[] clipDelta = new Quaterniond[n];
        Vector3d hipsOffset = null;
        if (a != null) {
            for (int i = 0; i < n; i++) {
                Quaterniond da = a.deltaFor(body.bones[i].name);
                if (b != null && blend < 1f) {
                    Quaterniond db = b.deltaFor(body.bones[i].name);
                    if (da == null) da = new Quaterniond();
                    if (db == null) db = new Quaterniond();
                    da = new Quaterniond(db).slerp(da, blend);
                }
                clipDelta[i] = da;
            }
            if (rig.hips >= 0) {
                double bodyHipsY = body.bindGlobal[rig.hips].getTranslation(new Vector3d()).y;
                double ratio = Math.abs(a.hipsBindY()) > 1e-6 ? bodyHipsY / a.hipsBindY() : 1;
                Vector3d off = new Vector3d(a.hipsOffset);
                if (b != null && blend < 1f) off.lerp(b.hipsOffset, 1 - blend, off);
                // Keep the vertical bob; drop horizontal root motion so clips play in place.
                hipsOffset = new Vector3d(0, off.y * ratio, 0);
            }
        }

        Quaterniond[] relative = new Quaterniond[n]; // vanilla: rotation relative to the parent's delta
        Quaterniond[] overlay = new Quaterniond[n];  // extra rotation on top of the clip (head look)
        boolean clip = a != null;
        if (rig.isHumanoid()) {
            Quaterniond look = mcToFbx(in.headPitch, in.headYaw, 0);
            if (clip) {
                overlay[rig.head] = look;
                if (in.vanillaRightArm) relative[rig.rightArm] = limb(body, rig.rightArm, rig.rightForeArm, in.rightArm);
                if (in.vanillaLeftArm) relative[rig.leftArm] = limb(body, rig.leftArm, rig.leftForeArm, in.leftArm);
                if (in.vanillaLegs) {
                    relative[rig.rightUpLeg] = limb(body, rig.rightUpLeg, rig.rightLeg, in.rightLeg);
                    relative[rig.leftUpLeg] = limb(body, rig.leftUpLeg, rig.leftLeg, in.leftLeg);
                }
            } else {
                relative[rig.head] = look;
                relative[rig.rightArm] = limb(body, rig.rightArm, rig.rightForeArm, in.rightArm);
                relative[rig.leftArm] = limb(body, rig.leftArm, rig.leftForeArm, in.leftArm);
                relative[rig.rightUpLeg] = limb(body, rig.rightUpLeg, rig.rightLeg, in.rightLeg);
                relative[rig.leftUpLeg] = limb(body, rig.leftUpLeg, rig.leftLeg, in.leftLeg);
                if (rig.spine >= 0) relative[rig.spine] = mcToFbx(in.bodyXRot, in.bodyYRot, 0);
            }
        }

        Quaterniond[] delta = new Quaterniond[n];
        Vector3d pos = new Vector3d();
        Quaterniond rot = new Quaterniond();
        Vector3d scale = new Vector3d();
        for (int i = 0; i < n; i++) {
            int p = body.parent[i];
            Quaterniond dp = p >= 0 ? delta[p] : new Quaterniond();
            Quaterniond d;
            if (relative[i] != null && (clipDelta[i] == null || !clip || isOverride(rig, i))) d = new Quaterniond(dp).mul(relative[i]);
            else if (clipDelta[i] != null) d = new Quaterniond(clipDelta[i]);
            else d = new Quaterniond(dp);
            if (overlay[i] != null) d = new Quaterniond(dp).mul(overlay[i]).mul(new Quaterniond(dp).invert()).mul(d);
            delta[i] = d;

            body.bindGlobal[i].getNormalizedRotation(rot);
            rot.premul(d);
            if (p >= 0) out[p].transformPosition(body.restLocal[i].getTranslation(pos));
            else body.bindGlobal[i].getTranslation(pos);
            if (i == rig.hips && hipsOffset != null) pos.add(hipsOffset);
            body.bindGlobal[i].getScale(scale);
            out[i].translationRotateScale(pos, rot, scale);
        }
    }

    private static boolean isOverride(HumanoidRig rig, int i) {
        return i == rig.rightArm || i == rig.leftArm || i == rig.rightUpLeg || i == rig.leftUpLeg;
    }

    /** Vanilla limb angles -> FBX rotation, including the rest correction from the body's bind pose to "hanging down". */
    private static Quaterniond limb(BodyModel body, int bone, int child, float[] angles) {
        Quaterniond r = mcToFbx(angles[0], angles[1], angles[2]);
        if (bone < 0 || child < 0) return r;
        Vector3d dir = body.bindGlobal[child].getTranslation(new Vector3d()).sub(body.bindGlobal[bone].getTranslation(new Vector3d()));
        if (dir.lengthSquared() < 1e-12) return r;
        Quaterniond down = new Quaterniond().rotationTo(dir.normalize(), new Vector3d(0, -1, 0));
        return r.mul(down);
    }

    /** MC model rotation (Rz*Ry*Rx) conjugated into the FBX frame (180° about X): Rz(-z)*Ry(-y)*Rx(x). */
    public static Quaterniond mcToFbx(double x, double y, double z) {
        return new Quaterniond().rotateZ(-z).rotateY(-y).rotateX(x);
    }
}

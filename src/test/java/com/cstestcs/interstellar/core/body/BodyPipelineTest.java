package com.cstestcs.interstellar.core.body;

import static org.junit.jupiter.api.Assertions.*;

import com.cstestcs.interstellar.core.fbx.FbxIO;
import com.cstestcs.interstellar.core.fbx.FbxNode;
import com.cstestcs.interstellar.core.fbx.FbxScene;
import java.util.Map;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class BodyPipelineTest {
    private static BodyModel load(String name) throws Exception {
        byte[] fbx = DefaultBodies.files().get(name + ".fbx");
        FbxScene scene = FbxScene.parse(FbxIO.read(fbx));
        return BodyModel.fromScene(BodyMeta.parse(name, null), scene);
    }

    private static Matrix4d[] solve(BodyModel body, AnimationClip.Sample s, PoseSolver.Input in) {
        Matrix4d[] g = new Matrix4d[body.boneCount()];
        for (int i = 0; i < g.length; i++) g[i] = new Matrix4d();
        PoseSolver.solve(body, s, null, 1, in, g);
        return g;
    }

    @Test
    void everyDefaultBodyLoadsAsAHumanoid() throws Exception {
        for (DefaultBodies.Shape shape : DefaultBodies.SHAPES) {
            BodyModel body = load(shape.name());
            assertTrue(body.rig.isHumanoid(), shape.name());
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int v = 0; v < body.vertexCount; v++) {
                minY = Math.min(minY, body.pos[v * 3 + 1]);
                maxY = Math.max(maxY, body.pos[v * 3 + 1]);
                for (int k = 0; k < 2; k++) {
                    float uv = body.uv[v * 2 + k];
                    assertTrue(uv >= 0 && uv <= 1, "uv out of range in " + shape.name());
                }
            }
            assertEquals(24, maxY, 0.6, shape.name() + " feet should be at y=24 (MC space, +y down)");
            Vector3d head = body.bindPositionMc(body.rig.head);
            assertEquals(0, head.y, 1e-6, "neck at y=0");
            // Character's right arm is on -x in MC space like vanilla.
            assertTrue(body.bindPositionMc(body.rig.rightHand).x < 0);
        }
    }

    @Test
    void writerOutputParsesBackIdentically() throws Exception {
        byte[] fbx = DefaultBodies.files().get("buff.fbx");
        FbxNode root = FbxIO.read(fbx);
        byte[] again = FbxIO.write(root.children);
        FbxScene a = FbxScene.parse(root), b = FbxScene.parse(FbxIO.read(again));
        assertEquals(a.bones.size(), b.bones.size());
        assertArrayEquals(a.meshes.get(0).controlPoints, b.meshes.get(0).controlPoints);
    }

    @Test
    void vanillaDriverHangsArmsAndKeepsLegs() throws Exception {
        BodyModel body = load("classic");
        Matrix4d[] g = solve(body, null, new PoseSolver.Input());
        Matrix4f[] skin = new Matrix4f[body.boneCount()];
        for (int i = 0; i < skin.length; i++) skin[i] = new Matrix4f();
        body.skinMatrices(g, skin);
        float[] pos = new float[body.pos.length], nrm = new float[body.nrm.length];
        body.skin(skin, pos, nrm);
        Vector3d shoulder = body.toMc.transformPosition(g[body.rig.rightArm].getTranslation(new Vector3d()));
        Vector3d hand = body.toMc.transformPosition(g[body.rig.rightHand].getTranslation(new Vector3d()));
        assertTrue(hand.y > shoulder.y + 5, "hand should hang below the shoulder (+y is down)");
        assertEquals(shoulder.x, hand.x, 0.5);
        Vector3d foot = body.toMc.transformPosition(g[body.rig.leftFoot].getTranslation(new Vector3d()));
        assertEquals(body.bindPositionMc(body.rig.leftFoot).y, foot.y, 1e-6);
        // Raising the arm forward (vanilla xRot = -90deg) moves the hand in front of the body (-z in MC space).
        PoseSolver.Input in = new PoseSolver.Input();
        in.rightArm[0] = (float) Math.toRadians(-90);
        g = solve(body, null, in);
        hand = body.toMc.transformPosition(g[body.rig.rightHand].getTranslation(new Vector3d()));
        assertTrue(hand.z < -5, "arm raised forward should point to -z, got " + hand);
    }

    @Test
    void retargetsAMixamoStyleClip() throws Exception {
        BodyModel body = load("minifig");
        // Clip authored on a differently proportioned skeleton (the buff body) rotating LeftArm down by 90deg about Z.
        FbxNode root = FbxIO.read(DefaultBodies.files().get("buff.fbx"));
        addRotationClip(root, "mixamorig:LeftArm", -90);
        FbxScene clipScene = FbxScene.parse(root);
        AnimationClip clip = AnimationClip.fromScene("Test Idle", AnimState.IDLE, clipScene);
        assertNotNull(clip);
        Matrix4d[] g = solve(body, clip.sample(0.5), new PoseSolver.Input());
        Vector3d shoulder = body.toMc.transformPosition(g[body.rig.leftArm].getTranslation(new Vector3d()));
        Vector3d hand = body.toMc.transformPosition(g[body.rig.leftHand].getTranslation(new Vector3d()));
        assertTrue(hand.y > shoulder.y + 3, "left arm should be rotated down, shoulder=" + shoulder + " hand=" + hand);
        // Right arm untouched: still in the T-pose, pointing to -x.
        Vector3d rShoulder = body.toMc.transformPosition(g[body.rig.rightArm].getTranslation(new Vector3d()));
        Vector3d rHand = body.toMc.transformPosition(g[body.rig.rightHand].getTranslation(new Vector3d()));
        assertTrue(rHand.x < rShoulder.x - 3);
    }

    @Test
    void armorVerticesMapOntoLimbs() throws Exception {
        BodyModel body = load("buff");
        HumanoidRig.Mapped m = new HumanoidRig.Mapped();
        // Bottom of a vanilla leg cube maps near the foot, and is skinned mostly by the shin/foot.
        body.rig.map(HumanoidRig.Limb.LEFT_LEG, 0, 12, 0, false, m);
        assertEquals(body.bindPositionMc(body.rig.leftFoot).y, m.pos.y, 3);
        assertTrue(m.bone[0] == body.rig.leftLeg || m.bone[0] == body.rig.leftFoot);
        // The middle of the arm blends upper and fore arm.
        body.rig.map(HumanoidRig.Limb.RIGHT_ARM, -1, 5, 0, false, m);
        assertEquals(body.rig.rightArm, m.bone[0]);
        assertEquals(body.rig.rightForeArm, m.bone[1]);
        assertEquals(1f, m.weight[0] + m.weight[1], 1e-5);
        // Buff limbs are thicker than vanilla.
        assertTrue(body.rig.chain(HumanoidRig.Limb.RIGHT_ARM).sx > 1.1);
    }

    @Test
    void animationStatesFromFileNames() {
        assertEquals(AnimState.WALK, AnimState.fromFileName("Walking.fbx"));
        assertEquals(AnimState.SNEAK_WALK, AnimState.fromFileName("Crouched Walking.fbx"));
        assertEquals(AnimState.SNEAK_IDLE, AnimState.fromFileName("Crouch Idle.fbx"));
        assertEquals(AnimState.FALL, AnimState.fromFileName("Falling Idle.fbx"));
        assertEquals(AnimState.RUN, AnimState.fromFileName("Standard Run.fbx"));
        assertEquals(AnimState.SIT, AnimState.fromFileName("Sitting Idle.fbx"));
        assertNull(AnimState.fromFileName("random.fbx"));
    }

    /** Adds an AnimationStack whose curve rotates {@code bone} about Z to {@code degrees} (Mixamo layout). */
    static void addRotationClip(FbxNode root, String bone, double degrees) {
        FbxNode objects = root.first("Objects"), conns = root.first("Connections");
        long boneId = 0;
        for (FbxNode m : objects.all("Model")) if (bone.endsWith(FbxScene.normalizeBoneName(m.stringProp(1))) && m.stringProp(1).contains(FbxScene.normalizeBoneName(bone))) boneId = m.longProp(0);
        assertNotEquals(0, boneId);
        long one = (long) FbxScene.KTIME;
        objects.child("AnimationStack", 9001L, FbxIO.objectName("Take 001", "AnimStack"), "").child("Properties70")
                .child("P", "LocalStop", "KTime", "Time", "", one);
        objects.child("AnimationLayer", 9002L, FbxIO.objectName("BaseLayer", "AnimLayer"), "");
        FbxNode cn = objects.child("AnimationCurveNode", 9003L, FbxIO.objectName("R", "AnimCurveNode"), "");
        FbxNode p = cn.child("Properties70");
        p.child("P", "d|X", "Number", "", "A", 0.0);
        p.child("P", "d|Y", "Number", "", "A", 0.0);
        p.child("P", "d|Z", "Number", "", "A", degrees);
        FbxNode curve = objects.child("AnimationCurve", 9004L, FbxIO.objectName("", "AnimCurve"), "");
        curve.child("KeyTime", new long[] { 0, one });
        curve.child("KeyValueFloat", new float[] { (float) degrees, (float) degrees });
        conns.child("C", "OO", 9002L, 9001L);
        conns.child("C", "OO", 9003L, 9002L);
        conns.child("C", "OP", 9003L, boneId, "Lcl Rotation");
        conns.child("C", "OP", 9004L, 9003L, "d|Z");
    }

    @Test
    void metaParsing() {
        BodyMeta m = BodyMeta.parse("x", "Gender: Female\nWeight: 3\nDisplayName: Lady");
        assertEquals(BodyMeta.BodyGender.FEMALE, m.gender());
        assertEquals(3, m.weight());
        assertTrue(m.gender().suits(com.cstestcs.interstellar.core.race.RaceDefinition.Gender.NULL));
        assertFalse(m.gender().suits(com.cstestcs.interstellar.core.race.RaceDefinition.Gender.MALE));
        assertFalse(BodyMeta.BodyGender.NULL.suits(com.cstestcs.interstellar.core.race.RaceDefinition.Gender.MALE));
        Map<String, byte[]> files = DefaultBodies.files();
        assertEquals(DefaultBodies.SHAPES.size() * 2, files.size());
    }
}

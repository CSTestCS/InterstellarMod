package com.cstestcs.interstellar.core.body;

import com.cstestcs.interstellar.core.fbx.FbxIO;
import com.cstestcs.interstellar.core.fbx.FbxNode;
import com.cstestcs.interstellar.core.image.SkinLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the default custom bodies as real Mixamo-rigged binary FBX files (T-pose, standard "mixamorig:" bone
 * names, 64x64 skin UVs). They go through exactly the same loader as user-supplied models, and any Mixamo animation
 * drives them. Limbs are built as segmented tubes whose joint rings are shared between bones, so they bend smoothly.
 */
public final class DefaultBodies {
    private DefaultBodies() {}

    /** Shape parameters, in skin pixels (the result is renormalised to vanilla height at load time anyway). */
    public record Shape(String name, String display, BodyMeta.BodyGender gender,
            double chestW, double waistW, double hipW, double torsoH, double torsoD,
            double shoulderInset, double upperArmW, double foreArmW, double armD, double armLen,
            double thighW, double shinW, double legD, double legLen, double hipGap,
            boolean blockHands, boolean wideFeet) {}

    public static final List<Shape> SHAPES = List.of(
            new Shape("classic", "Classic (bendy)", BodyMeta.BodyGender.ANY,
                    8, 8, 8, 12, 4, 0, 4, 4, 4, 12, 4, 4, 4, 12, 0, false, false),
            new Shape("buff", "Buff", BodyMeta.BodyGender.MALE,
                    12, 8.5, 9, 12, 6, -0.5, 5.5, 4.5, 5, 12, 5, 4.5, 5, 12, 0.5, false, false),
            new Shape("slim", "Slim", BodyMeta.BodyGender.FEMALE,
                    7.5, 6, 7.5, 12, 4, 0.25, 3, 2.75, 3, 12, 3.75, 3.25, 3.5, 12, 0, false, false),
            new Shape("minifig", "Minifigure", BodyMeta.BodyGender.ANY,
                    6.5, 8.5, 9.5, 9, 5, 0.5, 3, 3, 3, 8, 4.75, 4.75, 5, 9, 0, true, true),
            new Shape("storymode", "Story Mode", BodyMeta.BodyGender.ANY,
                    9.5, 9, 9, 11, 5.5, 0, 4.75, 4.5, 4.75, 11, 4.75, 4.75, 4.75, 11, 0, false, true));

    /** @return file name -> bytes for every default body (.fbx plus its .txt settings). */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Shape s : SHAPES) {
            out.put(s.name + ".fbx", build(s));
            out.put(s.name + ".txt", new BodyMeta(s.name, s.display, s.gender, 10).toText().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return out;
    }

    // ------------------------------------------------------------------ skeleton

    private static final class Bone {
        final String name;
        final Bone parent;
        final double x, y, z; // global bind position
        final long id;

        Bone(String name, Bone parent, double x, double y, double z, long id) {
            this.name = name;
            this.parent = parent;
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
        }
    }

    private static final class Builder {
        final List<Bone> bones = new ArrayList<>();
        final Map<String, Bone> byName = new HashMap<>();
        final List<double[]> cps = new ArrayList<>();
        final List<Map<String, Double>> weights = new ArrayList<>();
        final List<int[]> polys = new ArrayList<>();
        final List<double[]> polyUv = new ArrayList<>(); // u,v per corner (px)
        long nextId = 1000;

        Bone bone(String name, String parent, double x, double y, double z) {
            Bone b = new Bone(name, parent == null ? null : byName.get(parent), x, y, z, nextId++);
            bones.add(b);
            byName.put(name, b);
            return b;
        }

        int cp(double x, double y, double z, Object... boneWeights) {
            cps.add(new double[] { x, y, z });
            Map<String, Double> w = new LinkedHashMap<>();
            for (int i = 0; i < boneWeights.length; i += 2) w.merge((String) boneWeights[i], (Double) boneWeights[i + 1], Double::sum);
            weights.add(w);
            return cps.size() - 1;
        }

        void quad(int a, int b, int c, int d, double[] uvA, double[] uvB, double[] uvC, double[] uvD) {
            polys.add(new int[] { a, b, c, d });
            polyUv.add(new double[] { uvA[0], uvA[1], uvB[0], uvB[1], uvC[0], uvC[1], uvD[0], uvD[1] });
        }
    }

    /** One ring of a tube: centre, half extents and skin weights. Coordinates are in the limb's hanging-down frame. */
    private record Ring(double cx, double y, double cz, double hw, double hd, double t, Object[] weights) {}

    /**
     * Emits a box-UV mapped tube. Rings go from top to bottom; {@code rot} rotates the result about (px,py) in the
     * XY plane (used to lift arms into the T-pose).
     */
    private static void tube(Builder b, List<Ring> rings, int u, int v, int w, int h, int d, double inflate, double rot, double px, double py) {
        int n = rings.size();
        int[][] idx = new int[n][4];
        for (int i = 0; i < n; i++) {
            Ring r = rings.get(i);
            double hw = r.hw + inflate, hd = r.hd + inflate;
            // corners: 0 = (-x,+z front-right), 1 = (+x,+z front-left), 2 = (+x,-z back-left), 3 = (-x,-z back-right)
            double[][] c = { { r.cx - hw, r.cz + hd }, { r.cx + hw, r.cz + hd }, { r.cx + hw, r.cz - hd }, { r.cx - hw, r.cz - hd } };
            for (int k = 0; k < 4; k++) {
                double[] p = rotate(c[k][0], r.y + (i == 0 ? inflate : i == n - 1 ? -inflate : 0), px, py, rot);
                idx[i][k] = b.cp(p[0], p[1], c[k][1], r.weights);
            }
        }
        // Faces in skin layout terms (FBX: front = +Z, character right = -X).
        int[] front = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.FRONT);
        int[] back = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.BACK);
        int[] right = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.RIGHT);
        int[] left = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.LEFT);
        int[] top = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.TOP);
        int[] bottom = SkinLayout.faceRect(u, v, w, h, d, SkinLayout.Face.BOTTOM);
        for (int i = 0; i + 1 < n; i++) {
            double t0 = rings.get(i).t, t1 = rings.get(i + 1).t;
            int[] a = idx[i], c = idx[i + 1];
            // Each face: top-left, top-right, bottom-right, bottom-left as seen from outside.
            side(b, front, t0, t1, a[0], a[1], c[1], c[0]);
            side(b, left, t0, t1, a[1], a[2], c[2], c[1]);
            side(b, back, t0, t1, a[2], a[3], c[3], c[2]);
            side(b, right, t0, t1, a[3], a[0], c[0], c[3]);
        }
        int[] t = idx[0], bt = idx[n - 1];
        // Top: u from character right (-x) to left (+x), v from back to front.
        b.quad(t[3], t[2], t[1], t[0], uv(top[0], top[1]), uv(top[2], top[1]), uv(top[2], top[3]), uv(top[0], top[3]));
        // Bottom: u right -> left, v back -> front (viewed from below).
        b.quad(bt[0], bt[1], bt[2], bt[3], uv(bottom[0], bottom[3]), uv(bottom[2], bottom[3]), uv(bottom[2], bottom[1]), uv(bottom[0], bottom[1]));
    }

    private static void side(Builder b, int[] rect, double t0, double t1, int tl, int tr, int br, int bl) {
        double v0 = rect[1] + (rect[3] - rect[1]) * t0, v1 = rect[1] + (rect[3] - rect[1]) * t1;
        b.quad(tl, bl, br, tr, uv(rect[0], v0), uv(rect[0], v1), uv(rect[2], v1), uv(rect[2], v0));
    }

    private static double[] uv(double u, double v) {
        return new double[] { u, v };
    }

    private static double[] rotate(double x, double y, double px, double py, double deg) {
        if (deg == 0) return new double[] { x, y };
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        double dx = x - px, dy = y - py;
        return new double[] { px + dx * c - dy * s, py + dx * s + dy * c };
    }

    private static Object[] w(Object... bw) {
        return bw;
    }

    // ------------------------------------------------------------------ body

    static byte[] build(Shape s) {
        Builder b = new Builder();
        double legTop = s.legLen;                 // hip joint height
        double neck = legTop + s.torsoH;          // top of torso
        String p = "mixamorig:";
        b.bone(p + "Hips", null, 0, legTop + 1, 0);
        b.bone(p + "Spine", p + "Hips", 0, legTop + s.torsoH * 0.3, 0);
        b.bone(p + "Spine1", p + "Spine", 0, legTop + s.torsoH * 0.55, 0);
        b.bone(p + "Spine2", p + "Spine1", 0, legTop + s.torsoH * 0.8, 0);
        b.bone(p + "Neck", p + "Spine2", 0, neck - 0.25, 0);
        b.bone(p + "Head", p + "Neck", 0, neck, 0);
        b.bone(p + "HeadTop_End", p + "Head", 0, neck + 8, 0);
        double shoulderX = s.chestW / 2 + s.shoulderInset + s.upperArmW / 2;
        double shoulderY = neck - 2;
        for (int side = -1; side <= 1; side += 2) {
            String lr = side > 0 ? "Left" : "Right";
            double sx = side * shoulderX;
            b.bone(p + lr + "Shoulder", p + "Spine2", side * shoulderX * 0.4, shoulderY, 0);
            b.bone(p + lr + "Arm", p + lr + "Shoulder", sx, shoulderY, 0);
            b.bone(p + lr + "ForeArm", p + lr + "Arm", sx + side * s.armLen * 0.45, shoulderY, 0);
            b.bone(p + lr + "Hand", p + lr + "ForeArm", sx + side * s.armLen * 0.8, shoulderY, 0);
            b.bone(p + lr + "HandIndex1", p + lr + "Hand", sx + side * s.armLen, shoulderY, 0);
            double hx = side * (s.hipW / 4 + s.hipGap);
            b.bone(p + lr + "UpLeg", p + "Hips", hx, legTop, 0);
            b.bone(p + lr + "Leg", p + lr + "UpLeg", hx, legTop * 0.5, 0);
            b.bone(p + lr + "Foot", p + lr + "Leg", hx, legTop * 0.1, 0);
            b.bone(p + lr + "ToeBase", p + lr + "Foot", hx, 0.5, 1.5);
        }

        // Torso (+ jacket overlay): neck -> waist -> hips
        for (int layer = 0; layer < 2; layer++) {
            SkinLayout.Part part = layer == 0 ? SkinLayout.Part.BODY : SkinLayout.Part.JACKET;
            double inf = layer == 0 ? 0 : 0.25;
            List<Ring> torso = List.of(
                    new Ring(0, neck, 0, s.chestW / 2, s.torsoD / 2, 0, w(p + "Spine2", 1.0)),
                    new Ring(0, legTop + s.torsoH * 0.55, 0, (s.chestW + s.waistW) / 4, s.torsoD / 2, 0.45, w(p + "Spine1", 0.6, p + "Spine2", 0.4)),
                    new Ring(0, legTop + s.torsoH * 0.25, 0, s.waistW / 2, s.torsoD / 2 * 0.95, 0.75, w(p + "Spine", 0.7, p + "Hips", 0.3)),
                    new Ring(0, legTop, 0, s.hipW / 2, s.torsoD / 2, 1, w(p + "Hips", 1.0)));
            tube(b, torso, part.u, part.v, part.w, part.h, part.d, inf, 0, 0, 0);
        }

        for (int side = -1; side <= 1; side += 2) {
            String lr = side > 0 ? "Left" : "Right";
            double sx = side * shoulderX;
            boolean left = side > 0;
            for (int layer = 0; layer < 2; layer++) {
                SkinLayout.Part arm = left ? (layer == 0 ? SkinLayout.Part.LEFT_ARM : SkinLayout.Part.LEFT_SLEEVE)
                        : (layer == 0 ? SkinLayout.Part.RIGHT_ARM : SkinLayout.Part.RIGHT_SLEEVE);
                double inf = layer == 0 ? 0 : 0.25;
                double top = shoulderY + 2;
                List<Ring> rings = List.of(
                        new Ring(sx, top, 0, s.upperArmW / 2, s.armD / 2, 0, w(p + lr + "Arm", 1.0)),
                        new Ring(sx, top - s.armLen * 0.45 - 2 * 0.45, 0, (s.upperArmW + s.foreArmW) / 4, s.armD / 2, 0.45, w(p + lr + "Arm", 0.5, p + lr + "ForeArm", 0.5)),
                        new Ring(sx, top - (s.armLen + 2) * 0.8, 0, s.foreArmW / 2, s.armD / 2 * 0.95, 0.8, w(p + lr + "ForeArm", 0.5, p + lr + "Hand", 0.5)),
                        new Ring(sx, top - s.armLen - 2, 0, s.foreArmW / 2 * 0.95, s.armD / 2 * 0.9, 1, w(p + lr + "Hand", 1.0)));
                // Arms are authored hanging down, then lifted into the T-pose about the shoulder joint.
                tube(b, rings, arm.u, arm.v, arm.w, arm.h, arm.d, inf, side * 90, sx, shoulderY);
            }
            if (s.blockHands) {
                SkinLayout.Part arm = left ? SkinLayout.Part.LEFT_ARM : SkinLayout.Part.RIGHT_ARM;
                double y0 = shoulderY - s.armLen - 1.5;
                List<Ring> hand = List.of(
                        new Ring(sx, y0, 0.5, 1.6, 1.6, 0.85, w(p + lr + "Hand", 1.0)),
                        new Ring(sx, y0 - 2.5, 0.5, 1.6, 1.6, 1, w(p + lr + "Hand", 1.0)));
                tube(b, hand, arm.u, arm.v, arm.w, arm.h, arm.d, 0, side * 90, sx, shoulderY);
            }
            double hx = side * (s.hipW / 4 + s.hipGap);
            for (int layer = 0; layer < 2; layer++) {
                SkinLayout.Part leg = left ? (layer == 0 ? SkinLayout.Part.LEFT_LEG : SkinLayout.Part.LEFT_PANTS)
                        : (layer == 0 ? SkinLayout.Part.RIGHT_LEG : SkinLayout.Part.RIGHT_PANTS);
                double inf = layer == 0 ? 0 : 0.25;
                double footD = s.wideFeet ? s.legD / 2 + 0.75 : s.legD / 2;
                List<Ring> rings = List.of(
                        new Ring(hx, legTop, 0, s.thighW / 2, s.legD / 2, 0, w(p + lr + "UpLeg", 1.0)),
                        new Ring(hx, legTop * 0.5, 0, (s.thighW + s.shinW) / 4, s.legD / 2, 0.5, w(p + lr + "UpLeg", 0.5, p + lr + "Leg", 0.5)),
                        new Ring(hx, legTop * 0.1, s.wideFeet ? 0.4 : 0, s.shinW / 2, footD, 0.9, w(p + lr + "Leg", 0.5, p + lr + "Foot", 0.5)),
                        new Ring(hx, 0, s.wideFeet ? 0.4 : 0, s.shinW / 2, footD, 1, w(p + lr + "Foot", 1.0)));
                tube(b, rings, leg.u, leg.v, leg.w, leg.h, leg.d, inf, 0, 0, 0);
            }
        }
        return toFbx(b, s.name);
    }

    // ------------------------------------------------------------------ FBX output

    private static byte[] toFbx(Builder b, String name) {
        FbxNode header = new FbxNode("FBXHeaderExtension");
        header.child("FBXHeaderVersion", 1003);
        header.child("FBXVersion", 7400);
        header.child("Creator", "Interstellar default body generator");
        FbxNode global = new FbxNode("GlobalSettings");
        global.child("Version", 1000);
        FbxNode gp = global.child("Properties70");
        gp.child("P", "UpAxis", "int", "Integer", "", 1);
        gp.child("P", "UpAxisSign", "int", "Integer", "", 1);
        gp.child("P", "FrontAxis", "int", "Integer", "", 2);
        gp.child("P", "FrontAxisSign", "int", "Integer", "", 1);
        gp.child("P", "CoordAxis", "int", "Integer", "", 0);
        gp.child("P", "CoordAxisSign", "int", "Integer", "", 1);
        gp.child("P", "UnitScaleFactor", "double", "Number", "", 1.0);

        FbxNode objects = new FbxNode("Objects");
        FbxNode conns = new FbxNode("Connections");
        long geoId = 1, meshModelId = 2, skinId = 3;

        // Geometry
        double[] verts = new double[b.cps.size() * 3];
        for (int i = 0; i < b.cps.size(); i++) System.arraycopy(b.cps.get(i), 0, verts, i * 3, 3);
        int[] pvi = new int[b.polys.size() * 4];
        double[] uvs = new double[b.polys.size() * 8];
        int[] uvIndex = new int[pvi.length];
        for (int i = 0; i < b.polys.size(); i++) {
            int[] q = b.polys.get(i);
            double[] u = b.polyUv.get(i);
            for (int k = 0; k < 4; k++) {
                pvi[i * 4 + k] = k == 3 ? ~q[k] : q[k];
                uvs[i * 8 + k * 2] = u[k * 2] / 64.0;
                uvs[i * 8 + k * 2 + 1] = 1 - u[k * 2 + 1] / 64.0;
                uvIndex[i * 4 + k] = i * 4 + k;
            }
        }
        FbxNode geo = objects.child("Geometry", geoId, FbxIO.objectName(name, "Geometry"), "Mesh");
        geo.child("Vertices", verts);
        geo.child("PolygonVertexIndex", pvi);
        geo.child("GeometryVersion", 124);
        FbxNode luv = geo.child("LayerElementUV", 0);
        luv.child("Version", 101);
        luv.child("Name", "UVMap");
        luv.child("MappingInformationType", "ByPolygonVertex");
        luv.child("ReferenceInformationType", "IndexToDirect");
        luv.child("UV", uvs);
        luv.child("UVIndex", uvIndex);
        FbxNode layer = geo.child("Layer", 0);
        layer.child("Version", 100);
        FbxNode le = layer.child("LayerElement");
        le.child("Type", "LayerElementUV");
        le.child("TypedIndex", 0);

        FbxNode meshModel = objects.child("Model", meshModelId, FbxIO.objectName(name, "Model"), "Mesh");
        meshModel.child("Version", 232);
        meshModel.child("Properties70");
        conns.child("C", "OO", geoId, meshModelId);
        conns.child("C", "OO", meshModelId, 0L);

        // Bones
        for (Bone bone : b.bones) {
            FbxNode m = objects.child("Model", bone.id, FbxIO.objectName(bone.name, "Model"), "LimbNode");
            m.child("Version", 232);
            FbxNode props = m.child("Properties70");
            double px = bone.parent == null ? 0 : bone.parent.x, py = bone.parent == null ? 0 : bone.parent.y, pz = bone.parent == null ? 0 : bone.parent.z;
            props.child("P", "Lcl Translation", "Lcl Translation", "", "A", bone.x - px, bone.y - py, bone.z - pz);
            props.child("P", "Lcl Rotation", "Lcl Rotation", "", "A", 0.0, 0.0, 0.0);
            props.child("P", "Lcl Scaling", "Lcl Scaling", "", "A", 1.0, 1.0, 1.0);
            conns.child("C", "OO", bone.id, bone.parent == null ? 0L : bone.parent.id);
        }

        // Skin
        objects.child("Deformer", skinId, FbxIO.objectName(name, "Deformer"), "Skin").child("Version", 101);
        conns.child("C", "OO", skinId, geoId);
        long clusterId = 5000;
        for (Bone bone : b.bones) {
            List<Integer> idx = new ArrayList<>();
            List<Double> wts = new ArrayList<>();
            for (int i = 0; i < b.weights.size(); i++) {
                Double wt = b.weights.get(i).get(bone.name);
                if (wt != null && wt > 0) {
                    idx.add(i);
                    wts.add(wt);
                }
            }
            if (idx.isEmpty()) continue;
            FbxNode cl = objects.child("Deformer", clusterId, FbxIO.objectName(bone.name, "SubDeformer"), "Cluster");
            cl.child("Version", 100);
            cl.child("Indexes", idx.stream().mapToInt(Integer::intValue).toArray());
            cl.child("Weights", wts.stream().mapToDouble(Double::doubleValue).toArray());
            double[] link = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, bone.x, bone.y, bone.z, 1 };
            double[] inv = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -bone.x, -bone.y, -bone.z, 1 };
            cl.child("Transform", inv);
            cl.child("TransformLink", link);
            conns.child("C", "OO", clusterId, skinId);
            conns.child("C", "OO", bone.id, clusterId);
            clusterId++;
        }
        return FbxIO.write(FbxIO.list(header, global, objects, conns));
    }
}

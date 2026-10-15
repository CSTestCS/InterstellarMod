package com.cstestcs.interstellar.core.body;

import com.cstestcs.interstellar.core.fbx.FbxScene;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * A custom body converted into Minecraft model space, ready for CPU skinning.
 * <p>
 * Model space matches vanilla bipeds: pixels, +Y down, the character faces -Z, the neck (Head bone) sits at y=0 and
 * the feet at y=24. Skeleton maths stays in the FBX frame (so Mixamo curves apply unchanged); {@link #toMc}
 * converts between the two.
 */
public final class BodyModel {
    public final BodyMeta meta;
    public final FbxScene.Bone[] bones;
    public final int[] parent;
    public final Matrix4d[] bindGlobal;
    public final Matrix4d[] invBindGlobal;
    public final Matrix4d[] restLocal;
    /** FBX space -> MC model space (rotation 180° about X, uniform scale, translation). */
    public final Matrix4d toMc;
    public final Matrix4d fromMc;
    public final double scale;

    // Bind-pose vertex data in MC space; quads (triangles are stored as degenerate quads).
    public final float[] pos;
    public final float[] nrm;
    public final float[] uv;
    public final int[] joints;   // 4 per vertex
    public final float[] weights; // 4 per vertex
    public final int vertexCount;

    public final HumanoidRig rig;

    private BodyModel(BodyMeta meta, FbxScene scene, Matrix4d toMc, double scale, float[] pos, float[] nrm, float[] uv,
            int[] joints, float[] weights) {
        this.meta = meta;
        this.bones = scene.bones.toArray(new FbxScene.Bone[0]);
        this.parent = new int[bones.length];
        this.bindGlobal = new Matrix4d[bones.length];
        this.invBindGlobal = new Matrix4d[bones.length];
        this.restLocal = new Matrix4d[bones.length];
        for (int i = 0; i < bones.length; i++) {
            parent[i] = bones[i].parent;
            bindGlobal[i] = new Matrix4d(bones[i].bindGlobal);
            invBindGlobal[i] = new Matrix4d(bindGlobal[i]).invert();
            // Rest local derived from bind globals so parent * local == bind exactly.
            restLocal[i] = parent[i] >= 0 ? new Matrix4d(invBindGlobal[parent[i]]).mul(bindGlobal[i]) : new Matrix4d(bindGlobal[i]);
        }
        this.toMc = toMc;
        this.fromMc = new Matrix4d(toMc).invert();
        this.scale = scale;
        this.pos = pos;
        this.nrm = nrm;
        this.uv = uv;
        this.joints = joints;
        this.weights = weights;
        this.vertexCount = pos.length / 3;
        this.rig = new HumanoidRig(this, scene);
    }

    public int boneCount() {
        return bones.length;
    }

    public int bone(String name) {
        for (int i = 0; i < bones.length; i++) if (bones[i].name.equalsIgnoreCase(name)) return i;
        return -1;
    }

    /** Bind-pose position of a bone's origin in MC space. */
    public Vector3d bindPositionMc(int bone) {
        Vector3d p = bindGlobal[bone].getTranslation(new Vector3d());
        return toMc.transformPosition(p);
    }

    // ------------------------------------------------------------------ construction

    public static final class BodyLoadException extends Exception {
        public BodyLoadException(String msg) {
            super(msg);
        }
    }

    public static BodyModel fromScene(BodyMeta meta, FbxScene scene) throws BodyLoadException {
        if (scene.meshes.isEmpty()) throw new BodyLoadException("no mesh");
        if (scene.bones.isEmpty()) throw new BodyLoadException("no skeleton");
        // Gather bind-pose world positions (FBX space).
        List<double[]> worldPos = new ArrayList<>();
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (FbxScene.Mesh m : scene.meshes) {
            for (int i = 0; i < m.controlPoints.length; i += 3) {
                Vector3d v = m.meshGlobal.transformPosition(new Vector3d(m.controlPoints[i], m.controlPoints[i + 1], m.controlPoints[i + 2]));
                worldPos.add(new double[] { v.x, v.y, v.z });
                minY = Math.min(minY, v.y);
                maxY = Math.max(maxY, v.y);
            }
        }
        double neckY;
        Integer head = scene.boneIndex.get("Head");
        if (head == null) head = scene.boneIndex.get("Neck");
        if (head != null) neckY = scene.bones.get(head).bindGlobal.getTranslation(new Vector3d()).y;
        else neckY = minY + (maxY - minY) * 0.75;
        if (neckY - minY < 1e-6) throw new BodyLoadException("degenerate body height");
        double s = 24.0 / (neckY - minY);
        // MC = translate(0, 24 + minY*s, 0) * scale(s, -s, -s)
        Matrix4d toMc = new Matrix4d().translation(0, 24 + minY * s, 0).scale(s, -s, -s);

        int totalPolyVerts = 0;
        for (FbxScene.Mesh m : scene.meshes) totalPolyVerts += m.polygonVertexIndex.length;
        FloatList pos = new FloatList(totalPolyVerts * 3), nrm = new FloatList(totalPolyVerts * 3), uv = new FloatList(totalPolyVerts * 2), wts = new FloatList(totalPolyVerts * 4);
        IntList jts = new IntList(totalPolyVerts * 4);
        Matrix4d normalMat = new Matrix4d();
        for (FbxScene.Mesh m : scene.meshes) {
            Matrix4d meshToMc = new Matrix4d(toMc).mul(m.meshGlobal);
            meshToMc.normal(normalMat);
            int[] pvi = m.polygonVertexIndex;
            int polyStart = 0;
            for (int i = 0; i < pvi.length; i++) {
                if (pvi[i] >= 0) continue;
                int count = i - polyStart + 1;
                // Fan-triangulate polygons with more than 4 corners; quads and triangles go straight through.
                if (count == 4) emit(m, meshToMc, normalMat, polyStart, new int[] { 0, 1, 2, 3 }, pos, nrm, uv, jts, wts);
                else if (count == 3) emit(m, meshToMc, normalMat, polyStart, new int[] { 0, 1, 2, 2 }, pos, nrm, uv, jts, wts);
                else for (int k = 1; k + 1 < count; k++) emit(m, meshToMc, normalMat, polyStart, new int[] { 0, k, k + 1, k + 1 }, pos, nrm, uv, jts, wts);
                polyStart = i + 1;
            }
        }
        if (pos.size == 0) throw new BodyLoadException("mesh has no polygons");
        return new BodyModel(meta, scene, toMc, s, pos.toArray(), nrm.toArray(), uv.toArray(), jts.toArray(), wts.toArray());
    }

    private static void emit(FbxScene.Mesh m, Matrix4d meshToMc, Matrix4d normalMat, int polyStart, int[] corners,
            FloatList pos, FloatList nrm, FloatList uv, IntList jts, FloatList wts) {
        // toMc is a proper rotation plus uniform scale, so FBX winding stays outward-facing.
        for (int c = 0; c < corners.length; c++) {
            int pv = polyStart + corners[c];
            int cp = m.polygonVertexIndex[pv] < 0 ? ~m.polygonVertexIndex[pv] : m.polygonVertexIndex[pv];
            Vector3d p = meshToMc.transformPosition(new Vector3d(m.controlPoints[cp * 3], m.controlPoints[cp * 3 + 1], m.controlPoints[cp * 3 + 2]));
            pos.add((float) p.x, (float) p.y, (float) p.z);
            if (m.normals != null) {
                Vector3d n = normalMat.transformDirection(new Vector3d(m.normals[pv * 3], m.normals[pv * 3 + 1], m.normals[pv * 3 + 2])).normalize();
                nrm.add((float) n.x, (float) n.y, (float) n.z);
            } else {
                nrm.add(0, -1, 0);
            }
            if (m.uv != null) uv.add((float) m.uv[pv * 2], (float) (1 - m.uv[pv * 2 + 1])); // FBX V is bottom-up
            else uv.add(0, 0);
            int[] bi = m.boneIndex[cp];
            float[] bw = m.boneWeight[cp];
            float sum = 0;
            for (int k = 0; k < 4; k++) {
                int b = bi != null && k < bi.length ? bi[k] : 0;
                float w = bw != null && k < bw.length ? bw[k] : (k == 0 && bi == null ? 1f : 0f);
                jts.add(b);
                wts.add(w);
                sum += w;
            }
            if (sum > 0 && Math.abs(sum - 1) > 1e-4) {
                for (int k = 1; k <= 4; k++) wts.data[wts.size - k] /= sum;
            }
        }
    }

    /**
     * Computes skinning matrices (MC space) for a pose given as FBX-space global bone matrices.
     */
    public void skinMatrices(Matrix4d[] poseGlobal, Matrix4f[] out) {
        Matrix4d tmp = new Matrix4d();
        for (int i = 0; i < bones.length; i++) {
            tmp.set(toMc).mul(poseGlobal[i]).mul(invBindGlobal[i]).mul(fromMc);
            out[i].set(tmp);
        }
    }

    /** CPU skinning into the supplied arrays (positions and normals, MC space). */
    public void skin(Matrix4f[] skin, float[] outPos, float[] outNrm) {
        for (int v = 0; v < vertexCount; v++) {
            float px = pos[v * 3], py = pos[v * 3 + 1], pz = pos[v * 3 + 2];
            float nx = nrm[v * 3], ny = nrm[v * 3 + 1], nz = nrm[v * 3 + 2];
            float rx = 0, ry = 0, rz = 0, qx = 0, qy = 0, qz = 0;
            for (int k = 0; k < 4; k++) {
                float w = weights[v * 4 + k];
                if (w == 0) continue;
                Matrix4f m = skin[joints[v * 4 + k]];
                rx += w * (m.m00() * px + m.m10() * py + m.m20() * pz + m.m30());
                ry += w * (m.m01() * px + m.m11() * py + m.m21() * pz + m.m31());
                rz += w * (m.m02() * px + m.m12() * py + m.m22() * pz + m.m32());
                qx += w * (m.m00() * nx + m.m10() * ny + m.m20() * nz);
                qy += w * (m.m01() * nx + m.m11() * ny + m.m21() * nz);
                qz += w * (m.m02() * nx + m.m12() * ny + m.m22() * nz);
            }
            outPos[v * 3] = rx;
            outPos[v * 3 + 1] = ry;
            outPos[v * 3 + 2] = rz;
            float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (len < 1e-6f) len = 1;
            outNrm[v * 3] = qx / len;
            outNrm[v * 3 + 1] = qy / len;
            outNrm[v * 3 + 2] = qz / len;
        }
    }

    // ------------------------------------------------------------------ tiny growable arrays

    static final class FloatList {
        float[] data;
        int size;

        FloatList(int cap) {
            data = new float[Math.max(16, cap)];
        }

        void add(float... v) {
            if (size + v.length > data.length) data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + v.length));
            System.arraycopy(v, 0, data, size, v.length);
            size += v.length;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }

    static final class IntList {
        int[] data;
        int size;

        IntList(int cap) {
            data = new int[Math.max(16, cap)];
        }

        void add(int v) {
            if (size == data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            data[size++] = v;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}

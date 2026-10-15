package com.cstestcs.interstellar.core.fbx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * Extracts what Interstellar needs from an FBX node tree: the bone hierarchy, skinned meshes and animation curves.
 * Bone names are normalised ("mixamorig:LeftArm", "mixamorig5:LeftArm", "Armature|LeftArm" all become "LeftArm"),
 * which is what lets any Mixamo animation drive any Mixamo-rigged body.
 */
public final class FbxScene {
    /** FBX time units per second. */
    public static final double KTIME = 46186158000.0;

    public static final class Bone {
        public final String name;
        public final long id;
        public int parent = -1;
        public double[] t, r, s, preRotation, postRotation;
        public int rotationOrder;
        /** Global bind matrix: from the skin cluster's TransformLink if present, else from the rest hierarchy. */
        public Matrix4d bindGlobal;

        Bone(String name, long id) {
            this.name = name;
            this.id = id;
        }

        /** Local matrix for the given (possibly animated) T/R/S, honouring pre/post rotation. */
        public Matrix4d local(double[] t, double[] r, double[] s) {
            Matrix4d m = new Matrix4d().translation(t[0], t[1], t[2]);
            m.mul(euler(preRotation, 0));
            m.mul(euler(r, rotationOrder));
            m.mul(euler(postRotation, 0).invert());
            m.scale(s[0], s[1], s[2]);
            return m;
        }

        public Matrix4d restLocal() {
            return local(t, r, s);
        }
    }

    public static final class Mesh {
        public String name;
        public double[] controlPoints;      // xyz per control point, mesh-local
        public int[] polygonVertexIndex;    // FBX encoding: last index of a polygon is bitwise-negated
        public double[] uv;                 // per polygon-vertex uv (already de-indexed)
        public double[] normals;            // per polygon-vertex normal (may be null)
        public Matrix4d meshGlobal = new Matrix4d();
        /** Per control point: up to 4 influences. */
        public int[][] boneIndex;
        public float[][] boneWeight;
    }

    public static final class Curve {
        public final double[] times; // seconds
        public final float[] values;

        Curve(double[] times, float[] values) {
            this.times = times;
            this.values = values;
        }

        public float sample(double t) {
            if (times.length == 0) return 0;
            if (t <= times[0]) return values[0];
            int last = times.length - 1;
            if (t >= times[last]) return values[last];
            int lo = 0, hi = last;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (times[mid] <= t) lo = mid;
                else hi = mid;
            }
            double f = (t - times[lo]) / (times[hi] - times[lo]);
            return (float) (values[lo] + (values[hi] - values[lo]) * f);
        }
    }

    /** Animated channel for one bone: [T|R|S][x|y|z] curves; null entries use the default value. */
    public static final class Channel {
        public final Curve[][] curves = new Curve[3][3];
        public final double[][] defaults = new double[3][];
    }

    public static final class Clip {
        public final String name;
        public double duration;
        public final Map<String, Channel> channels = new HashMap<>();

        Clip(String name) {
            this.name = name;
        }
    }

    public final List<Bone> bones = new ArrayList<>();
    public final Map<String, Integer> boneIndex = new HashMap<>();
    public final List<Mesh> meshes = new ArrayList<>();
    public final List<Clip> clips = new ArrayList<>();
    public int upAxis = 1;
    public double unitScale = 1;

    public static String normalizeBoneName(String raw) {
        String n = raw;
        int c = Math.max(n.lastIndexOf(':'), n.lastIndexOf('|'));
        if (c >= 0) n = n.substring(c + 1);
        return n.strip();
    }

    public Bone bone(String name) {
        Integer i = boneIndex.get(name);
        return i == null ? null : bones.get(i);
    }

    // ------------------------------------------------------------------ parsing

    public static FbxScene parse(FbxNode root) {
        FbxScene scene = new FbxScene();
        FbxNode gs = root.first("GlobalSettings");
        if (gs != null) {
            scene.upAxis = (int) gs.p70Num("UpAxis", 1);
            scene.unitScale = gs.p70Num("UnitScaleFactor", 1);
        }
        FbxNode objects = root.first("Objects");
        if (objects == null) return scene;
        Map<Long, FbxNode> byId = new HashMap<>();
        for (FbxNode o : objects.children) byId.put(o.longProp(0), o);

        // child -> parents (OO) and child -> (parent, property) (OP)
        Map<Long, List<long[]>> childToParent = new HashMap<>();
        Map<Long, List<Long>> parentToChildren = new HashMap<>();
        Map<Long, Map<Long, String>> opProps = new HashMap<>();
        FbxNode conns = root.first("Connections");
        if (conns != null) {
            for (FbxNode c : conns.all("C")) {
                long child = c.longProp(1), parent = c.longProp(2);
                childToParent.computeIfAbsent(child, k -> new ArrayList<>()).add(new long[] { parent });
                parentToChildren.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
                if ("OP".equals(c.stringProp(0)) && c.prop(3) instanceof String p) {
                    opProps.computeIfAbsent(child, k -> new HashMap<>()).put(parent, p);
                }
            }
        }

        // ---- bones: every Model that is not a mesh (LimbNode / Null / Root), plus meshes' parents
        Map<Long, Integer> modelToBone = new HashMap<>();
        for (FbxNode o : objects.all("Model")) {
            String type = o.stringProp(2);
            if ("Mesh".equals(type)) continue;
            Bone b = new Bone(normalizeBoneName(o.stringProp(1)), o.longProp(0));
            b.t = o.p70Vec("Lcl Translation", new double[] { 0, 0, 0 });
            b.r = o.p70Vec("Lcl Rotation", new double[] { 0, 0, 0 });
            b.s = o.p70Vec("Lcl Scaling", new double[] { 1, 1, 1 });
            b.preRotation = o.p70Vec("PreRotation", new double[] { 0, 0, 0 });
            b.postRotation = o.p70Vec("PostRotation", new double[] { 0, 0, 0 });
            b.rotationOrder = (int) o.p70Num("RotationOrder", 0);
            modelToBone.put(b.id, scene.bones.size());
            scene.boneIndex.putIfAbsent(b.name, scene.bones.size());
            scene.bones.add(b);
        }
        for (Bone b : scene.bones) {
            for (long[] p : childToParent.getOrDefault(b.id, List.of())) {
                Integer pi = modelToBone.get(p[0]);
                if (pi != null) {
                    b.parent = pi;
                    break;
                }
            }
        }
        scene.sortBonesParentFirst(modelToBone);
        // Rest globals (overwritten by cluster TransformLink when available).
        Matrix4d[] restGlobal = scene.restGlobals();
        for (int i = 0; i < scene.bones.size(); i++) scene.bones.get(i).bindGlobal = restGlobal[i];

        // ---- meshes
        for (FbxNode geo : objects.all("Geometry")) {
            if (!"Mesh".equals(geo.stringProp(2))) continue;
            Mesh mesh = new Mesh();
            mesh.name = geo.stringProp(1);
            mesh.controlPoints = geo.doubles("Vertices");
            mesh.polygonVertexIndex = geo.ints("PolygonVertexIndex");
            if (mesh.controlPoints == null || mesh.polygonVertexIndex == null) continue;
            mesh.uv = layer(geo, "LayerElementUV", "UV", "UVIndex", 2, mesh.polygonVertexIndex);
            mesh.normals = layer(geo, "LayerElementNormal", "Normals", "NormalsIndex", 3, mesh.polygonVertexIndex);
            // The model that owns this geometry gives its global transform.
            for (long[] p : childToParent.getOrDefault(geo.longProp(0), List.of())) {
                FbxNode model = byId.get(p[0]);
                if (model != null && model.name.equals("Model")) {
                    mesh.meshGlobal = scene.modelGlobal(model, byId, childToParent, modelToBone, restGlobal);
                    break;
                }
            }
            int cps = mesh.controlPoints.length / 3;
            mesh.boneIndex = new int[cps][];
            mesh.boneWeight = new float[cps][];
            // skin deformers attached to the geometry
            for (Long deformerId : parentToChildren.getOrDefault(geo.longProp(0), List.of())) {
                FbxNode skin = byId.get(deformerId);
                if (skin == null || !skin.name.equals("Deformer") || !"Skin".equals(skin.stringProp(2))) continue;
                for (Long clusterId : parentToChildren.getOrDefault(deformerId, List.of())) {
                    FbxNode cluster = byId.get(clusterId);
                    if (cluster == null || !"Cluster".equals(cluster.stringProp(2))) continue;
                    Integer boneIdx = null;
                    for (Long boneModel : parentToChildren.getOrDefault(clusterId, List.of())) {
                        boneIdx = modelToBone.get(boneModel);
                        if (boneIdx != null) break;
                    }
                    if (boneIdx == null) continue;
                    double[] link = cluster.doubles("TransformLink");
                    if (link != null && link.length == 16) scene.bones.get(boneIdx).bindGlobal = new Matrix4d().set(link);
                    double[] tr = cluster.doubles("Transform");
                    if (tr != null && tr.length == 16 && link != null) {
                        // Transform = inverse(TransformLink) * meshGlobal at bind time, so meshGlobal = link * transform.
                        mesh.meshGlobal = new Matrix4d().set(link).mul(new Matrix4d().set(tr));
                    }
                    int[] idx = cluster.ints("Indexes");
                    double[] w = cluster.doubles("Weights");
                    if (idx == null || w == null) continue;
                    for (int k = 0; k < idx.length && k < w.length; k++) addInfluence(mesh, idx[k], boneIdx, (float) w[k]);
                }
            }
            scene.meshes.add(mesh);
        }

        // ---- animation
        for (FbxNode stack : objects.all("AnimationStack")) {
            Clip clip = new Clip(stack.stringProp(1));
            double start = stack.p70Num("LocalStart", 0) / KTIME;
            double stop = stack.p70Num("LocalStop", 0) / KTIME;
            for (Long layerId : parentToChildren.getOrDefault(stack.longProp(0), List.of())) {
                FbxNode layer = byId.get(layerId);
                if (layer == null || !layer.name.equals("AnimationLayer")) continue;
                for (Long cnId : parentToChildren.getOrDefault(layerId, List.of())) {
                    FbxNode cn = byId.get(cnId);
                    if (cn == null || !cn.name.equals("AnimationCurveNode")) continue;
                    Map<Long, String> targets = opProps.getOrDefault(cnId, Map.of());
                    for (Map.Entry<Long, String> target : targets.entrySet()) {
                        Integer boneIdx = modelToBone.get(target.getKey());
                        if (boneIdx == null) continue;
                        int kind = switch (target.getValue()) {
                            case "Lcl Translation" -> 0;
                            case "Lcl Rotation" -> 1;
                            case "Lcl Scaling" -> 2;
                            default -> -1;
                        };
                        if (kind < 0) continue;
                        Channel ch = clip.channels.computeIfAbsent(scene.bones.get(boneIdx).name, k -> new Channel());
                        ch.defaults[kind] = new double[] { cn.p70Num("d|X", 0), cn.p70Num("d|Y", 0), cn.p70Num("d|Z", 0) };
                        for (Long curveId : parentToChildren.getOrDefault(cnId, List.of())) {
                            FbxNode curve = byId.get(curveId);
                            if (curve == null || !curve.name.equals("AnimationCurve")) continue;
                            String axis = opProps.getOrDefault(curveId, Map.of()).get(cnId);
                            int a = "d|X".equals(axis) ? 0 : "d|Y".equals(axis) ? 1 : "d|Z".equals(axis) ? 2 : -1;
                            if (a < 0) continue;
                            long[] kt = curve.longs("KeyTime");
                            double[] kv = curve.doubles("KeyValueFloat");
                            if (kt == null || kv == null) continue;
                            int n = Math.min(kt.length, kv.length);
                            double[] times = new double[n];
                            float[] vals = new float[n];
                            for (int k = 0; k < n; k++) {
                                times[k] = kt[k] / KTIME - start;
                                vals[k] = (float) kv[k];
                                clip.duration = Math.max(clip.duration, times[k]);
                            }
                            ch.curves[kind][a] = new Curve(times, vals);
                        }
                    }
                }
            }
            if (stop > start) clip.duration = Math.max(clip.duration, stop - start);
            if (!clip.channels.isEmpty()) scene.clips.add(clip);
        }
        return scene;
    }

    private static void addInfluence(Mesh mesh, int cp, int bone, float weight) {
        if (cp < 0 || cp >= mesh.boneIndex.length || weight <= 0) return;
        int[] bi = mesh.boneIndex[cp];
        float[] bw = mesh.boneWeight[cp];
        if (bi == null) {
            mesh.boneIndex[cp] = new int[] { bone };
            mesh.boneWeight[cp] = new float[] { weight };
            return;
        }
        if (bi.length < 4) {
            int[] nbi = java.util.Arrays.copyOf(bi, bi.length + 1);
            float[] nbw = java.util.Arrays.copyOf(bw, bw.length + 1);
            nbi[bi.length] = bone;
            nbw[bw.length] = weight;
            mesh.boneIndex[cp] = nbi;
            mesh.boneWeight[cp] = nbw;
            return;
        }
        // Replace the weakest influence if this one is stronger.
        int min = 0;
        for (int i = 1; i < 4; i++) if (bw[i] < bw[min]) min = i;
        if (weight > bw[min]) {
            bi[min] = bone;
            bw[min] = weight;
        }
    }

    /** De-indexes a layer element into per-polygon-vertex values. */
    private static double[] layer(FbxNode geo, String layerName, String dataName, String indexName, int comps, int[] pvi) {
        FbxNode le = geo.first(layerName);
        if (le == null) return null;
        double[] data = le.doubles(dataName);
        if (data == null) return null;
        int[] index = le.ints(indexName);
        String mapping = le.childString("MappingInformationType");
        String ref = le.childString("ReferenceInformationType");
        boolean indexed = ref != null && ref.startsWith("IndexToDirect") && index != null;
        boolean byControlPoint = "ByVertice".equals(mapping) || "ByVertex".equals(mapping) || "ByControlPoint".equals(mapping);
        double[] out = new double[pvi.length * comps];
        int polygon = 0;
        for (int i = 0; i < pvi.length; i++) {
            int cp = pvi[i] < 0 ? ~pvi[i] : pvi[i];
            int src;
            if (byControlPoint) src = indexed ? index[cp] : cp;
            else if ("ByPolygon".equals(mapping)) src = indexed ? index[polygon] : polygon;
            else src = indexed ? index[i] : i; // ByPolygonVertex
            if (pvi[i] < 0) polygon++;
            if (src < 0 || (src + 1) * comps > data.length) continue;
            System.arraycopy(data, src * comps, out, i * comps, comps);
        }
        return out;
    }

    private Matrix4d modelGlobal(FbxNode model, Map<Long, FbxNode> byId, Map<Long, List<long[]>> c2p, Map<Long, Integer> modelToBone, Matrix4d[] restGlobal) {
        Bone tmp = new Bone("", 0);
        tmp.t = model.p70Vec("Lcl Translation", new double[] { 0, 0, 0 });
        tmp.r = model.p70Vec("Lcl Rotation", new double[] { 0, 0, 0 });
        tmp.s = model.p70Vec("Lcl Scaling", new double[] { 1, 1, 1 });
        tmp.preRotation = model.p70Vec("PreRotation", new double[] { 0, 0, 0 });
        tmp.postRotation = model.p70Vec("PostRotation", new double[] { 0, 0, 0 });
        tmp.rotationOrder = (int) model.p70Num("RotationOrder", 0);
        Matrix4d local = tmp.restLocal();
        for (long[] p : c2p.getOrDefault(model.longProp(0), List.of())) {
            Integer bi = modelToBone.get(p[0]);
            if (bi != null) return new Matrix4d(restGlobal[bi]).mul(local);
        }
        return local;
    }

    private void sortBonesParentFirst(Map<Long, Integer> modelToBone) {
        List<Bone> sorted = new ArrayList<>();
        boolean[] placed = new boolean[bones.size()];
        Map<Integer, Integer> remap = new LinkedHashMap<>();
        while (sorted.size() < bones.size()) {
            int before = sorted.size();
            for (int i = 0; i < bones.size(); i++) {
                Bone b = bones.get(i);
                if (placed[i]) continue;
                if (b.parent < 0 || placed[b.parent]) {
                    placed[i] = true;
                    remap.put(i, sorted.size());
                    sorted.add(b);
                }
            }
            if (sorted.size() == before) { // cycle: break it
                for (int i = 0; i < bones.size(); i++) if (!placed[i]) { bones.get(i).parent = -1; break; }
            }
        }
        for (Bone b : sorted) if (b.parent >= 0) b.parent = remap.get(b.parent);
        bones.clear();
        bones.addAll(sorted);
        boneIndex.clear();
        for (int i = 0; i < bones.size(); i++) boneIndex.putIfAbsent(bones.get(i).name, i);
        for (Map.Entry<Long, Integer> e : modelToBone.entrySet()) e.setValue(remap.get(e.getValue()));
    }

    public Matrix4d[] restGlobals() {
        Matrix4d[] g = new Matrix4d[bones.size()];
        for (int i = 0; i < bones.size(); i++) {
            Bone b = bones.get(i);
            Matrix4d local = b.restLocal();
            g[i] = b.parent >= 0 ? new Matrix4d(g[b.parent]).mul(local) : local;
        }
        return g;
    }

    /** Euler (degrees) to matrix for an FBX rotation order (0=XYZ ... 5=ZYX, 6=spheric treated as XYZ). */
    public static Matrix4d euler(double[] deg, int order) {
        double x = Math.toRadians(deg[0]), y = Math.toRadians(deg[1]), z = Math.toRadians(deg[2]);
        Matrix4d m = new Matrix4d();
        // FBX applies the first-named axis first, so the matrix is the reverse product.
        switch (order) {
            case 1 -> m.rotateY(y).rotateZ(z).rotateX(x); // XZY
            case 2 -> m.rotateX(x).rotateZ(z).rotateY(y); // YZX
            case 3 -> m.rotateZ(z).rotateX(x).rotateY(y); // YXZ
            case 4 -> m.rotateY(y).rotateX(x).rotateZ(z); // ZXY
            case 5 -> m.rotateX(x).rotateY(y).rotateZ(z); // ZYX
            default -> m.rotateZ(z).rotateY(y).rotateX(x); // XYZ
        }
        return m;
    }

    public static Vector3d transform(Matrix4d m, double x, double y, double z) {
        return m.transformPosition(new Vector3d(x, y, z));
    }
}

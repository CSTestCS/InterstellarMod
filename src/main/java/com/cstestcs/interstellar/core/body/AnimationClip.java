package com.cstestcs.interstellar.core.body;

import com.cstestcs.interstellar.core.fbx.FbxScene;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * One animation clip (usually a raw Mixamo FBX) prepared for retargeting.
 * <p>
 * Retargeting is done with world-space rotation deltas: for each bone the clip's rotation relative to its own bind
 * pose is applied to the target bone's bind rotation. That makes the result independent of how either skeleton
 * orients its bone axes, so any T-posed humanoid rig accepts any Mixamo clip.
 */
public final class AnimationClip {
    public final String name;
    public final AnimState state;
    public final double duration;
    private final FbxScene.Bone[] bones;
    private final FbxScene.Channel[] channels;
    private final Quaterniond[] invBindRotation;
    private final int hips;
    private final Vector3d hipsBind;
    private final Map<String, Integer> index = new HashMap<>();

    private AnimationClip(String name, AnimState state, FbxScene scene, FbxScene.Clip clip) {
        this.name = name;
        this.state = state;
        this.duration = Math.max(clip.duration, 1e-3);
        this.bones = scene.bones.toArray(new FbxScene.Bone[0]);
        this.channels = new FbxScene.Channel[bones.length];
        this.invBindRotation = new Quaterniond[bones.length];
        Matrix4d[] rest = new Matrix4d[bones.length];
        for (int i = 0; i < bones.length; i++) rest[i] = bones[i].bindGlobal;
        for (int i = 0; i < bones.length; i++) {
            channels[i] = clip.channels.get(bones[i].name);
            invBindRotation[i] = rest[i].getNormalizedRotation(new Quaterniond()).invert();
            index.putIfAbsent(bones[i].name, i);
        }
        Integer h = index.get("Hips");
        this.hips = h == null ? -1 : h;
        this.hipsBind = hips >= 0 ? rest[hips].getTranslation(new Vector3d()) : new Vector3d();
    }

    /** Builds a clip from a parsed animation FBX (the first animation stack is used). */
    public static AnimationClip fromScene(String name, AnimState state, FbxScene scene) {
        if (scene.clips.isEmpty() || scene.bones.isEmpty()) return null;
        return new AnimationClip(name, state, scene, scene.clips.get(0));
    }

    public double wrap(double time) {
        if (!state.loops) return Math.min(time, duration);
        double t = time % duration;
        return t < 0 ? t + duration : t;
    }

    /** Result of sampling: per clip bone world rotation delta and the hips offset from bind (clip units). */
    public final class Sample {
        public final Quaterniond[] delta = new Quaterniond[bones.length];
        public final Vector3d hipsOffset = new Vector3d();

        public Quaterniond deltaFor(String bone) {
            Integer i = index.get(bone);
            return i == null || channels[i] == null && !animatedAncestor(i) ? null : delta[i];
        }

        public double hipsBindY() {
            return hipsBind.y;
        }
    }

    private boolean animatedAncestor(int i) {
        for (int p = bones[i].parent; p >= 0; p = bones[p].parent) if (channels[p] != null) return true;
        return false;
    }

    public Sample sample(double time) {
        double t = wrap(time);
        Sample s = new Sample();
        Matrix4d[] g = new Matrix4d[bones.length];
        for (int i = 0; i < bones.length; i++) {
            FbxScene.Bone b = bones[i];
            FbxScene.Channel ch = channels[i];
            Matrix4d local = b.local(sampleKind(ch, 0, t, b.t), sampleKind(ch, 1, t, b.r), sampleKind(ch, 2, t, b.s));
            g[i] = b.parent >= 0 ? new Matrix4d(g[b.parent]).mul(local) : local;
            s.delta[i] = g[i].getNormalizedRotation(new Quaterniond()).mul(invBindRotation[i]);
        }
        if (hips >= 0) g[hips].getTranslation(s.hipsOffset).sub(hipsBind);
        return s;
    }

    private static double[] sampleKind(FbxScene.Channel ch, int kind, double t, double[] def) {
        if (ch == null) return def;
        double[] out = new double[3];
        for (int a = 0; a < 3; a++) {
            FbxScene.Curve c = ch.curves[kind][a];
            if (c != null) out[a] = c.sample(t);
            else if (ch.defaults[kind] != null) out[a] = ch.defaults[kind][a];
            else out[a] = def[a];
        }
        return out;
    }
}

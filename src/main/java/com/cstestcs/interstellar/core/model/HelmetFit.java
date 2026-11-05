package com.cstestcs.interstellar.core.model;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Fits worn helmets to any head model. A vanilla helmet is modelled around the standard 8x8x8 head (x -4..4,
 * y -8..0, z -4..4 around the neck pivot). For a race head the helmet is moved, turned and stretched onto the
 * head's skull instead: a part named "skull" if there is one (its biggest cube), otherwise the biggest cube of the
 * whole head. The helmet follows that cube's part rotations, so it sits on a tilted skull at the same tilt.
 * <p>
 * Matrices work in model units (1 = one block = 16 pixels), as the render pose stack does, starting in the space
 * of the part named "head" with its offset ignored (head models are drawn with their head pivot on the mob's).
 */
public final class HelmetFit {
    private HelmetFit() {}

    private record Found(ModelDef.CubeDef cube, Matrix4f partToHead, float volume) {}

    /** @return the transform that carries a vanilla helmet onto the skull, or null for a model without cubes */
    public static Matrix4f compute(ModelDef def) {
        ModelDef.PartDef head = def.root.find("head");
        Found best = null;
        if (head != null) {
            ModelDef.PartDef skull = head.find("skull");
            Matrix4f base = new Matrix4f().rotate(rotation(head)).scale(head.scale);
            if (skull != null && skull != head) best = biggest(skull, chainTo(head, skull, base), null, false);
            if (best == null) best = biggest(head, base, null, true);
        } else {
            for (ModelDef.PartDef p : def.root.children.values()) best = biggest(p, local(p), best, true);
        }
        if (best == null) return null;
        ModelDef.CubeDef c = best.cube;
        return new Matrix4f(best.partToHead)
                .translate(c.x() / 16f, c.y() / 16f, c.z() / 16f)
                .scale(c.w() / 8f, c.h() / 8f, c.d() / 8f)
                .translate(4 / 16f, 8 / 16f, 4 / 16f);
    }

    /** The biggest cube of a part (and, with {@code deep}, of its children), with the transform of its part. */
    private static Found biggest(ModelDef.PartDef part, Matrix4f toHead, Found best, boolean deep) {
        for (ModelDef.CubeDef c : part.cubes) {
            float v = c.w() * c.h() * c.d();
            if (best == null || v > best.volume) best = new Found(c, new Matrix4f(toHead), v);
        }
        if (deep) {
            for (ModelDef.PartDef child : part.children.values()) best = biggest(child, new Matrix4f(toHead).mul(local(child)), best, true);
        }
        return best;
    }

    /** Transform from {@code target}'s space to {@code from}'s, found by walking down the part tree. */
    private static Matrix4f chainTo(ModelDef.PartDef from, ModelDef.PartDef target, Matrix4f base) {
        if (from == target) return base;
        for (ModelDef.PartDef child : from.children.values()) {
            Matrix4f m = chainTo(child, target, new Matrix4f(base).mul(local(child)));
            if (m != null) return m;
        }
        return null;
    }

    /** What ModelPart.translateAndRotate applies for a part: offset, then rotation (Z, Y, X), then scale. */
    static Matrix4f local(ModelDef.PartDef p) {
        return new Matrix4f().translate(p.x / 16f, p.y / 16f, p.z / 16f).rotate(rotation(p)).scale(p.scale);
    }

    private static Quaternionf rotation(ModelDef.PartDef p) {
        return new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot);
    }
}

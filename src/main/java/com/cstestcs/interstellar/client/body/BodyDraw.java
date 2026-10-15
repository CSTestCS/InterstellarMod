package com.cstestcs.interstellar.client.body;

import com.cstestcs.interstellar.core.body.BodyModel;
import com.cstestcs.interstellar.core.body.HumanoidRig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Drawing of custom bodies: the skinned mesh, heads on the head bone, and biped layers bent onto the body. */
public final class BodyDraw {
    private BodyDraw() {}

    /** Emits the CPU-skinned mesh as quads. Positions are MC model pixels, converted to blocks here. */
    public static void mesh(PoseStack.Pose pose, VertexConsumer vc, BodyInstances.Instance inst, int light, int overlay, int color) {
        BodyModel body = inst.body;
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        Vector3f p = new Vector3f(), q = new Vector3f();
        float[] pos = inst.pos, nrm = inst.nrm, uv = body.uv;
        for (int v = 0; v < body.vertexCount; v++) {
            m.transformPosition(pos[v * 3] / 16f, pos[v * 3 + 1] / 16f, pos[v * 3 + 2] / 16f, p);
            n.transform(nrm[v * 3], nrm[v * 3 + 1], nrm[v * 3 + 2], q).normalize();
            vc.addVertex(p.x, p.y, p.z, color, uv[v * 2], uv[v * 2 + 1], overlay, light, q.x, q.y, q.z);
        }
    }

    /** Renders a part (normally the vanilla head) at the body's head bone, ignoring the part's own pose. */
    public static void onHeadBone(PoseStack poseStack, ModelPart part, BodyInstances.Instance inst, VertexConsumer vc, int light, int overlay, int color) {
        poseStack.pushPose();
        poseStack.mulPose(inst.headTransform());
        PartPose saved = part.storePose();
        part.loadPose(PartPose.ZERO);
        part.render(poseStack, vc, light, overlay, color);
        part.loadPose(saved);
        poseStack.popPose();
    }

    /**
     * Draws any {@link HumanoidModel} (armour, clothing overlays, modded armour models...) bent onto the body. Each
     * top-level part is rendered in its own local frame through a vertex wrapper that maps every vertex onto the
     * matching limb chain and skins it with the body's bones, so plates stretch to the limb's thickness and bend at
     * elbows and knees with the animation.
     */
    public static void deformed(HumanoidModel<?> model, BodyInstances.Instance inst, PoseStack poseStack, VertexConsumer vc, int light, int overlay, int color) {
        PoseStack local = new PoseStack();
        PoseStack.Pose world = poseStack.last();
        draw(model.head, HumanoidRig.Limb.HEAD, inst, local, world, vc, light, overlay, color);
        draw(model.body, HumanoidRig.Limb.BODY, inst, local, world, vc, light, overlay, color);
        draw(model.rightArm, HumanoidRig.Limb.RIGHT_ARM, inst, local, world, vc, light, overlay, color);
        draw(model.leftArm, HumanoidRig.Limb.LEFT_ARM, inst, local, world, vc, light, overlay, color);
        draw(model.rightLeg, HumanoidRig.Limb.RIGHT_LEG, inst, local, world, vc, light, overlay, color);
        draw(model.leftLeg, HumanoidRig.Limb.LEFT_LEG, inst, local, world, vc, light, overlay, color);
    }

    private static void draw(ModelPart part, HumanoidRig.Limb limb, BodyInstances.Instance inst, PoseStack local, PoseStack.Pose world,
            VertexConsumer vc, int light, int overlay, int color) {
        if (!part.visible) return;
        PartPose saved = part.storePose();
        part.loadPose(PartPose.ZERO);
        part.render(local, new Deformer(vc, limb, inst, world), light, overlay, color);
        part.loadPose(saved);
    }

    /** Maps part-local vertices (blocks, pivot-relative) onto the skinned body. */
    private static final class Deformer extends VertexConsumerWrapper {
        private final HumanoidRig.Limb limb;
        private final BodyInstances.Instance inst;
        private final PoseStack.Pose world;
        private final HumanoidRig.Mapped mapped = new HumanoidRig.Mapped();
        private final Matrix3f blendRot = new Matrix3f();
        private final Vector3f tmp = new Vector3f(), acc = new Vector3f();

        Deformer(VertexConsumer parent, HumanoidRig.Limb limb, BodyInstances.Instance inst, PoseStack.Pose world) {
            super(parent);
            this.limb = limb;
            this.inst = inst;
            this.world = world;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inst.body.rig.map(limb, x * 16, y * 16, z * 16, false, mapped);
            acc.zero();
            blendRot.zero();
            float total = 0;
            for (int k = 0; k < 3; k++) {
                float w = mapped.weight[k];
                if (w <= 0) continue;
                Matrix4f skin = inst.skin[mapped.bone[k]];
                skin.transformPosition((float) mapped.pos.x, (float) mapped.pos.y, (float) mapped.pos.z, tmp);
                acc.fma(w, tmp);
                Matrix3f r = skin.get3x3(new Matrix3f());
                blendRot.add(r.scale(w));
                total += w;
            }
            if (total <= 0) {
                acc.set((float) mapped.pos.x, (float) mapped.pos.y, (float) mapped.pos.z);
                blendRot.identity();
            }
            else if (total != 1f) acc.div(total);
            world.pose().transformPosition(acc.x / 16f, acc.y / 16f, acc.z / 16f, tmp);
            parent.addVertex(tmp.x, tmp.y, tmp.z);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            Vector3f nrm = blendRot.transform(x, y, z, new Vector3f());
            world.normal().transform(nrm).normalize();
            parent.setNormal(nrm.x, nrm.y, nrm.z);
            return this;
        }
    }
}

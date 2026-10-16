package com.cstestcs.interstellar.client.body;

import com.cstestcs.interstellar.core.body.AnimState;
import com.cstestcs.interstellar.core.body.AnimationClip;
import com.cstestcs.interstellar.core.body.BodyModel;
import com.cstestcs.interstellar.core.body.PoseSolver;
import com.cstestcs.interstellar.gun.GunItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Per-entity animation state for custom bodies. Updated once per frame while the entity's render state is
 * extracted; the result (skin matrices and CPU-skinned vertices) is read by the draw code in the same frame.
 */
public final class BodyInstances {
    private static final Int2ObjectOpenHashMap<Instance> INSTANCES = new Int2ObjectOpenHashMap<>();
    private static @Nullable HumanoidModel<HumanoidRenderState> probe;
    private static int frame;

    private BodyInstances() {}

    public static final class Instance {
        public final BodyModel body;
        public final Matrix4d[] globals;
        public final Matrix4f[] skin;
        public final float[] pos, nrm;
        AnimState state = AnimState.IDLE;
        float stateStart;
        @Nullable AnimationClip clip, previous;
        float previousTime, blendStart;
        int lastFrame;

        Instance(BodyModel body) {
            this.body = body;
            globals = new Matrix4d[body.boneCount()];
            skin = new Matrix4f[body.boneCount()];
            for (int i = 0; i < globals.length; i++) {
                globals[i] = new Matrix4d();
                skin[i] = new Matrix4f();
            }
            pos = new float[body.pos.length];
            nrm = new float[body.nrm.length];
        }

        /** Block-space transform that places vanilla head geometry (head-pivot relative) on the head bone. */
        public Matrix4f headTransform() {
            int head = body.rig.head;
            if (head < 0) return new Matrix4f();
            var bind = body.bindPositionMc(head);
            return new Matrix4f().scale(1 / 16f).mul(skin[head]).translate((float) bind.x, (float) bind.y, (float) bind.z).scale(16f);
        }

        /**
         * Block-space transform that places vanilla arm geometry (shoulder-pivot relative, arm hanging down) so that
         * its hand, 10 pixels down the arm, sits in the body's hand: held items follow the body's own arms.
         */
        public Matrix4f handTransform(boolean right) {
            var chain = body.rig.chain(right ? com.cstestcs.interstellar.core.body.HumanoidRig.Limb.RIGHT_ARM
                    : com.cstestcs.interstellar.core.body.HumanoidRig.Limb.LEFT_ARM);
            int bone = chain.c >= 0 ? chain.c : chain.b >= 0 ? chain.b : chain.a;
            if (bone < 0 || !chain.valid) return null;
            var q = chain.qb;
            return new Matrix4f().scale(1 / 16f).mul(skin[bone]).translate((float) chain.j2.x, (float) chain.j2.y, (float) chain.j2.z)
                    .rotate(new org.joml.Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w)).translate(0, -10, 0).scale(16f);
        }

        /** Block-space transform for things attached to the lower back (tails), in vanilla body-part coordinates. */
        public Matrix4f bodyTransform() {
            int bone = body.rig.spine >= 0 ? body.rig.spine : body.rig.hips;
            if (bone < 0) return new Matrix4f();
            var neck = body.rig.chain(com.cstestcs.interstellar.core.body.HumanoidRig.Limb.BODY).j0;
            return new Matrix4f().scale(1 / 16f).mul(skin[bone]).translate((float) neck.x, (float) neck.y, (float) neck.z).scale(16f);
        }
    }

    public static void clear() {
        INSTANCES.clear();
    }

    /** Called once per rendered frame to drop instances of entities no longer drawn. */
    public static void endFrame() {
        frame++;
        if ((frame & 63) == 0) INSTANCES.values().removeIf(i -> frame - i.lastFrame > 120);
    }

    public static @Nullable Instance update(LivingEntity entity, LivingEntityRenderState state, BodyModel body) {
        Instance inst = INSTANCES.get(entity.getId());
        if (inst == null || inst.body != body) {
            inst = new Instance(body);
            INSTANCES.put(entity.getId(), inst);
        }
        inst.lastFrame = frame;
        float age = state.ageInTicks;

        // ---- clip selection
        AnimState wanted = pickState(entity, state);
        if (wanted != inst.state) {
            inst.previous = inst.clip;
            inst.previousTime = (age - inst.stateStart) / 20f;
            inst.blendStart = age;
            inst.state = wanted;
            inst.stateStart = age;
            inst.clip = null;
        }
        List<AnimationClip> clips = BodyLibrary.clips(body.meta.name(), wanted);
        if (inst.clip == null && !clips.isEmpty()) inst.clip = clips.get(Math.floorMod(entity.getUUID().hashCode(), clips.size()));
        float blend = Mth.clamp((age - inst.blendStart) / 5f, 0f, 1f);

        // ---- vanilla angles (used directly without clips, and for arms while attacking or aiming)
        PoseSolver.Input in = vanillaInput(state);
        boolean armsBusy = state instanceof HumanoidRenderState hs && (entity.isSwinging() || hs.isUsingItem);
        boolean gun = entity.getMainHandItem().getItem() instanceof GunItem;
        in.vanillaRightArm = armsBusy || gun;
        in.vanillaLeftArm = armsBusy || gun;
        in.vanillaLegs = entity.isPassenger() && inst.clip == null;

        AnimationClip.Sample a = inst.clip == null ? null : inst.clip.sample((age - inst.stateStart) / 20f);
        AnimationClip.Sample b = inst.previous == null || blend >= 1f ? null : inst.previous.sample(inst.previousTime);
        if (a == null && b != null) {
            a = b;
            b = null;
        }
        PoseSolver.solve(body, a, b, blend, in, inst.globals);
        body.skinMatrices(inst.globals, inst.skin);
        body.skin(inst.skin, inst.pos, inst.nrm);
        return inst;
    }

    static AnimState pickState(LivingEntity e, LivingEntityRenderState s) {
        if (e.deathTime > 0) return AnimState.DEATH;
        if (e.isPassenger()) return e.getVehicle() instanceof com.cstestcs.interstellar.ship.SpacecraftEntity ship && ship.pilot() == e ? AnimState.IDLE : AnimState.SIT;
        if (e.isFallFlying()) return AnimState.FLY;
        if (e.isVisuallySwimming() || e.isInWater() && !e.onGround()) return AnimState.SWIM;
        if (e.onClimbable() && !e.onGround()) return AnimState.CLIMB;
        if (!e.onGround()) {
            double vy = e.getDeltaMovement().y;
            if (vy > 0.05) return AnimState.JUMP;
            if (vy < -0.35) return AnimState.FALL;
        }
        float speed = s.walkAnimationSpeed;
        if (e.isCrouching()) return speed > 0.1f ? AnimState.SNEAK_WALK : AnimState.SNEAK_IDLE;
        if (e.getMainHandItem().getItem() instanceof GunItem && e.isUsingItem()) return AnimState.SHOOT;
        if (e.isSprinting() || speed > 0.75f) return AnimState.RUN;
        if (speed > 0.08f) return AnimState.WALK;
        return AnimState.IDLE;
    }

    private static PoseSolver.Input vanillaInput(LivingEntityRenderState state) {
        PoseSolver.Input in = new PoseSolver.Input();
        in.headYaw = state.yRot * Mth.DEG_TO_RAD;
        in.headPitch = state.xRot * Mth.DEG_TO_RAD;
        if (state instanceof HumanoidRenderState hs) {
            HumanoidModel<HumanoidRenderState> m = probe();
            m.setupAnim(hs);
            copy(m.rightArm, in.rightArm);
            copy(m.leftArm, in.leftArm);
            copy(m.rightLeg, in.rightLeg);
            copy(m.leftLeg, in.leftLeg);
            in.bodyXRot = m.body.xRot;
            in.bodyYRot = m.body.yRot;
        } else {
            // Non-humanoid models (villagers, illagers...): a plain walk cycle.
            float pos = state.walkAnimationPos, speed = Math.min(1f, state.walkAnimationSpeed);
            float swing = Mth.cos(pos * 0.6662f) * 1.4f * speed * 0.5f;
            in.rightLeg[0] = swing;
            in.leftLeg[0] = -swing;
            in.rightArm[0] = -swing * 0.8f;
            in.leftArm[0] = swing * 0.8f;
        }
        return in;
    }

    private static void copy(net.minecraft.client.model.geom.ModelPart part, float[] out) {
        out[0] = part.xRot;
        out[1] = part.yRot;
        out[2] = part.zRot;
    }

    private static HumanoidModel<HumanoidRenderState> probe() {
        if (probe == null) probe = new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64).bakeRoot());
        return probe;
    }
}

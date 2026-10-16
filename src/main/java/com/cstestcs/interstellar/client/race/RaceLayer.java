package com.cstestcs.interstellar.client.race;

import com.cstestcs.interstellar.client.body.BodyDraw;
import com.cstestcs.interstellar.client.render.RenderInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * Draws everything race-specific on top of (or instead of) a mob's own model: the race biped or custom body with
 * the race skin, the race head model with its head skin, the tail, and villager clothes moved onto the biped.
 * Added to every living entity renderer; it does nothing for entities without race render info.
 */
public class RaceLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    /** [slim arms][headless]: separate instances because drawing is deferred, so visibility cannot be toggled per draw. */
    private static RaceModels.RaceBiped[][] bipeds;
    private static RaceModels.VillagerOutfit outfit;

    public RaceLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    private static RaceModels.RaceBiped biped(boolean slimArms, boolean headless) {
        if (bipeds == null) {
            bipeds = new RaceModels.RaceBiped[2][2];
            for (int s = 0; s < 2; s++) {
                for (int h = 0; h < 2; h++) {
                    bipeds[s][h] = RaceModels.biped(s == 1);
                    bipeds[s][h].head.visible = h == 0;
                }
            }
            outfit = new RaceModels.VillagerOutfit(RaceModels.VillagerOutfit.create().bakeRoot());
        }
        return bipeds[slimArms ? 1 : 0][headless ? 1 : 0];
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        RenderInfo info = state.getRenderData(RenderInfo.KEY);
        if (info == null) return;
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0f);
        if (info.drawsConvertedBody() && info.bodySkin != null) submitConvertedBody(poseStack, collector, light, overlay, info);
        if (info.race == null) return;
        ClientRaces.Race race = info.race;
        RaceModels.RaceBiped biped = biped(race.def.slimArms(), info.hasRaceHead());
        HumanoidRenderState hs = humanoid(state);
        boolean bodyDrawn = info.body != null && info.replacesBody();

        // ---- body
        if (info.replacesBody()) {
            Identifier skin = race.skin(info.skinVariant);
            if (bodyDrawn) {
                var inst = info.body;
                collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(skin), (pose, vc) -> BodyDraw.mesh(pose, vc, inst, light, overlay, -1));
                if (!info.hasRaceHead()) {
                    poseStack.pushPose();
                    poseStack.mulPose(inst.headTransform());
                    collector.submitModel(new RaceModels.Part(biped.head), Unit.INSTANCE, poseStack, RenderTypes.entityCutout(skin), light, overlay, -1);
                    poseStack.popPose();
                }
            } else {
                collector.submitModel(biped, hs, poseStack, RenderTypes.entityCutout(skin), light, overlay, -1);
            }
            if (info.villagerLike && !info.outfit.isEmpty()) {
                for (Identifier tex : info.outfit) {
                    collector.submitModel(outfit, hs, poseStack, RenderTypes.entityCutout(tex), light, overlay, -1);
                }
            }
        }

        // ---- head model
        if (info.hasRaceHead()) {
            Identifier headSkin = race.headSkin(info.headVariant);
            if (headSkin != null) {
                poseStack.pushPose();
                if (info.body != null && (bodyDrawn || info.drawsBody())) {
                    poseStack.mulPose(info.body.headTransform());
                } else if (info.replacesBody()) {
                    biped.setupAnim(hs);
                    biped.root().translateAndRotate(poseStack);
                    biped.head.translateAndRotate(poseStack);
                } else if (getParentModel() instanceof HeadedModel headed) {
                    getParentModel().root().translateAndRotate(poseStack);
                    headed.getHead().translateAndRotate(poseStack);
                }
                float s = race.def.headScale();
                if (s != 1f) poseStack.scale(s, s, s);
                ModelPart head = race.head;
                collector.submitModel(new RaceModels.Part(head), Unit.INSTANCE, poseStack, RenderTypes.entityCutout(headSkin), light, overlay, -1);
                poseStack.popPose();
            }
        }

        // ---- tail (texture: the race skin, else the mob's own texture; its top-left 8x8 colours the tail)
        if (race.tail != null) {
            Identifier tex = race.skin(info.skinVariant);
            if (tex == null) tex = info.texture;
            if (tex != null) {
                poseStack.pushPose();
                if (info.body != null && (bodyDrawn || info.drawsBody())) {
                    poseStack.mulPose(info.body.bodyTransform());
                } else if (info.replacesBody()) {
                    biped.setupAnim(hs);
                    biped.root().translateAndRotate(poseStack);
                    biped.body.translateAndRotate(poseStack);
                } else {
                    getParentModel().root().translateAndRotate(poseStack);
                }
                collector.submitModel(new RaceModels.Tail(race.tail), new RaceModels.Tail.State(info.ageInTicks, info.walkSpeed), poseStack,
                        RenderTypes.entityCutout(tex), light, overlay, -1);
                poseStack.popPose();
            }
        }
    }

    /**
     * A villager/illager/skeleton with a custom body: the body mesh wears the converted skin, the biped head (with
     * hat layer) sits on the head bone, plus the villager nose.
     */
    private void submitConvertedBody(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, RenderInfo info) {
        Identifier skin = info.bodySkin;
        var inst = info.body;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(skin), (pose, vc) -> BodyDraw.mesh(pose, vc, inst, light, overlay, -1));
        if (info.hasRaceHead()) return; // the race head is drawn below
        RaceModels.RaceBiped biped = biped(false, false);
        poseStack.pushPose();
        poseStack.mulPose(inst.headTransform());
        collector.submitModel(new RaceModels.Part(biped.head), Unit.INSTANCE, poseStack, RenderTypes.entityCutout(skin), light, overlay, -1);
        if (info.convertLayout != com.cstestcs.interstellar.core.image.SkinConverter.Layout.THIN_BIPED) {
            collector.submitModel(new RaceModels.Part(RaceModels.nose()), Unit.INSTANCE, poseStack, RenderTypes.entityCutout(skin), light, overlay, -1);
        }
        poseStack.popPose();
    }

    /** Humanoid render states are used as they are; others (villagers) get the basics copied into a scratch state. */
    private static HumanoidRenderState humanoid(LivingEntityRenderState state) {
        if (state instanceof HumanoidRenderState hs) return hs;
        HumanoidRenderState copy = new HumanoidRenderState();
        copy.ageInTicks = state.ageInTicks;
        copy.walkAnimationPos = state.walkAnimationPos;
        copy.walkAnimationSpeed = state.walkAnimationSpeed;
        copy.yRot = state.yRot;
        copy.xRot = state.xRot;
        copy.isBaby = state.isBaby;
        return copy;
    }
}

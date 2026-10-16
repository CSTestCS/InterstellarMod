package com.cstestcs.interstellar.client.render;

import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.client.body.BodyDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Called (through {@code ModelMixin}) whenever any model is about to draw itself with an entity render state.
 * Decides, per model, whether vanilla drawing proceeds:
 * <ul>
 *     <li>the entity's own model: hidden when the race replaces the body (the race layer draws instead), replaced by
 *     the skinned custom body (with the vanilla head on the head bone), or drawn without its head when the race
 *     has a head model;</li>
 *     <li>any other humanoid model drawn with the same state (armour, overlays, modded armour): bent onto the
 *     custom body.</li>
 * </ul>
 */
public final class BodyRenderHook {
    private BodyRenderHook() {}

    /** @return true if the hook drew (or deliberately skipped) the model and vanilla drawing must be cancelled. */
    public static boolean render(Model<?> model, Object state, PoseStack poseStack, VertexConsumer vc, int light, int overlay, int color) {
        if (!(state instanceof EntityRenderState es)) return false;
        RenderInfo info = es.getRenderData(RenderInfo.KEY);
        if (info == null) return false;
        // Converted bodies (villagers, illagers, skeletons): the race layer draws the body with the converted skin;
        // the mob's own non-humanoid layers (villager clothes, crossed arms) are already part of that skin.
        if (info.drawsConvertedBody() && info.bodySkin != null && (model == info.mainModel || !(model instanceof HumanoidModel<?>))) return true;
        if (model == info.mainModel) {
            if (info.replacesBody()) return true; // the race layer draws the race biped/body with the race skin
            if (info.drawsBody()) {
                BodyDraw.mesh(poseStack.last(), vc, info.body, light, overlay, color);
                if (!info.hasRaceHead() && model instanceof HumanoidModel<?> h) BodyDraw.onHeadBone(poseStack, h.head, info.body, vc, light, overlay, color);
                return true;
            }
            if (info.hasRaceHead() && model instanceof HeadedModel headed) {
                ModelPart head = headed.getHead();
                boolean was = head.visible;
                head.visible = false;
                model.root().render(poseStack, vc, light, overlay, color);
                head.visible = was;
                return true;
            }
            return false;
        }
        if (model instanceof com.cstestcs.interstellar.client.race.RaceModels.RaceBiped) return false;
        if (info.drawsBody() && model instanceof HumanoidModel<?> h && InterstellarConfig.CLIENT.deformArmor.get()) {
            BodyDraw.deformed(h, info.body, poseStack, vc, light, overlay, color);
            return true;
        }
        return false;
    }
}

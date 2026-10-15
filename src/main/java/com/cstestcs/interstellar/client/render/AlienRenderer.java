package com.cstestcs.interstellar.client.render;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.alien.AlienEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Aliens are plain bipeds; their race (applied by the race layer) supplies the actual skin, head model and tail.
 * The default texture is only seen for aliens without a race.
 */
public class AlienRenderer extends HumanoidMobRenderer<AlienEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = Interstellar.id("textures/entity/alien.png");

    public AlienRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}

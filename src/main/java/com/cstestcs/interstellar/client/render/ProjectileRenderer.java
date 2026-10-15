package com.cstestcs.interstellar.client.render;

import com.cstestcs.interstellar.gun.GunProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** Glowing bolt: a small coloured cube with a brighter core, oriented along its flight direction. */
public class ProjectileRenderer extends EntityRenderer<GunProjectile, ProjectileRenderer.State> {
    public static class State extends EntityRenderState {
        int color;
        float size;
    }

    public ProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GunProjectile entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.color = entity.color();
        state.size = entity.size();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int c = state.color;
        float s = state.size;
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, vc) -> {
            cube(pose, vc, s, (c >> 16) & 255, (c >> 8) & 255, c & 255, 160);
            cube(pose, vc, s * 0.45f, 255, 255, 255, 230);
        });
        super.submit(state, poseStack, collector, camera);
    }

    private static void cube(PoseStack.Pose pose, VertexConsumer vc, float s, int r, int g, int b, int a) {
        float[][] faces = {
                { -s, -s, -s, s, -s, -s, s, s, -s, -s, s, -s }, { -s, -s, s, -s, s, s, s, s, s, s, -s, s },
                { -s, -s, -s, -s, s, -s, -s, s, s, -s, -s, s }, { s, -s, -s, s, -s, s, s, s, s, s, s, -s },
                { -s, -s, -s, -s, -s, s, s, -s, s, s, -s, -s }, { -s, s, -s, s, s, -s, s, s, s, -s, s, s } };
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) vc.addVertex(pose, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).setColor(r, g, b, a);
        }
    }
}

package com.cstestcs.interstellar.client.ship;

import com.cstestcs.interstellar.ship.ShipStructure;
import com.cstestcs.interstellar.ship.SpacecraftEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Draws a spacecraft's blocks with the regular block models (so any block, modded ones included, looks exactly as it
 * did when placed), rotated by the ship's yaw and pitch and scaled by its space scale. The same transform is used
 * by {@link SpacecraftEntity#toWorld}, so riders sit where the blocks are drawn.
 */
public class SpacecraftRenderer extends EntityRenderer<SpacecraftEntity, SpacecraftRenderer.State> {
    public static class State extends EntityRenderState {
        ShipStructure structure = ShipStructure.EMPTY;
        float yaw, pitch, scale = 1f;
        BlockPos origin = BlockPos.ZERO;
        @Nullable Level level;
    }

    public SpacecraftRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpacecraftEntity ship, State state, float partialTick) {
        super.extractRenderState(ship, state, partialTick);
        state.structure = ship.structure();
        state.yaw = Mth.rotLerp(partialTick, ship.yRotO, ship.getYRot());
        state.pitch = Mth.lerp(partialTick, ship.xRotO, ship.getXRot());
        state.scale = ship.shipScale();
        state.origin = ship.blockPosition();
        state.level = ship.level();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.level == null) return;
        poseStack.pushPose();
        // Matches SpacecraftEntity.toWorld (Vec3 yRot(base), xRot(-pitch), yRot(-yaw), scale), applied right to left.
        poseStack.scale(state.scale, state.scale, state.scale);
        poseStack.rotateDegrees(Axis.YP, -state.yaw);
        poseStack.rotateDegrees(Axis.XP, state.pitch); // Vec3.xRot(a) == JOML rotateX(-a)
        poseStack.rotateDegrees(Axis.YP, state.structure.baseYaw);
        ShipStructure structure = state.structure;
        if (!(structure.renderCache instanceof net.neoforged.neoforge.model.data.ModelData[] data) || data.length != structure.entries.size()) {
            // Plated blocks carry their plating in their block entity data: decoded once per structure.
            data = new net.neoforged.neoforge.model.data.ModelData[structure.entries.size()];
            for (int i = 0; i < data.length; i++) {
                net.minecraft.world.level.block.state.BlockState plating = com.cstestcs.interstellar.ship.plating.Plating.fromTag(structure.entries.get(i).blockEntity().orElse(null));
                data[i] = plating == null ? net.neoforged.neoforge.model.data.ModelData.EMPTY
                        : net.neoforged.neoforge.model.data.ModelData.of(com.cstestcs.interstellar.ship.plating.Plating.PROPERTY, plating);
            }
            structure.renderCache = data;
        }
        int index = 0;
        for (ShipStructure.Entry e : state.structure.entries) {
            net.neoforged.neoforge.model.data.ModelData modelData = data[index++];
            poseStack.pushPose();
            poseStack.translate(e.pos().getX() - 0.5, e.pos().getY(), e.pos().getZ() - 0.5);
            MovingBlockRenderState block = new MovingBlockRenderState();
            block.blockState = e.state();
            block.blockPos = state.origin.offset(e.pos());
            block.lightEngine = state.level.getLightEngine();
            block.biome = state.level.getBiome(block.blockPos);
            block.modelData = modelData;
            collector.submitMovingBlock(poseStack, block, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}

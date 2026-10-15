package com.cstestcs.interstellar.mixin;

import com.cstestcs.interstellar.client.render.BodyRenderHook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers the render state a model was last set up with, and lets {@link BodyRenderHook} take over drawing.
 * Models are always set up immediately before they are drawn (the feature renderer calls setupAnim then
 * renderToBuffer), so the captured state belongs to the draw that follows.
 */
@Mixin(Model.class)
public abstract class ModelMixin {
    @Unique
    private Object interstellar$state;

    @Inject(method = "setupAnim", at = @At("HEAD"))
    private void interstellar$captureState(Object state, CallbackInfo ci) {
        this.interstellar$state = state;
    }

    @Inject(method = "renderToBuffer", at = @At("HEAD"), cancellable = true)
    private void interstellar$render(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        Object state = this.interstellar$state;
        this.interstellar$state = null;
        if (state != null && BodyRenderHook.render((Model<?>) (Object) this, state, poseStack, buffer, light, overlay, color)) ci.cancel();
    }
}

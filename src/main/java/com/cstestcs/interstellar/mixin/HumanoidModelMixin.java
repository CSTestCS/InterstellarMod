package com.cstestcs.interstellar.mixin;

import com.cstestcs.interstellar.client.render.BodyRenderHook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Held items are placed with the mob's own (hidden) arm. When a race biped or a custom body is drawn instead, put
 * them in that model's hand.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void interstellar$translateToHand(HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        if (BodyRenderHook.translateToHand((HumanoidModel<?>) (Object) this, state, arm, poseStack)) ci.cancel();
    }
}

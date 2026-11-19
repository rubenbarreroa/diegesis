package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * Moves the camera to the station while the player works at it, and applies the scene's FOV.
 */
@Mixin(value = Camera.class, priority = 2000)
abstract class CameraMixin {
    private static final double BODY_CLEARANCE = 0.75;

    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void diegesis$focusCamera(float partialTick, CallbackInfo ci) {
        FocusController focus = FocusController.get();
        if (!focus.isActive()) {
            return;
        }
        Camera self = (Camera) (Object) this;
        boolean wasDetached = detached;
        focus.frame();
        Vec3 eye = self.position();
        FocusController.CameraPose pose = focus.cameraPose(eye, self.yRot(), self.xRot());
        if (pose == null) {
            return;
        }
        setRotation(pose.yaw(), pose.pitch());
        setPosition(pose.position());
        detached = !focus.options().hidePlayer() && (wasDetached || pose.position().distanceToSqr(eye) > BODY_CLEARANCE * BODY_CLEARANCE);
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void diegesis$focusFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        FocusController focus = FocusController.get();
        if (focus.isActive()) {
            cir.setReturnValue((float) focus.fov(cir.getReturnValue()));
        }
    }
}

package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;

/** Hides the block outline while focused at a station, and appends Diegesis scene geometry to level feature rendering. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    @Final
    private SubmitNodeStorage submitNodeStorage;

    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void diegesis$noOutlineAtStation(LevelRenderState state, SubmitNodeCollector collector, boolean outline, CallbackInfo ci) {
        if (FocusController.get().isActive()) {
            state.blockOutlineRenderState = null;
        }
    }

    @Inject(method = "submitFeatures", at = @At("TAIL"))
    private void diegesis$appendScene(LevelRenderState state, SubmitNodeCollector collector, boolean outline, CallbackInfo ci) {
        SceneRenderer.append(state, submitNodeStorage);
    }
}

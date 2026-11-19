package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.render.SceneRenderer;

/**
 * Extracts Diegesis 3D scene geometry into the frame's {@code LevelRenderState} right after level extraction.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "extract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V"))
    private void diegesis$extractScene(DeltaTracker tracker, boolean advance, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.gameRenderer.gameRenderState().shouldRenderLevel) {
            SceneRenderer.extract();
        }
    }
}

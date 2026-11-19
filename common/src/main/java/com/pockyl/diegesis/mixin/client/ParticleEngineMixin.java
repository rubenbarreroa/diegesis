package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.render.ClearView;

@Mixin(value = ParticleEngine.class, priority = 1100)
abstract class ParticleEngineMixin {
    @Inject(method = "extract", at = @At("RETURN"), require = 0)
    private void diegesis$restoreDrawn(CallbackInfo info) {
        ClearView.restoreParticle();
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void diegesis$restoreBeforeTick(CallbackInfo info) {
        ClearView.restoreParticle();
    }
}

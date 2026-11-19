package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.render.ClearView;

/** World particles in the way of the camera dissolve while focused at a station. */
@Mixin(value = SingleQuadParticle.class, priority = 900)
abstract class SingleQuadParticleMixin extends Particle implements ClearView.FadingParticle {
    @Shadow
    protected float quadSize;

    @Shadow
    protected float alpha;

    @Unique
    private float diegesis$alpha;
    @Unique
    private float diegesis$quadSize;

    protected SingleQuadParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "extract", at = @At("HEAD"))
    private void diegesis$fade(QuadParticleRenderState state, Camera camera, float partialTicks, CallbackInfo info) {
        Vec3 eye = camera.position();
        float fade = ClearView.particleFade(Mth.lerp(partialTicks, xo, x) - eye.x, Mth.lerp(partialTicks, yo, y) - eye.y,
                Mth.lerp(partialTicks, zo, z) - eye.z);
        if (fade < 1.0F) {
            diegesis$alpha = alpha;
            diegesis$quadSize = quadSize;
            alpha *= fade;
            quadSize *= fade;
            ClearView.fadedParticle(this);
        }
    }

    @Override
    public void diegesis$restore() {
        alpha = diegesis$alpha;
        quadSize = diegesis$quadSize;
    }
}

package com.pockyl.diegesis.fabric.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.hud.HudSlide;

/** Boss bars slide up with the rest of the HUD on Minecraft 26.3. */
@Mixin(BossHealthOverlay.class)
abstract class BossHealthOverlayMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$begin(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        FocusController focus = FocusController.get();
        if (focus.isActive() && focus.options().hideHud() && (HudSlide.gone() || focus.isHudHidden())) {
            ci.cancel();
            return;
        }
        HudSlide.begin("boss", graphics);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"), require = 0)
    private void diegesis$end(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        HudSlide.end(graphics);
    }
}

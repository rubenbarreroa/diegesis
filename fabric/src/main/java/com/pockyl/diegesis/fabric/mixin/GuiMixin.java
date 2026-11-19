package com.pockyl.diegesis.fabric.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.hud.HudSlide;

/**
 * Slides and hides vanilla HUD layers on Minecraft 26.3 while working at a station.
 */
@Mixin(Hud.class)
abstract class GuiMixin {
    @Unique
    private GuiGraphicsExtractor diegesis$graphics;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void diegesis$frame(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        diegesis$graphics = graphics;
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$noCrosshair(CallbackInfo ci) {
        if (diegesis$hides()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$hotbar(CallbackInfo ci) {
        diegesis$begin(HudSlide.HOTBAR, ci);
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$health(CallbackInfo ci) {
        diegesis$begin("health", ci);
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$vehicle(CallbackInfo ci) {
        diegesis$begin("vehicle_health", ci);
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$hotbarDecorations(CallbackInfo ci) {
        if (diegesis$hides() && (HudSlide.gone() || FocusController.get().isHudHidden())) {
            ci.cancel();
        }
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$itemName(CallbackInfo ci) {
        diegesis$begin("item_name", ci);
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$effects(CallbackInfo ci) {
        diegesis$begin("effects", ci);
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$scoreboard(CallbackInfo ci) {
        diegesis$begin("scoreboard", ci);
    }

    @Inject(method = {"extractItemHotbar", "extractPlayerHealth", "extractVehicleHealth",
            "extractSelectedItemName", "extractEffects", "displayScoreboardSidebar"}, at = @At("RETURN"), require = 0)
    private void diegesis$drawn(CallbackInfo ci) {
        if (diegesis$graphics != null) {
            HudSlide.end(diegesis$graphics);
        }
    }

    @Unique
    private void diegesis$begin(String key, CallbackInfo ci) {
        if (diegesis$hides() && (HudSlide.gone() || FocusController.get().isHudHidden())) {
            ci.cancel();
        } else if (diegesis$graphics != null) {
            HudSlide.begin(key, diegesis$graphics);
        }
    }

    @Unique
    private static boolean diegesis$hides() {
        FocusController focus = FocusController.get();
        return focus.isActive() && focus.options().hideHud();
    }
}

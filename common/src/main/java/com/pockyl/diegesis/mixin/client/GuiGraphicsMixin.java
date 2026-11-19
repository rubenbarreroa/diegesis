package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.hud.HudSlide;

/** Measures where the HUD draws so each part slides off to its nearest edge. */
@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsMixin {
    @Inject(method = "fill(IIIII)V", at = @At("HEAD"), require = 0)
    private void diegesis$fill(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        HudSlide.include(self().pose(), x1, y1, x2, y2);
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V", at = @At("HEAD"), require = 0)
    private void diegesis$text(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        int w = font.width(text);
        if (w > 0) {
            HudSlide.include(self().pose(), x, y, x + w, y + font.lineHeight);
        }
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"), require = 0)
    private void diegesis$sequence(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        int w = font.width(text);
        if (w > 0) {
            HudSlide.include(self().pose(), x, y, x + w, y + font.lineHeight);
        }
    }

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"), require = 0)
    private void diegesis$item(ItemStack stack, int x, int y, CallbackInfo ci) {
        HudSlide.include(self().pose(), x, y, x + 16, y + 16);
    }

    private GuiGraphicsExtractor self() {
        return (GuiGraphicsExtractor) (Object) this;
    }
}

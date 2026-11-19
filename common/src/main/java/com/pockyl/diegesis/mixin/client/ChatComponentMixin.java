package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.hud.HudSlide;

/** Slides the chat off to the left and hides it while the player works at a station whose scene asks for it. */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    @Unique
    private boolean diegesis$slides;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void diegesis$hideChat(GuiGraphicsExtractor graphics, Font font, int tickCount, int mouseX, int mouseY,
            ChatComponent.DisplayMode displayMode, boolean focused, CallbackInfo ci) {
        FocusController focus = FocusController.get();
        if (focus.isActive() && focus.options().hideChat() && HudSlide.gone()) {
            ci.cancel();
            return;
        }
        diegesis$slides = !focus.isActive() || focus.options().hideChat();
        if (diegesis$slides) {
            HudSlide.begin(HudSlide.CHAT, graphics);
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void diegesis$chatDrawn(GuiGraphicsExtractor graphics, Font font, int tickCount, int mouseX, int mouseY,
            ChatComponent.DisplayMode displayMode, boolean focused, CallbackInfo ci) {
        if (diegesis$slides) {
            HudSlide.end(graphics);
        }
    }
}

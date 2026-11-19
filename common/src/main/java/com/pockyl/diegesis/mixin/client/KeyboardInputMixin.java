package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/** Keeps the player standing while they work at a station. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void diegesis$standAtStation(CallbackInfo ci) {
        FocusController focus = FocusController.get();
        if (!focus.isActive() || focus.isExiting()) {
            return;
        }
        ClientInput input = (ClientInput) (Object) this;
        input.keyPresses = Input.EMPTY;
        input.moveVector = Vec2.ZERO;
    }
}

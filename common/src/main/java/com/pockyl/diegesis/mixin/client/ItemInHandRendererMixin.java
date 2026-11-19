package com.pockyl.diegesis.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/** Hides the first-person hands while working at a station on Minecraft 26.3. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class ItemInHandRendererMixin {
    @WrapMethod(method = "submitHandsWithItems")
    private void diegesis$noHandsAtStation(float partialTick, PoseStack pose, SubmitNodeCollector collector,
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, Operation<Void> original) {
        if (!FocusController.get().isHandGone()) {
            original.call(partialTick, pose, collector, player, hands);
        }
    }
}

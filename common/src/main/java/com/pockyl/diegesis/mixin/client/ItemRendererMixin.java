package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.mesh.ItemShapes;

/** Items with a shape made from their data: hooks item render-state extraction in 26.3. */
@Mixin(ItemModelResolver.class)
abstract class ItemRendererMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void diegesis$shapedItem(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level,
            ItemOwner owner, int seed, CallbackInfo ci) {
        ItemShapes.apply(stack, state);
    }
}

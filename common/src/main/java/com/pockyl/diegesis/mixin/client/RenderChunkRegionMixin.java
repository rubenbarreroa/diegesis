package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/** Chunk meshes skip blocks in the camera's way at a station. */
@Mixin(RenderSectionRegion.class)
abstract class RenderChunkRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void diegesis$clearBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (ClearBlocks.hides(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}

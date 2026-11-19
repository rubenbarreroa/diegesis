package com.pockyl.diegesis.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.impl.client.HiddenBlocks;

import java.util.function.Predicate;

/** The model of a block a scene can stand in for: it draws nothing at the positions {@link HiddenBlocks} names. */
final class HideableFabricModel extends WrapperBlockStateModel {
    HideableFabricModel(BlockStateModel model) {
        super(model);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
            Predicate<Direction> cullTest) {
        if (!HiddenBlocks.isHidden(pos)) {
            HiddenBlocks.built(pos);
            super.emitQuads(emitter, level, pos, state, random, cullTest);
        }
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return null;
    }
}

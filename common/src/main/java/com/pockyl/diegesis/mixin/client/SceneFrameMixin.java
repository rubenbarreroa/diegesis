package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.pockyl.diegesis.impl.client.render.SceneFrameAccess;

@Mixin(LevelRenderState.class)
abstract class SceneFrameMixin implements SceneFrameAccess {
    @Unique
    private SubmitNodeStorage diegesis$scene;

    @Override
    public SubmitNodeStorage diegesis$getScene() {
        return diegesis$scene;
    }

    @Override
    public void diegesis$setScene(SubmitNodeStorage scene) {
        diegesis$scene = scene;
    }
}

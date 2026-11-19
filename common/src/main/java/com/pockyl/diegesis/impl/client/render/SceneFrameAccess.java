package com.pockyl.diegesis.impl.client.render;

import net.minecraft.client.renderer.SubmitNodeStorage;

/** Duck interface mixed onto {@code LevelRenderState} to carry Diegesis scene geometry across extraction and feature submission. */
public interface SceneFrameAccess {
    SubmitNodeStorage diegesis$getScene();

    void diegesis$setScene(SubmitNodeStorage scene);
}

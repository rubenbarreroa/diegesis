package com.pockyl.diegesis.impl.client.render;

import net.minecraft.client.renderer.rendertype.RenderType;

import com.pockyl.diegesis.api.client.DiegesisBuffers;

/**
 * Kept-type workaround from 1.21.1 Iris/BufferSource; no-op under Minecraft 26.3's SubmitNodeCollector.
 */
public final class KeptTypes {
    private KeptTypes() {
    }

    public static void keep(DiegesisBuffers buffers, RenderType type) {
    }
}

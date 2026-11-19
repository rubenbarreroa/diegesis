package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

import com.pockyl.diegesis.api.client.NodePainter;

/**
 * Offscreen 4x supersampling pass from 1.21.1; on Minecraft 26.3 RenderPearl all node painters
 * submit directly into the level feature queue so {@link #defer} always returns {@code false}.
 */
public final class SmoothPass {
    private SmoothPass() {
    }

    static void begin(Matrix4f projection, Matrix4f view) {
    }

    static boolean defer(NodePainter painter, PoseStack poseStack, int light) {
        return false;
    }

    public static void draw() {
    }

    public static void release() {
    }
}

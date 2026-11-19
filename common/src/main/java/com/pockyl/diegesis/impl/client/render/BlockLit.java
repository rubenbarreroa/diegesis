package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A block model drawn lit as the world lights the station's block ({@code Visual.litAsBlock}):
 * each face takes directional diffuse shading and the light of the neighbouring air block it faces.
 */
final class BlockLit {
    private BlockLit() {
    }

    static void draw(BlockStateModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer, Matrix4f toWorld) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Matrix4f world = new Matrix4f(toWorld).mul(poseStack.last().pose());
        QuadInstance instance = new QuadInstance();
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        var tintSource = state != null ? minecraft.getBlockColors().getTintSource(state, 0) : null;
        int blockTint = tintSource != null ? tintSource.colorInWorld(state, level, pos) : -1;
        float tintR = blockTint == -1 ? 1.0F : (blockTint >> 16 & 0xFF) / 255.0F;
        float tintG = blockTint == -1 ? 1.0F : (blockTint >> 8 & 0xFF) / 255.0F;
        float tintB = blockTint == -1 ? 1.0F : (blockTint & 0xFF) / 255.0F;

        for (BakedQuad quad : ItemModels26.collectQuads(model)) {
            Direction quadDir = quad.direction();
            Vector3f normal = world.transformDirection(new Vector3f(quadDir.step())).normalize();
            float shade = diffuse(normal);
            Direction worldDir = Direction.getApproximateNearest(normal.x, normal.y, normal.z);
            BlockPos lit = pos.relative(worldDir);
            int light = LightCoordsUtil.getLightCoords(level, level.getBlockState(lit).isSolidRender() ? pos : lit);
            if (quad.materialInfo().lightEmission() > 0) {
                light = LightCoordsUtil.lightCoordsWithEmission(light, quad.materialInfo().lightEmission());
            }
            boolean tinted = quad.materialInfo().isTinted();
            float r = shade * (tinted ? tintR : 1.0F);
            float g = shade * (tinted ? tintG : 1.0F);
            float b = shade * (tinted ? tintB : 1.0F);
            instance.setColor(ARGB.colorFromFloat(1.0F, r, g, b));
            instance.setLightCoords(light);
            consumer.putBakedQuad(poseStack.last(), quad, instance);
        }
    }

    /** The shade the world gives a face by the way it faces: 1 up, 0.5 down, 0.8 north and south, 0.6 east and west, between. */
    private static float diffuse(Vector3f normal) {
        float up = normal.y * normal.y * (3.0F + normal.y) / 4.0F;
        return Math.min(1.0F, normal.x * normal.x * 0.6F + up + normal.z * normal.z * 0.8F);
    }
}

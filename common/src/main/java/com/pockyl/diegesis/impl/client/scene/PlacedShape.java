package com.pockyl.diegesis.impl.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisBuffers;
import com.pockyl.diegesis.impl.client.render.ItemModels26;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What an item placed freely looks like and how big it really is (ported to Minecraft 26.3 ItemStackRenderState / BlockStateModel).
 */
public final class PlacedShape {
    private static final float THINNEST = 0.012F;
    private static final float SOLID_SIZE = 0.8F;
    private static final Map<ShapeKey, PlacedShape> CACHE = new ConcurrentHashMap<>();

    private record ShapeKey(Item item, DataComponentPatch patch, boolean blockModels, boolean framePose) {
    }

    private final BlockStateModel blockModel;
    private final BlockState block;
    private final boolean translucent;
    private final Vector3f center;
    private final Vector3f half;
    private final float unit;
    private final int light;
    private final boolean flame;
    private final boolean custom;
    private final ItemDisplayContext context;
    private final Vector3f hitHalf;
    private final Quaternionf hitTurn;

    private PlacedShape(BlockStateModel blockModel, BlockState block, Box3 box, Fit fit, int light, boolean flame,
            boolean custom, ItemDisplayContext context) {
        this.context = context;
        this.blockModel = blockModel;
        this.block = block;
        this.translucent = blockModel != null && blockModel.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT);
        this.center = new Vector3f(box.min).add(box.max).mul(0.5F);
        this.half = new Vector3f(box.max).sub(box.min).mul(0.5F).max(new Vector3f(THINNEST));
        float longest = Math.max(half.x, Math.max(half.y, half.z));
        boolean solid = Math.min(half.x, Math.min(half.y, half.z)) > longest * 0.6F;
        this.unit = (solid ? SOLID_SIZE : 1.0F) * 0.5F / longest;
        this.light = light;
        this.flame = flame;
        this.custom = custom;
        this.hitHalf = fit == null ? new Vector3f(half) : new Vector3f(fit.half()).max(new Vector3f(THINNEST));
        this.hitTurn = fit == null ? new Quaternionf() : new Quaternionf(fit.turn());
    }

    private record Fit(Quaternionf turn, Vector3f half) {
    }

    private static final int FIT_STEP = 1;
    private static final float FIT_GAIN = 0.9F;
    private static final float FIT_OVER = 1.03F;

    static void clearCache() {
        CACHE.clear();
    }

    public static PlacedShape of(ItemStack stack, ClientLevel level, boolean blockModels) {
        return of(stack, level, blockModels, true);
    }

    public static PlacedShape of(ItemStack stack, ClientLevel level, boolean blockModels, boolean framePose) {
        ShapeKey key = new ShapeKey(stack.getItem(), stack.getComponentsPatch(), blockModels, framePose);
        return CACHE.computeIfAbsent(key, k -> compute(stack, level, blockModels, framePose));
    }

    private static PlacedShape compute(ItemStack stack, ClientLevel level, boolean blockModels, boolean framePose) {
        ItemStackRenderState fixedState = ItemModels26.resolveItem(stack, ItemDisplayContext.FIXED, level, 0);
        boolean is3d = fixedState.usesBlockLight();
        boolean isCustom = hasSpecialRenderer(fixedState);
        BlockState state = stack.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState() : null;
        if (state != null && state.hasProperty(BlockStateProperties.LIT)) {
            state = state.setValue(BlockStateProperties.LIT, true);
        }
        int light = state == null ? 0 : state.getLightEmission();
        boolean flame = state != null && (state.getBlock() instanceof TorchBlock || state.getBlock() instanceof AbstractCandleBlock);
        if (blockModels && standsAsBlock(stack, is3d, state)) {
            BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
            return new PlacedShape(model, state, blockBox(model), null, light, flame, false, ItemDisplayContext.FIXED);
        }
        if (!framePose && is3d && !isCustom) {
            ItemStackRenderState noneState = ItemModels26.resolveItem(stack, ItemDisplayContext.NONE, level, 0);
            return new PlacedShape(null, null, itemBox(noneState, false), null, light, flame, false, ItemDisplayContext.NONE);
        }
        Box3 box = itemBox(fixedState, !is3d);
        Fit fit = is3d || isCustom ? null : flatFit(fixedState, box);
        return new PlacedShape(null, null, box, fit, light, flame, isCustom, ItemDisplayContext.FIXED);
    }

    private static boolean hasSpecialRenderer(ItemStackRenderState state) {
        for (int i = 0; i < state.activeLayerCount; i++) {
            if (state.layers[i].specialRenderer != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean standsAsBlock(ItemStack stack, boolean is3d, BlockState state) {
        return state != null && !is3d && stack.getItem().getDescriptionId().equals(state.getBlock().getDescriptionId())
                && state.getRenderShape() == RenderShape.MODEL
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    private static Box3 blockBox(BlockStateModel model) {
        Box3 box = new Box3();
        for (BakedQuad quad : ItemModels26.collectQuads(model)) {
            for (int i = 0; i < 4; i++) {
                box.add(quad.position(i));
            }
        }
        return box.orElse(0.0F, 1.0F);
    }

    private static Matrix4f layerTransform(ItemStackRenderState state, ItemStackRenderState.LayerRenderState layer) {
        PoseStack pose = new PoseStack();
        layer.itemTransform.apply(state.displayContext.leftHand(), pose.last());
        pose.last().mulPose(layer.localTransform);
        return new Matrix4f(pose.last().pose());
    }

    private static Fit flatFit(ItemStackRenderState state, Box3 box) {
        List<Vector3f> points = new ArrayList<>();
        for (int idx = 0; idx < state.activeLayerCount; idx++) {
            var layer = state.layers[idx];
            Matrix4f transform = layerTransform(state, layer);
            for (BakedQuad quad : layer.quads.all()) {
                if (quad.direction().getAxis() == Direction.Axis.Z) {
                    continue;
                }
                for (int i = 0; i < 4; i++) {
                    Vector3fc p = quad.position(i);
                    points.add(transform.transformPosition(new Vector3f(p.x(), p.y(), p.z())));
                }
            }
        }
        if (points.size() < 3 || box.isEmpty()) {
            return null;
        }
        Vector3f size = new Vector3f(box.max).sub(box.min);
        Vector3f middle = new Vector3f(box.min).add(box.max).mul(0.5F);
        int thin = size.x <= size.y && size.x <= size.z ? 0 : size.y <= size.z ? 1 : 2;
        int a = thin == 0 ? 1 : 0;
        int b = thin == 2 ? 1 : 2;
        float squareArea = size.get(a) * size.get(b) / 4.0F;
        float bestArea = squareArea;
        float bestAngle = 0.0F;
        float bestU = size.get(a) / 2.0F;
        float bestV = size.get(b) / 2.0F;
        for (int degrees = FIT_STEP; degrees < 90; degrees += FIT_STEP) {
            float angle = (float) Math.toRadians(degrees);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float most = 0.0F;
            float mostV = 0.0F;
            for (Vector3f point : points) {
                float du = point.get(a) - middle.get(a);
                float dv = point.get(b) - middle.get(b);
                most = Math.max(most, Math.abs(cos * du + sin * dv));
                mostV = Math.max(mostV, Math.abs(-sin * du + cos * dv));
            }
            if (most * mostV < bestArea) {
                bestArea = most * mostV;
                bestAngle = angle;
                bestU = most;
                bestV = mostV;
            }
        }
        if (bestAngle == 0.0F || bestArea > squareArea * FIT_GAIN) {
            return null;
        }
        float cos = (float) Math.cos(bestAngle);
        float sin = (float) Math.sin(bestAngle);
        float roomA = a == 1 ? size.get(a) / 2.0F * FIT_OVER : Float.MAX_VALUE;
        float roomB = b == 1 ? size.get(b) / 2.0F * FIT_OVER : Float.MAX_VALUE;
        if (bestU >= bestV) {
            bestU = Math.max(0.0F, Math.min(bestU, Math.min((roomA - sin * bestV) / cos, (roomB - cos * bestV) / sin)));
            bestV = Math.max(0.0F, Math.min(bestV, Math.min((roomA - cos * bestU) / sin, (roomB - sin * bestU) / cos)));
        } else {
            bestV = Math.max(0.0F, Math.min(bestV, Math.min((roomA - cos * bestU) / sin, (roomB - sin * bestU) / cos)));
            bestU = Math.max(0.0F, Math.min(bestU, Math.min((roomA - sin * bestV) / cos, (roomB - cos * bestV) / sin)));
        }
        if (bestU <= 0.0F || bestV <= 0.0F) {
            return null;
        }
        Vector3f half = new Vector3f();
        half.setComponent(a, bestU);
        half.setComponent(b, bestV);
        half.setComponent(thin, size.get(thin) / 2.0F);
        org.joml.Matrix3f sides = new org.joml.Matrix3f();
        Vector3f first = new Vector3f();
        first.setComponent(a, (float) Math.cos(bestAngle));
        first.setComponent(b, (float) Math.sin(bestAngle));
        Vector3f second = new Vector3f();
        second.setComponent(a, (float) -Math.sin(bestAngle));
        second.setComponent(b, (float) Math.cos(bestAngle));
        Vector3f across = new Vector3f();
        across.setComponent(thin, 1.0F);
        sides.setColumn(a, first);
        sides.setColumn(b, second);
        sides.setColumn(thin, across);
        if (sides.determinant() < 0.0F) {
            sides.setColumn(thin, across.negate());
        }
        return new Fit(new Quaternionf().setFromNormalized(sides), half);
    }

    private static Box3 itemBox(ItemStackRenderState state, boolean edgesOnly) {
        Box3 edges = new Box3();
        Box3 all = new Box3();
        for (int idx = 0; idx < state.activeLayerCount; idx++) {
            var layer = state.layers[idx];
            Matrix4f transform = layerTransform(state, layer);
            for (BakedQuad quad : layer.quads.all()) {
                boolean big = quad.direction().getAxis() == Direction.Axis.Z;
                for (int i = 0; i < 4; i++) {
                    Vector3fc p = quad.position(i);
                    Vector3f pt = transform.transformPosition(new Vector3f(p.x(), p.y(), p.z()));
                    all.add(pt);
                    if (!big) {
                        edges.add(pt);
                    }
                }
            }
        }
        Box3 used = edgesOnly && !edges.isEmpty() ? edges : all;
        return used.orElse(-0.5F, 0.5F);
    }

    public int light() {
        return light;
    }

    public boolean hasFlame() {
        return flame;
    }

    public boolean castsShadow() {
        return !custom;
    }

    public float modelScale(float size) {
        return size * unit;
    }

    public boolean isFlat() {
        float longest = Math.max(half.x, Math.max(half.y, half.z));
        return block == null && Math.min(half.x, Math.min(half.y, half.z)) < longest * 0.2F;
    }

    public Vector3fc half() {
        return half;
    }

    public Vector3fc hitHalf() {
        return hitHalf;
    }

    public Quaternionfc hitTurn() {
        return hitTurn;
    }

    public Vector3f extents(Quaternionfc rotation, float size) {
        Matrix4f turn = new Matrix4f().rotation(new Quaternionf(rotation));
        float scale = modelScale(size);
        return new Vector3f(
                Math.abs(turn.m00()) * half.x + Math.abs(turn.m10()) * half.y + Math.abs(turn.m20()) * half.z,
                Math.abs(turn.m01()) * half.x + Math.abs(turn.m11()) * half.y + Math.abs(turn.m21()) * half.z,
                Math.abs(turn.m02()) * half.x + Math.abs(turn.m12()) * half.y + Math.abs(turn.m22()) * half.z).mul(scale);
    }

    public void draw(ItemStack stack, PoseStack poseStack, DiegesisBuffers buffers, int packedLight, ClientLevel level, int seed) {
        Minecraft minecraft = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(-center.x, -center.y, -center.z);
        if (block != null && blockModel != null) {
            VertexConsumer consumer = buffers.getBuffer(translucent ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet());
            var tintSource = minecraft.getBlockColors().getTintSource(block, 0);
            int color = tintSource != null ? tintSource.color(block) : -1;
            float red = color == -1 ? 1.0F : (color >> 16 & 0xFF) / 255.0F;
            float green = color == -1 ? 1.0F : (color >> 8 & 0xFF) / 255.0F;
            float blue = color == -1 ? 1.0F : (color & 0xFF) / 255.0F;
            ItemModels26.renderBlockModel(poseStack.last(), consumer, block, blockModel, red, green, blue, packedLight,
                    OverlayTexture.NO_OVERLAY);
        } else {
            ItemModels26.renderStatic(stack, context, packedLight, OverlayTexture.NO_OVERLAY, poseStack,
                    com.pockyl.diegesis.impl.client.render.SceneRenderer.solidItems(buffers), level, seed);
        }
        poseStack.popPose();
    }

    private static final class Box3 {
        final Vector3f min = new Vector3f(Float.MAX_VALUE);
        final Vector3f max = new Vector3f(-Float.MAX_VALUE);

        void add(Vector3fc point) {
            min.min(point);
            max.max(point);
        }

        boolean isEmpty() {
            return min.x > max.x;
        }

        Box3 orElse(float low, float high) {
            if (isEmpty()) {
                add(new Vector3f(low));
                add(new Vector3f(high));
            }
            return this;
        }
    }
}

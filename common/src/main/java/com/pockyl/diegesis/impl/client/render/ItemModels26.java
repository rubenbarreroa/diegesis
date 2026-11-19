package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.MatrixUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.client.DiegesisBuffers;
import com.pockyl.diegesis.impl.client.mesh.ItemShapes;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for drawing Minecraft 26.3 {@link ItemStackRenderState} and {@link BlockStateModel} geometry
 * directly into {@link DiegesisBuffers}.
 */
public final class ItemModels26 {
    private static final Direction[] SIDES = {
            null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private ItemModels26() {
    }

    public static ItemStackRenderState resolveItem(ItemStack stack, ItemDisplayContext context, Level level, int seed) {
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, seed);
        ItemShapes.apply(stack, state);
        return state;
    }

    public static void renderStatic(ItemStack stack, ItemDisplayContext context, int light, int overlay, PoseStack pose,
            DiegesisBuffers buffers, Level level, int seed) {
        renderState(resolveItem(stack, context, level, seed), pose, buffers, light, overlay);
    }

    public static void renderState(ItemStackRenderState state, PoseStack pose, DiegesisBuffers buffers, int light, int overlay) {
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        for (int i = 0; i < state.activeLayerCount; i++) {
            ItemStackRenderState.LayerRenderState layer = state.layers[i];
            pose.pushPose();
            layer.itemTransform.apply(state.displayContext.leftHand(), pose.last());
            pose.last().mulPose(layer.localTransform);
            if (layer.specialRenderer != null) {
                drawSpecial(layer, pose, buffers, light, overlay);
            } else {
                PoseStack.Pose foilPose = pose.last().copy();
                if (state.displayContext == ItemDisplayContext.GUI) {
                    MatrixUtil.mulComponentWise(foilPose.pose(), 0.5F);
                } else if (state.displayContext.firstPerson()) {
                    MatrixUtil.mulComponentWise(foilPose.pose(), 0.75F);
                }
                var tints = layer.tintLayers();
                for (BakedQuad quad : layer.quads.all()) {
                    var material = quad.materialInfo();
                    RenderType type = switch (layer.foilType) {
                        case NONE -> material.itemRenderType();
                        case STANDARD -> material.itemGlintRenderType();
                        case SPECIAL -> material.itemGlintSpecialRenderType();
                    };
                    int tint = material.isTinted() && material.tintIndex() >= 0 && material.tintIndex() < tints.size()
                            ? tints.getInt(material.tintIndex()) : -1;
                    instance.setColor(tint);
                    if (layer.foilType == ItemStackRenderState.FoilType.SPECIAL) {
                        buffers.getBuffer(type).putBakedQuadWithGlint(pose.last(), quad, instance, foilPose);
                    } else {
                        buffers.getBuffer(type).putBakedQuad(pose.last(), quad, instance);
                    }
                }
            }
            pose.popPose();
        }
    }

    /** Renders a {@link BlockStateModel} directly into {@code consumer} with uniform tint, light and overlay. */
    public static void renderBlockModel(PoseStack.Pose pose, VertexConsumer consumer, BlockState state, BlockStateModel model,
            float red, float green, float blue, int light, int overlay) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        model.collectParts(random, parts);
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        int tintedColor = ARGB.colorFromFloat(1.0F, red, green, blue);
        for (BlockStateModelPart part : parts) {
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) {
                    instance.setColor(quad.materialInfo().isTinted() ? tintedColor : -1);
                    consumer.putBakedQuad(pose, quad, instance);
                }
            }
        }
    }

    /** Collects all quads of a {@link BlockStateModel} across all parts and sides. */
    public static List<BakedQuad> collectQuads(BlockStateModel model) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        List<BakedQuad> result = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction side : SIDES) {
                result.addAll(part.getQuads(side));
            }
        }
        return result;
    }

    private static void drawSpecial(ItemStackRenderState.LayerRenderState layer, PoseStack pose, DiegesisBuffers buffers,
            int light, int overlay) {
        SubmitNodeStorage storage = new SubmitNodeStorage();
        submitSpecialUnchecked(layer, pose, storage, light, overlay);
        List<SubmitNode> nodes = new ArrayList<>();
        storage.drainPhases(phase -> phase.sortInto((node, ordered) -> nodes.add(node)));
        boolean unsupported = false;
        for (SubmitNode node : nodes) {
            if (!(node instanceof ModelFeatureRenderer.Submit<?>) && !(node instanceof CustomFeatureRenderer.Submit)) {
                unsupported = true;
                break;
            }
        }
        if (unsupported) {
            SubmitNodeCollector collector = SceneRenderer.currentCollector();
            if (collector != null) {
                submitSpecialUnchecked(layer, pose, collector, light, overlay);
            }
            return;
        }
        for (SubmitNode node : nodes) {
            if (node instanceof ModelFeatureRenderer.Submit<?> model) {
                drawModel(model, buffers);
            } else if (node instanceof CustomFeatureRenderer.Submit custom) {
                custom.customGeometryRenderer().render(custom.pose(), buffers.getBuffer(custom.renderType()));
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void submitSpecialUnchecked(ItemStackRenderState.LayerRenderState layer, PoseStack pose,
            SubmitNodeCollector collector, int light, int overlay) {
        ((net.minecraft.client.renderer.special.SpecialModelRenderer) layer.specialRenderer).submit(
                layer.argumentForSpecialRendering, pose, collector, light, overlay,
                layer.foilType != ItemStackRenderState.FoilType.NONE, 0);
    }

    private static <S> void drawModel(ModelFeatureRenderer.Submit<S> submit, DiegesisBuffers buffers) {
        PoseStack pose = new PoseStack();
        pose.last().set(submit.pose());
        VertexConsumer consumer = buffers.getBuffer(submit.renderType());
        if (submit.sheetedDecalPose() != null) {
            consumer = new SheetedDecalTextureGenerator(consumer, submit.sheetedDecalPose(), 1.0F);
        } else if (submit.uvMapping() != null) {
            consumer = submit.uvMapping().wrap(consumer);
        }
        submit.model().setupAnim(submit.state());
        submit.model().renderToBuffer(pose, consumer, submit.lightCoords(), submit.overlayCoords(), submit.tintedColor());
    }
}

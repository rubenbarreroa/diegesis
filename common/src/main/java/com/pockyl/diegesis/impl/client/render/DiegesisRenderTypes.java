package com.pockyl.diegesis.impl.client.render;

import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.DiegesisMod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Diegesis custom RenderPipelines and RenderTypes built for Minecraft 26.3 RenderPearl. */
final class DiegesisRenderTypes {
    private static final BlendFunction LIGHTEN = new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ONE);

    private static final RenderPipeline LIGHT_POOL_PIPELINE = registerVariant(
            RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE, "pipeline/light_pool", LIGHTEN, false, false);
    private static final RenderPipeline GHOST_ENTITY_PIPELINE = registerVariant(
            RenderPipelines.ENTITY_TRANSLUCENT, "pipeline/ghost_entity", BlendFunction.TRANSLUCENT, true, false);
    private static final RenderPipeline CLEAR_BLOCK_PIPELINE = registerVariant(
            RenderPipelines.ENTITY_TRANSLUCENT_CULL, "pipeline/clear_block", BlendFunction.TRANSLUCENT, true, false);

    private static final Map<Identifier, RenderType> LIGHT_POOLS = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> GHOSTS = new ConcurrentHashMap<>();
    private static RenderType clearSheet;

    private DiegesisRenderTypes() {
    }

    private static RenderPipeline registerVariant(RenderPipeline source, String location, BlendFunction blend, boolean cull, boolean depthWrite) {
        var values = new HashMap<>(source.getShaderDefines().values());
        var defines = new ShaderDefines(values, source.getShaderDefines().flags());
        var snippet = new RenderPipeline.Snippet(
                source.getShaders(),
                Optional.of(defines),
                Optional.of(source.getBindGroupLayouts()),
                source.getColorTargetStates().toArray(ColorTargetState[]::new),
                source.getColorTargetStates().size(),
                Optional.ofNullable(source.getDepthStencilState()),
                Optional.of(source.getPolygonMode()),
                Optional.of(cull),
                source.getVertexFormatBindings().toArray(VertexFormat[]::new),
                Optional.of(source.getPrimitiveTopology()),
                source.pushConstantSize());
        var builder = RenderPipeline.builder(snippet)
                .withLocation(DiegesisMod.id(location))
                .withCull(cull)
                .withColorTargetState(new ColorTargetState(blend));
        DepthStencilState depth = source.getDepthStencilState();
        if (depth != null) {
            builder.withDepthStencilState(new DepthStencilState(depth.depthTest(), depthWrite, depth.depthBiasScaleFactor(), depth.depthBiasConstant()));
        }
        return RenderPipelines.register(builder.build());
    }

    static RenderType lightPool(Identifier texture) {
        return LIGHT_POOLS.computeIfAbsent(texture, id -> RenderType.create("diegesis_light_pool",
                RenderSetup.builder(LIGHT_POOL_PIPELINE)
                        .withTexture("Sampler0", id)
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .createRenderSetup()));
    }

    static RenderType ghostEntity(Identifier texture) {
        return GHOSTS.computeIfAbsent(texture, id -> RenderType.create("diegesis_ghost",
                RenderSetup.builder(GHOST_ENTITY_PIPELINE)
                        .withTexture("Sampler0", id)
                        .useLightmap()
                        .useOverlay()
                        .sortOnUpload()
                        .createRenderSetup()));
    }

    static RenderType itemSheet() {
        return Sheets.translucentBlockItemSheet();
    }

    static RenderType clearBlockSheet() {
        if (clearSheet == null) {
            clearSheet = RenderType.create("diegesis_clear",
                    RenderSetup.builder(CLEAR_BLOCK_PIPELINE)
                            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                            .useLightmap()
                            .useOverlay()
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .sortOnUpload()
                            .createRenderSetup());
        }
        return clearSheet;
    }
}

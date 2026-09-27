package io.github.itzispyder.impropers3dminimap.util.minecraft;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class RenderConstants {

    public static final RenderPipeline PIPELINE_LINES = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation("pipeline/global_lines_pipeline")
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    public static final RenderPipeline PIPELINE_LINES_STRIP = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation("pipeline/global_lines_pipeline")
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINE_STRIP)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    public static final RenderPipeline PIPELINE_QUADS = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation("pipeline/global_fill_pipeline")
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    public static final RenderPipeline PIPELINE_TRI_FAN = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation("pipeline/global_fill_pipeline")
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_FAN)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    public static final RenderPipeline PIPELINE_TRI = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation("pipeline/global_fill_pipeline")
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    public static final RenderPipeline PIPELINE_TRI_STRIP = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation("pipeline/global_fill_pipeline")
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    public static final RenderPipeline PIPELINE_TEX_QUADS = RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
            .withLocation("pipeline/gui_textured")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    public static final RenderPipeline PIPELINE_TEX_TRI_FAN = RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
            .withLocation("pipeline/gui_textured")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLE_FAN)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();


    // RenderLayer.MultiPhaseParameters and RenderPhase.Texture no longer exist in 1.21.11.
    // RenderLayer is now built from a RenderSetup (see RenderLayer.of(String, RenderSetup) and
    // RenderSetup.Builder), confirmed against the real net.minecraft.client.render.RenderLayers
    // vanilla definitions, which use RenderSetup.builder(pipeline).texture("Sampler0", id)... for
    // every textured layer. Buffer-size hint moved from RenderLayer.of's int param onto the
    // builder as .expectedBufferSize(...).
    private static RenderSetup emptyParams(RenderPipeline pipeline) {
        return RenderSetup.builder(pipeline)
                .expectedBufferSize(256)
                .build();
    }

    private static RenderSetup textureParams(RenderPipeline pipeline, Identifier id) {
        return RenderSetup.builder(pipeline)
                .expectedBufferSize(256)
                .texture("Sampler0", id)
                .build();
    }

    public static final RenderLayer LINES = RenderLayer.of("coherent_layer_lines", emptyParams(PIPELINE_LINES));
    public static final RenderLayer LINES_STRIP = RenderLayer.of("coherent_layer_lines_strip", emptyParams(PIPELINE_LINES_STRIP));
    public static final RenderLayer QUADS = RenderLayer.of("coherent_layer_quads", emptyParams(PIPELINE_QUADS));
    public static final RenderLayer TRI_FAN = RenderLayer.of("coherent_layer_tri_fan", emptyParams(PIPELINE_TRI_FAN));
    public static final RenderLayer TRI_STRIP = RenderLayer.of("coherent_layer_tri_strip", emptyParams(PIPELINE_TRI_STRIP));
    public static final RenderLayer TRI = RenderLayer.of("coherent_layer_tri", emptyParams(PIPELINE_TRI));
    public static final Function<Identifier, RenderLayer> TEX_QUADS = id -> RenderLayer.of("coherent_layer_tex_quad", textureParams(PIPELINE_TEX_QUADS, id));
    public static final Function<Identifier, RenderLayer> TEX_TRI_FAN = id -> RenderLayer.of("coherent_layer_tex_tri_fan", textureParams(PIPELINE_TEX_TRI_FAN, id));

}
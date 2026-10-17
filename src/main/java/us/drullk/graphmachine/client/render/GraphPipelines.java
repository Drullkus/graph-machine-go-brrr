package us.drullk.graphmachine.client.render;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import us.drullk.graphmachine.GraphMachine;

/**
 * The three render pipelines of the graph and their per-instance vertex formats.
 *
 * <p>Every pipeline reads its geometry from {@code gl_VertexIndex} and takes one vertex binding at slot 0
 * with step rate 1, so each "vertex" of the binding is one instance. The attribute order here is the
 * {@code layout(location = n)} order of the shaders in {@code assets/graphmachine/shaders/core}. All
 * three pipelines share the vanilla matrices, fog and globals bind groups plus {@link #GRAPH_INFO}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphPipelines {
    /** The {@code GraphInfo} uniform block; see {@code include/graphinfo.glsl}. */
    public static final BindGroupLayout GRAPH_INFO = BindGroupLayout.builder().withUniform("GraphInfo", UniformType.UNIFORM_BUFFER).build();

    /** One node: position, cube edge length, opaque color. 20 bytes. */
    public static final VertexFormat NODE_FORMAT = VertexFormat.builder(1)
            .addAttribute("NodePos", GpuFormat.RGB32_FLOAT)
            .addAttribute("NodeScale", GpuFormat.R32_FLOAT)
            .addAttribute("NodeColor", GpuFormat.RGBA8_UNORM)
            .build();

    /** One glyph: its node, the node's scale (the label floats that far in front of it), offset and size in font pixels, atlas UVs, color, line offset. 56 bytes. */
    public static final VertexFormat LABEL_FORMAT = VertexFormat.builder(1)
            .addAttribute("NodePos", GpuFormat.RGB32_FLOAT)
            .addAttribute("NodeScale", GpuFormat.R32_FLOAT)
            .addAttribute("GlyphOffset", GpuFormat.RG32_FLOAT)
            .addAttribute("GlyphSize", GpuFormat.RG32_FLOAT)
            .addAttribute("GlyphUV", GpuFormat.RGBA32_FLOAT)
            .addAttribute("GlyphColor", GpuFormat.RGBA8_UNORM)
            .addAttribute("LineOffset", GpuFormat.R32_FLOAT)
            .build();

    /** One edge: both end positions, both end colors, both compacted node indices, pixel width, both end cube sizes. 52 bytes. */
    public static final VertexFormat EDGE_FORMAT = VertexFormat.builder(1)
            .addAttribute("EdgeA", GpuFormat.RGB32_FLOAT)
            .addAttribute("EdgeB", GpuFormat.RGB32_FLOAT)
            .addAttribute("ColorA", GpuFormat.RGBA8_UNORM)
            .addAttribute("ColorB", GpuFormat.RGBA8_UNORM)
            .addAttribute("EdgeNodes", GpuFormat.RG32_SINT)
            .addAttribute("EdgeWidth", GpuFormat.R32_FLOAT)
            .addAttribute("EdgeScales", GpuFormat.RG32_FLOAT)
            .build();

    /** Vertices per node instance: a cube of 12 triangles. */
    public static final int NODE_VERTICES = 36;
    /** Vertices per glyph or edge instance: a quad of 2 triangles. */
    public static final int QUAD_VERTICES = 6;

    private static final RenderPipeline.Snippet GRAPH_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withBindGroupLayout(GRAPH_INFO)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .buildSnippet();

    public static final RenderPipeline NODES = RenderPipeline.builder(GRAPH_SNIPPET)
            .withLocation(id("pipeline/graph_nodes"))
            .withVertexShader(id("core/graph_nodes"))
            .withFragmentShader(id("core/graph_nodes"))
            .withVertexBinding(0, NODE_FORMAT)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final RenderPipeline LABELS = RenderPipeline.builder(GRAPH_SNIPPET)
            .withLocation(id("pipeline/graph_labels"))
            .withVertexShader(id("core/graph_labels"))
            .withFragmentShader(id("core/graph_labels"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, LABEL_FORMAT)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final RenderPipeline EDGES = RenderPipeline.builder(GRAPH_SNIPPET)
            .withLocation(id("pipeline/graph_edges"))
            .withVertexShader(id("core/graph_edges"))
            .withFragmentShader(id("core/graph_edges"))
            .withVertexBinding(0, EDGE_FORMAT)
            // Edges blend, but they also write depth: a nearer edge then hides the edges behind it whatever the
            // draw order, instead of a later far edge painting over a near one.
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();

    private GraphPipelines() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GraphMachine.MODID, path);
    }

    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(NODES);
        event.registerPipeline(LABELS);
        event.registerPipeline(EDGES);
    }
}

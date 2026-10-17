package us.drullk.graphmachine.client.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import us.drullk.graphmachine.GraphMachine;
import us.drullk.graphmachine.client.data.GraphAssets;
import us.drullk.graphmachine.client.data.GraphAssetsLoader;
import us.drullk.graphmachine.client.panel.GraphSettingsStore;
import us.drullk.graphmachine.client.panel.GraphViewSettings;
import us.drullk.graphmachine.client.panel.PanelLayout;
import us.drullk.graphmachine.worldgen.GraphDimensionTypes;

/**
 * Draws the class graph in a level of the graph dimension type.
 *
 * <p>Each frame starts in {@link #onFrameStart}: it starts a {@link GraphViewBuilder} on a worker when
 * the settings or assets changed, moves a finished view into the GPU buffers a few megabytes at a time,
 * and writes the {@code GraphInfo} uniform block. Nodes and labels draw after the opaque blocks, edges
 * after the opaque features, each with one {@code drawIndirect} per instance stream (or a direct draw
 * on a device without indirect draws). Picking runs in the opaque pass, where the camera state is at
 * hand; the hovered node reaches the shaders one frame later.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphRenderer {
    /** Most bytes moved into the GPU buffers per frame while a view is applied. */
    private static final long UPLOAD_BUDGET_BYTES = 4L << 20;
    private static final int UBO_SIZE = 16 * 4;
    private static final int LABEL_STRIDE = GraphPipelines.LABEL_FORMAT.getVertexSize();
    private static final float HOVER_MIX = 0.5F;
    private static final float HOVER_EDGE_WIDTH = 2.0F;
    private static final float LABEL_FADE_START = 64.0F;
    private static final float LABEL_FADE_END = 96.0F;

    private final Vector3f direction = new Vector3f();
    /** Created on the first frame, because the GPU device does not exist while the mod class loads (datagen has none). */
    private @Nullable MappableRingBuffer uniforms;
    private @Nullable GraphGpuBuffers buffers;
    private @Nullable GraphGlyphAtlas atlas;
    private int atlasGeneration = -1;
    private @Nullable CompletableFuture<GraphView> building;
    private @Nullable AtomicBoolean buildCancel;
    private @Nullable GraphViewSettings requestedSettings;
    private int requestedGeneration = -1;
    private @Nullable GpuBufferSlice uniformSlice;
    private int hoveredNode = -1;
    private boolean indirect = true;
    private boolean indirectChecked;

    /** True when the given level is a graph level. */
    public static boolean isGraphLevel(@Nullable ClientLevel level) {
        return level != null && level.dimensionTypeRegistration().is(GraphDimensionTypes.GRAPH);
    }

    /** True while the player is in a graph level and the assets are loaded. */
    public static boolean shouldDraw() {
        return isGraphLevel(Minecraft.getInstance().level) && GraphAssetsLoader.current() != null;
    }

    /** The compacted index of the hovered node, or -1. */
    public int hoveredNode() {
        return this.hoveredNode;
    }

    /** The name of the hovered class, or null. */
    public @Nullable String hoveredClassName() {
        GraphView view = this.buffers == null ? null : this.buffers.frontView();
        GraphAssets assets = GraphAssetsLoader.current();
        if (view == null || assets == null || this.hoveredNode < 0 || this.hoveredNode >= view.nodeCount() || view.assetsGeneration() != assets.generation()) {
            return null;
        }
        return assets.nodes().nodes().get(view.nodeIds()[this.hoveredNode]).className();
    }

    /** True while a new view is being built or uploaded. */
    public boolean applying() {
        return this.building != null || (this.buffers != null && this.buffers.uploading());
    }

    /** Progress of the current apply, 0 to 1; the build counts as the first tenth. */
    public float applyProgress() {
        if (this.building != null) {
            return 0.0F;
        }
        return this.buffers == null ? 1.0F : 0.1F + 0.9F * this.buffers.progress();
    }

    /** The view on screen, or null before the first one finished. */
    public @Nullable GraphView frontView() {
        return this.buffers == null ? null : this.buffers.frontView();
    }

    public void onFrameStart(RenderFrameEvent.Pre event) {
        GraphSettingsStore.saveIfDue();
        GraphAssets assets = GraphAssetsLoader.current();
        if (assets == null || !isGraphLevel(Minecraft.getInstance().level)) {
            this.cancelBuild();
            return;
        }
        if (!this.indirectChecked) {
            this.indirect = RenderSystem.getDevice().getDeviceInfo().features().drawIndirect();
            this.indirectChecked = true;
            GraphMachine.LOGGER.info("Graph renderer uses {} draws", this.indirect ? "indirect" : "direct");
        }
        if (this.buffers == null) {
            this.buffers = new GraphGpuBuffers();
        }
        if (this.atlas == null || this.atlasGeneration != assets.generation()) {
            if (this.atlas != null) {
                this.atlas.close();
            }
            this.atlas = GraphGlyphAtlas.build();
            this.atlasGeneration = assets.generation();
            this.requestedSettings = null;
        }

        this.pollBuild();
        GraphViewSettings settings = GraphSettingsStore.current();
        if (this.requestedSettings == null || !this.requestedSettings.sameView(settings) || this.requestedGeneration != assets.generation()) {
            this.startBuild(assets, settings);
        }
        this.pollBuild();
        if (this.buffers.uploading()) {
            this.buffers.uploadSome(UPLOAD_BUDGET_BYTES);
        }
        this.writeUniforms();
    }

    private void startBuild(GraphAssets assets, GraphViewSettings settings) {
        this.cancelBuild();
        GraphGlyphAtlas atlas = this.atlas;
        if (atlas == null) {
            return;
        }
        this.requestedSettings = settings;
        this.requestedGeneration = assets.generation();
        AtomicBoolean cancel = new AtomicBoolean();
        this.buildCancel = cancel;
        GraphViewBuilder builder = new GraphViewBuilder(assets, atlas, settings, GraphSettingsStore.version(), cancel);
        this.building = CompletableFuture.supplyAsync(builder::build, Util.backgroundExecutor());
    }

    private void cancelBuild() {
        if (this.buildCancel != null) {
            this.buildCancel.set(true);
        }
        this.building = null;
        this.buildCancel = null;
    }

    private void pollBuild() {
        CompletableFuture<GraphView> future = this.building;
        if (future == null || !future.isDone()) {
            return;
        }
        this.building = null;
        this.buildCancel = null;
        try {
            GraphView view = future.join();
            if (this.buffers != null) {
                this.buffers.beginUpload(view);
            }
        } catch (CancellationException ignored) {
        } catch (CompletionException e) {
            if (!(e.getCause() instanceof CancellationException)) {
                GraphMachine.LOGGER.error("Building the graph view failed", e.getCause());
            }
        }
    }

    private void writeUniforms() {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        if (this.uniforms == null) {
            this.uniforms = new MappableRingBuffer(() -> "Graph info", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        }
        this.uniforms.rotate();
        GpuBuffer buffer = this.uniforms.currentBuffer();
        ByteBuffer data = ByteBuffer.allocateDirect(UBO_SIZE).order(java.nio.ByteOrder.nativeOrder());
        Std140Builder.intoBuffer(data)
                .putVec4((float) camera.x, (float) camera.y, (float) camera.z, GraphViewBuilder.LABEL_SCALE)
                .putVec4(HOVER_MIX, HOVER_EDGE_WIDTH, LABEL_FADE_START, LABEL_FADE_END)
                .putVec4(PanelLayout.density(), 0.0F, 0.0F, 0.0F)
                .putIVec4(this.hoveredNode, this.atlas != null && this.atlas.singleChannel() ? 1 : 0, 0, 0);
        data.position(0).limit(UBO_SIZE);
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        this.uniformSlice = buffer.slice();
    }

    public void onOpaque(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        GraphGpuBuffers buffers = this.ready();
        if (buffers == null) {
            return;
        }
        this.pick(buffers, event.getLevelRenderState().cameraRenderState);
        RenderPass pass = event.getRenderPass();
        this.bind(pass, event.getModelViewMatrix());
        this.draw(pass, buffers, GraphGpuBuffers.Slot.NODES, GraphPipelines.NODES);

        GraphGlyphAtlas atlas = this.atlas;
        if (atlas == null || !atlas.ready()) {
            return;
        }
        pass.setUniform("Sampler0", atlas.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        this.draw(pass, buffers, GraphGpuBuffers.Slot.NAMES, GraphPipelines.LABELS);
        switch (GraphSettingsStore.current().packageMode()) {
            case SHOW -> this.draw(pass, buffers, GraphGpuBuffers.Slot.PACKAGES, GraphPipelines.LABELS);
            case HOVER -> this.drawHoveredPackage(pass, buffers);
            case HIDDEN -> {
            }
        }
    }

    /**
     * Draws the edges. They blend, but they draw at the end of the solid pass rather than in the
     * translucent pass, because the translucent stage event only fires with classic transparency and
     * the edges need no sorting against other translucent geometry.
     */
    public void onOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        GraphGpuBuffers buffers = this.ready();
        if (buffers == null) {
            return;
        }
        RenderPass pass = event.getRenderPass();
        this.bind(pass, event.getModelViewMatrix());
        this.draw(pass, buffers, GraphGpuBuffers.Slot.EDGES, GraphPipelines.EDGES);
    }

    private @Nullable GraphGpuBuffers ready() {
        GraphGpuBuffers buffers = this.buffers;
        if (buffers == null || buffers.frontView() == null || this.uniformSlice == null || !shouldDraw()) {
            return null;
        }
        return buffers;
    }

    private void bind(RenderPass pass, Matrix4fc modelView) {
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView)));
        pass.setUniform("GraphInfo", this.uniformSlice);
    }

    private void draw(RenderPass pass, GraphGpuBuffers buffers, GraphGpuBuffers.Slot slot, RenderPipeline pipeline) {
        int count = buffers.count(slot);
        GpuBufferSlice vertices = buffers.vertices(slot);
        if (count <= 0 || vertices == null) {
            return;
        }
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        pass.setVertexBuffer(0, vertices);
        if (this.indirect) {
            pass.drawIndirect(buffers.command(slot), 1);
        } else {
            pass.draw(slot.verticesPerInstance, count, 0, 0);
        }
    }

    private void drawHoveredPackage(RenderPass pass, GraphGpuBuffers buffers) {
        GraphView view = buffers.frontView();
        if (view == null || this.hoveredNode < 0 || this.hoveredNode >= view.nodeCount()) {
            return;
        }
        int count = view.packageGlyphCounts()[this.hoveredNode];
        GpuBufferSlice vertices = buffers.vertices(GraphGpuBuffers.Slot.PACKAGES, view.packageGlyphStart()[this.hoveredNode], count, LABEL_STRIDE);
        if (vertices == null) {
            return;
        }
        pass.setPipeline(RenderSystem.getCompiledPipeline(GraphPipelines.LABELS));
        pass.setVertexBuffer(0, vertices);
        pass.draw(GraphPipelines.QUAD_VERTICES, count, 0, 0);
    }

    /** Updates {@link #hoveredNode} from the crosshair, or from the cursor while a screen is open. */
    private void pick(GraphGpuBuffers buffers, CameraRenderState camera) {
        GraphView view = buffers.frontView();
        if (view == null) {
            this.hoveredNode = -1;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() == null) {
            this.direction.set(0.0F, 0.0F, -1.0F).rotate(camera.orientation);
        } else {
            float density = PanelLayout.density();
            float mouseX = (float) minecraft.mouseHandler.xpos() * density;
            float mouseY = (float) minecraft.mouseHandler.ypos() * density;
            int width = minecraft.getWindow().getWidth();
            int height = minecraft.getWindow().getHeight();
            if (mouseX < 0 || mouseY < 0 || mouseX >= width || mouseY >= height) {
                this.hoveredNode = -1;
                return;
            }
            float ndcX = mouseX / width * 2.0F - 1.0F;
            float ndcY = 1.0F - mouseY / height * 2.0F;
            Matrix4f projection = camera.projectionMatrix;
            this.direction.set(ndcX / projection.m00(), ndcY / projection.m11(), -1.0F).rotate(camera.orientation).normalize();
        }
        this.hoveredNode = GraphPicker.pick(view.nodeBoxes(), view.nodeCount(), camera.pos.x, camera.pos.y, camera.pos.z, this.direction);
    }

    /** Frees the GPU resources; call when the client shuts down. */
    public void close() {
        this.cancelBuild();
        if (this.buffers != null) {
            this.buffers.close();
            this.buffers = null;
        }
        if (this.atlas != null) {
            this.atlas.close();
            this.atlas = null;
        }
        if (this.uniforms != null) {
            this.uniforms.close();
            this.uniforms = null;
        }
    }
}

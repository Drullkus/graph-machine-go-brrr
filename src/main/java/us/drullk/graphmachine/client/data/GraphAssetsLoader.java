package us.drullk.graphmachine.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;
import us.drullk.graphmachine.GraphMachine;
import us.drullk.graphmachine.graph.GraphClusters;
import us.drullk.graphmachine.graph.GraphData;
import us.drullk.graphmachine.graph.GraphEdges;
import us.drullk.graphmachine.graph.GraphFiles;

/**
 * Loads the three graph assets when the client resources load, and keeps the last good set.
 *
 * <p>The renderer reads {@link #current()} from the render thread; the field is volatile and the record
 * is immutable, so a reload in progress never shows a half-loaded set.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphAssetsLoader extends SimplePreparableReloadListener<Optional<GraphAssets>> {
    /** The reload listener id. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(GraphMachine.MODID, "graph_assets");

    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static volatile @Nullable GraphAssets current;

    /** The assets of the last successful load, or null when none loaded. */
    public static @Nullable GraphAssets current() {
        return current;
    }

    @Override
    protected Optional<GraphAssets> prepare(ResourceManager manager, ProfilerFiller profiler) {
        try {
            GraphData nodes = readJson(manager, GraphFiles.NODES, GraphData.CODEC);
            GraphClusters clusters = readJson(manager, GraphFiles.CLUSTERS, GraphClusters.CODEC);
            GraphEdges edges = readEdges(manager, nodes.nodes().size());
            return Optional.of(new GraphAssets(nodes, edges, clusters, GENERATION.incrementAndGet()));
        } catch (IOException | RuntimeException e) {
            GraphMachine.LOGGER.error("Failed to load the graph assets; the graph is not drawn", e);
            return Optional.empty();
        }
    }

    @Override
    protected void apply(Optional<GraphAssets> prepared, ResourceManager manager, ProfilerFiller profiler) {
        prepared.ifPresent(assets -> {
            current = assets;
            GraphMachine.LOGGER.info("Loaded {} graph nodes, {} edges and {} clusters", assets.nodeCount(), assets.edges().count(), assets.clusters().clusters().size());
        });
    }

    private static <T> T readJson(ResourceManager manager, Identifier id, Codec<T> codec) throws IOException {
        Resource resource = manager.getResourceOrThrow(id);
        try (Reader reader = resource.openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            return codec.parse(JsonOps.INSTANCE, json).getOrThrow(message -> new IOException("Cannot parse " + id + ": " + message));
        }
    }

    private static GraphEdges readEdges(ResourceManager manager, int nodeCount) throws IOException {
        Resource resource = manager.getResourceOrThrow(GraphFiles.EDGES);
        byte[] bytes;
        try (InputStream in = resource.open()) {
            bytes = in.readAllBytes();
        }
        if (bytes.length % GraphEdges.BYTES_PER_EDGE != 0) {
            throw new IOException(GraphFiles.EDGES + " has " + bytes.length + " bytes, not a multiple of " + GraphEdges.BYTES_PER_EDGE);
        }
        GraphEdges edges = GraphEdges.read(ByteBuffer.wrap(bytes));
        for (int i = 0; i < edges.count(); i++) {
            if (edges.nodeA(i) < 0 || edges.nodeA(i) >= nodeCount || edges.nodeB(i) < 0 || edges.nodeB(i) >= nodeCount) {
                throw new IOException(GraphFiles.EDGES + " edge " + i + " points outside the " + nodeCount + " nodes");
            }
        }
        return edges;
    }
}

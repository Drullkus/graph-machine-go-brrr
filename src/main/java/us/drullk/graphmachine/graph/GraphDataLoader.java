package us.drullk.graphmachine.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;
import us.drullk.graphmachine.GraphMachine;

/**
 * Loads {@code data/graphmachine/graph/nodes.json} when the server data packs load.
 *
 * <p>The result is kept in a static field, because the chunk generator has no other path to the
 * resource manager while it generates chunks.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 */
public final class GraphDataLoader extends SimplePreparableReloadListener<Optional<GraphData>> {
    /** The reload listener id. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(GraphMachine.MODID, "graph_data");
    /** The data pack file that holds the nodes. */
    public static final Identifier FILE = Identifier.fromNamespaceAndPath(GraphMachine.MODID, "graph/nodes.json");

    private static volatile @Nullable GraphData current;

    /** The graph data of the last successful load, or null when none loaded. */
    public static @Nullable GraphData current() {
        return current;
    }

    @Override
    protected Optional<GraphData> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            GraphMachine.LOGGER.warn("Graph data file {} is missing; the graph world preset will generate no nodes", FILE);
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            return Optional.of(GraphData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        } catch (IOException | RuntimeException e) {
            GraphMachine.LOGGER.error("Failed to read graph data file {}", FILE, e);
            return Optional.empty();
        }
    }

    @Override
    protected void apply(Optional<GraphData> prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared.orElse(null);
        prepared.ifPresent(data -> GraphMachine.LOGGER.info("Loaded {} graph nodes", data.nodes().size()));
    }
}

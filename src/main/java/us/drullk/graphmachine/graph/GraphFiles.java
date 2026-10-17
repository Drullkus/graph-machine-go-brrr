package us.drullk.graphmachine.graph;

import net.minecraft.resources.Identifier;
import us.drullk.graphmachine.GraphMachine;

/**
 * Resource locations of the graph assets. Datagen writes them, the client reads them.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphFiles {
    /** {@code assets/graphmachine/graph/nodes.json}, a {@link GraphData}. */
    public static final Identifier NODES = Identifier.fromNamespaceAndPath(GraphMachine.MODID, "graph/nodes.json");
    /** {@code assets/graphmachine/graph/edges.bin}, a {@link GraphEdges}. */
    public static final Identifier EDGES = Identifier.fromNamespaceAndPath(GraphMachine.MODID, "graph/edges.bin");
    /** {@code assets/graphmachine/graph/clusters.json}, a {@link GraphClusters}. */
    public static final Identifier CLUSTERS = Identifier.fromNamespaceAndPath(GraphMachine.MODID, "graph/clusters.json");

    private GraphFiles() {
    }
}

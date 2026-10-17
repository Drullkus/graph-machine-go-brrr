package us.drullk.graphmachine.client.data;

import us.drullk.graphmachine.graph.GraphClusters;
import us.drullk.graphmachine.graph.GraphData;
import us.drullk.graphmachine.graph.GraphEdges;

/**
 * One consistent set of loaded graph assets. {@code generation} increases with every successful reload,
 * so caches built from an older set can tell they are stale.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public record GraphAssets(GraphData nodes, GraphEdges edges, GraphClusters clusters, int generation) {
    public int nodeCount() {
        return this.nodes.nodes().size();
    }
}

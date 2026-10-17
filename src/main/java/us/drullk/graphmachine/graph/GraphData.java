package us.drullk.graphmachine.graph;

import com.mojang.serialization.Codec;
import java.util.List;

/**
 * All graph nodes in the order of the asset file.
 *
 * <p>The file format is {@code {"nodes": [ ... ]}} with one {@link GraphNode} object per entry. The
 * index of a node in {@link #nodes()} is the node id that {@link GraphEdges} refers to.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public record GraphData(List<GraphNode> nodes) {
    public static final Codec<GraphData> CODEC = GraphNode.CODEC.listOf().fieldOf("nodes").codec().xmap(GraphData::new, GraphData::nodes);

    public GraphData {
        nodes = List.copyOf(nodes);
    }
}

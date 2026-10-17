package us.drullk.graphmachine.graph;

import com.mojang.serialization.Codec;
import java.util.List;

/**
 * All clusters, sorted by id. The file format is {@code {"clusters": [ ... ]}}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public record GraphClusters(List<GraphCluster> clusters) {
    public static final Codec<GraphClusters> CODEC = GraphCluster.CODEC.listOf().fieldOf("clusters").codec().xmap(GraphClusters::new, GraphClusters::clusters);

    /** Opaque ARGB color of the classes that belong to no cluster. */
    public static final int UNCLUSTERED_COLOR = 0xFF9A9A9A;

    public GraphClusters {
        clusters = List.copyOf(clusters);
    }

    /** The color of a cluster id, or {@link #UNCLUSTERED_COLOR} when the id is unknown or negative. */
    public int color(int cluster) {
        for (GraphCluster entry : this.clusters) {
            if (entry.id() == cluster) {
                return entry.color();
            }
        }
        return UNCLUSTERED_COLOR;
    }
}

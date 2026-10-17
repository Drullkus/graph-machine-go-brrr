package us.drullk.graphmachine.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks the cluster label and color lookup.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
class GraphClusterTest {
    @Test
    void labelNamesTheTopPackageAndSize() {
        GraphCluster cluster = new GraphCluster(1, 188, 0xFF112233, List.of(new GraphCluster.PackageShare("net.minecraft.client.renderer", 0.84F), new GraphCluster.PackageShare("net.minecraft.client.model", 0.05F)));
        assertEquals("Cluster 1 - net.minecraft.client.renderer (188)", cluster.label());
        assertEquals("Cluster 7 - mixed (3)", new GraphCluster(7, 3, 0, List.of()).label());
    }

    @Test
    void unknownClusterGetsTheNeutralColor() {
        GraphClusters clusters = new GraphClusters(List.of(new GraphCluster(2, 1, 0xFF445566, List.of())));
        assertEquals(0xFF445566, clusters.color(2));
        assertEquals(GraphClusters.UNCLUSTERED_COLOR, clusters.color(-1));
        assertEquals(GraphClusters.UNCLUSTERED_COLOR, clusters.color(5));
    }
}

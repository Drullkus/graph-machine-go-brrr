package us.drullk.graphmachine.graph;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

/**
 * Checks the binary edge format round trip.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
class GraphEdgesTest {
    @Test
    void roundTripKeepsPairsAndOrder() {
        GraphEdges edges = new GraphEdges(new int[] {0, 1, 1, 2, 2, 0});
        byte[] bytes = edges.write();
        assertEquals(3 * GraphEdges.BYTES_PER_EDGE, bytes.length);

        GraphEdges read = GraphEdges.read(ByteBuffer.wrap(bytes));
        assertEquals(3, read.count());
        assertArrayEquals(edges.pairs(), read.pairs());
        assertEquals(2, read.nodeA(2));
        assertEquals(0, read.nodeB(2));
    }

    @Test
    void fileIsLittleEndian() {
        byte[] bytes = new GraphEdges(new int[] {1, 256}).write();
        assertArrayEquals(new byte[] {1, 0, 0, 0, 0, 1, 0, 0}, bytes);
    }

    @Test
    void oddLengthIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GraphEdges(new int[] {1}));
    }
}

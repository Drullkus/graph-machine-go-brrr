package us.drullk.graphmachine.graph;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The class edges as pairs of node ids, packed in one int array: edge {@code i} connects
 * {@code nodeA(i)} and {@code nodeB(i)}.
 *
 * <p>The asset file {@code edges.bin} holds one little-endian int32 pair per edge and nothing else. Node
 * ids are indices into {@link GraphData#nodes()}. Pairs are sorted by first id, then second id.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public record GraphEdges(int[] pairs) {
    /** Bytes per edge in the binary file. */
    public static final int BYTES_PER_EDGE = Integer.BYTES * 2;

    public GraphEdges {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Edge array has an odd length: " + pairs.length);
        }
    }

    public int count() {
        return this.pairs.length / 2;
    }

    public int nodeA(int edge) {
        return this.pairs[edge * 2];
    }

    public int nodeB(int edge) {
        return this.pairs[edge * 2 + 1];
    }

    /** Reads the binary file format. */
    public static GraphEdges read(ByteBuffer bytes) {
        ByteBuffer buffer = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int[] pairs = new int[buffer.remaining() / Integer.BYTES];
        buffer.asIntBuffer().get(pairs);
        return new GraphEdges(pairs);
    }

    /** Writes the binary file format. */
    public byte[] write() {
        ByteBuffer buffer = ByteBuffer.allocate(this.pairs.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asIntBuffer().put(this.pairs);
        return buffer.array();
    }
}

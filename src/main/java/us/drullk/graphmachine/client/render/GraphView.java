package us.drullk.graphmachine.client.render;

import java.nio.ByteBuffer;

/**
 * The compacted instance data of one settings state, ready for upload.
 *
 * <p>Node, glyph and edge instances are packed in the layouts of {@link GraphPipelines}. Every node
 * index inside the buffers is a compacted index (0 to {@code nodeCount - 1}); {@code nodeIds} maps it
 * back to the index in the asset's node list. {@code nodeBoxes} repeats position and scale as floats
 * for the picker. {@code packageGlyphStart} and {@code packageGlyphCounts} give the package glyph range
 * of each compacted node, so the "on mouseover" mode can draw one node's package line.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public record GraphView(
        int settingsVersion,
        int assetsGeneration,
        ByteBuffer nodes,
        int nodeCount,
        float[] nodeBoxes,
        int[] nodeIds,
        ByteBuffer nameGlyphs,
        int nameGlyphCount,
        ByteBuffer packageGlyphs,
        int packageGlyphCount,
        int[] packageGlyphStart,
        int[] packageGlyphCounts,
        ByteBuffer edges,
        int edgeCount
) {
    /** Total bytes the upload has to move. */
    public long uploadBytes() {
        return (long) this.nodes.remaining() + this.nameGlyphs.remaining() + this.packageGlyphs.remaining() + this.edges.remaining();
    }
}

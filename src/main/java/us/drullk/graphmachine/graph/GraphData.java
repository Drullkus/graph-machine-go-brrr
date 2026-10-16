package us.drullk.graphmachine.graph;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

/**
 * All graph nodes, indexed by the chunk their x and z coordinates fall into.
 *
 * <p>The file format is {@code {"nodes": [ ... ]}} with one {@link GraphNode} object per entry.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 */
public final class GraphData {
    public static final Codec<GraphData> CODEC = GraphNode.CODEC.listOf().fieldOf("nodes").codec().xmap(GraphData::new, GraphData::nodes);

    private final List<GraphNode> nodes;
    private final Long2ObjectMap<List<GraphNode>> nodesByChunk = new Long2ObjectOpenHashMap<>();

    public GraphData(List<GraphNode> nodes) {
        this.nodes = List.copyOf(nodes);
        for (GraphNode node : this.nodes) {
            this.nodesByChunk.computeIfAbsent(chunkKey(node.x(), node.z()), ignored -> new ArrayList<>()).add(node);
        }
    }

    public List<GraphNode> nodes() {
        return this.nodes;
    }

    /** The nodes whose x and z coordinates fall into the given chunk. */
    public List<GraphNode> nodesInChunk(ChunkPos pos) {
        return this.nodesByChunk.getOrDefault(pos.pack(), List.of());
    }

    private static long chunkKey(float x, float z) {
        return ChunkPos.pack(SectionPos.blockToSectionCoord(Mth.floor(x)), SectionPos.blockToSectionCoord(Mth.floor(z)));
    }
}

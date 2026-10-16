package us.drullk.graphmachine.graph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/**
 * One top-level Minecraft class and its place in the world.
 *
 * <p>The position is in block units, as written by {@code tools/layout_graph.py}. The chunk generator
 * adds its own vertical offset when it places the node. {@code cluster} is the k-means cluster id, or
 * {@code -1} for a class with no edges. {@code degree} is the number of classes this class references or
 * is referenced by.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 */
public record GraphNode(String className, float x, float y, float z, int cluster, Side side, int degree) {
    public static final Codec<GraphNode> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("class").forGetter(GraphNode::className),
            Codec.FLOAT.fieldOf("x").forGetter(GraphNode::x),
            Codec.FLOAT.fieldOf("y").forGetter(GraphNode::y),
            Codec.FLOAT.fieldOf("z").forGetter(GraphNode::z),
            Codec.INT.fieldOf("cluster").forGetter(GraphNode::cluster),
            Side.CODEC.fieldOf("dist").forGetter(GraphNode::side),
            Codec.INT.fieldOf("degree").forGetter(GraphNode::degree)
    ).apply(i, GraphNode::new));

    /** The class name without its package. */
    public String simpleName() {
        int dot = this.className.lastIndexOf('.');
        return dot < 0 ? this.className : this.className.substring(dot + 1);
    }

    /** The package name, or an empty string for the default package. */
    public String packageName() {
        int dot = this.className.lastIndexOf('.');
        return dot < 0 ? "" : this.className.substring(0, dot);
    }

    /** Which distribution ships the class, as tagged in the jar manifest. */
    public enum Side implements StringRepresentable {
        /** Present only in the client jar. */
        CLIENT("client"),
        /** Present in both the client and the server. */
        COMMON("common");

        public static final Codec<Side> CODEC = StringRepresentable.fromEnum(Side::values);

        private final String name;

        Side(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}

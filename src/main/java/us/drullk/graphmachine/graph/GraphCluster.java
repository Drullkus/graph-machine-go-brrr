package us.drullk.graphmachine.graph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * One k-means cluster: its id, how many classes it holds, its display color and the packages that make
 * up most of it.
 *
 * <p>{@code color} is opaque ARGB. The renderer and the panel both read it, so nodes and checkboxes
 * agree. {@code topPackages} is ordered by share, largest first.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public record GraphCluster(int id, int size, int color, List<PackageShare> topPackages) {
    public static final Codec<GraphCluster> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(GraphCluster::id),
            Codec.INT.fieldOf("size").forGetter(GraphCluster::size),
            Codec.INT.fieldOf("color").forGetter(GraphCluster::color),
            PackageShare.CODEC.listOf().fieldOf("top_packages").forGetter(GraphCluster::topPackages)
    ).apply(i, GraphCluster::new));

    public GraphCluster {
        topPackages = List.copyOf(topPackages);
    }

    /** The label the panel shows: {@code Cluster 3 - net.minecraft.client.renderer (188)}. */
    public String label() {
        String top = this.topPackages.isEmpty() ? "mixed" : this.topPackages.getFirst().name();
        return "Cluster " + this.id + " - " + top + " (" + this.size + ")";
    }

    /** A package and the fraction (0 to 1) of the cluster's classes that live in it. */
    public record PackageShare(String name, float share) {
        public static final Codec<PackageShare> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("package").forGetter(PackageShare::name),
                Codec.FLOAT.fieldOf("share").forGetter(PackageShare::share)
        ).apply(i, PackageShare::new));
    }
}

package us.drullk.graphmachine.datagen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import us.drullk.graphmachine.graph.GraphCluster;
import us.drullk.graphmachine.graph.GraphClusters;
import us.drullk.graphmachine.graph.GraphFiles;

/**
 * Converts {@code graph-data/clusters.csv} into the client asset {@code assets/graphmachine/graph/clusters.json}.
 *
 * <p>The CSV columns are {@code cluster,size,package_1,share_1,package_2,share_2,package_3,share_3}.
 * Each cluster gets a color from a fixed palette: the hue steps by the golden angle, and the lightness
 * alternates between two bands so neighbors in id order stay apart.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphClustersProvider implements DataProvider {
    private final PackOutput output;

    public GraphClustersProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        GraphClusters clusters = read(GraphDataProvider.inputDir().resolve("clusters.csv"));
        return DataProvider.saveStable(cache, GraphClusters.CODEC, clusters, GraphDataProvider.assetPath(this.output, GraphFiles.CLUSTERS));
    }

    @Override
    public String getName() {
        return "Graph clusters";
    }

    static GraphClusters read(Path csv) {
        List<GraphCluster> clusters = new ArrayList<>();
        for (String[] row : GraphDataProvider.rows(csv)) {
            int id = Integer.parseInt(row[0]);
            int size = Integer.parseInt(row[1]);
            List<GraphCluster.PackageShare> top = new ArrayList<>();
            for (int i = 2; i + 1 < row.length; i += 2) {
                if (!row[i].isBlank()) {
                    top.add(new GraphCluster.PackageShare(row[i], Float.parseFloat(row[i + 1])));
                }
            }
            clusters.add(new GraphCluster(id, size, color(id), top));
        }
        clusters.sort((a, b) -> Integer.compare(a.id(), b.id()));
        return new GraphClusters(clusters);
    }

    /** Opaque ARGB color for a cluster id. */
    static int color(int id) {
        float hue = (float) ((id * 0.618033988749895) % 1.0);
        float lightness = id % 2 == 0 ? 0.55F : 0.68F;
        return hsl(hue, 0.65F, lightness);
    }

    private static int hsl(float h, float s, float l) {
        float c = (1.0F - Math.abs(2.0F * l - 1.0F)) * s;
        float hp = h * 6.0F;
        float x = c * (1.0F - Math.abs(hp % 2.0F - 1.0F));
        float r, g, b;
        if (hp < 1) { r = c; g = x; b = 0; }
        else if (hp < 2) { r = x; g = c; b = 0; }
        else if (hp < 3) { r = 0; g = c; b = x; }
        else if (hp < 4) { r = 0; g = x; b = c; }
        else if (hp < 5) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        float m = l - c / 2.0F;
        return ARGB.color(255, Mth.floor((r + m) * 255.0F + 0.5F), Mth.floor((g + m) * 255.0F + 0.5F), Mth.floor((b + m) * 255.0F + 0.5F));
    }
}

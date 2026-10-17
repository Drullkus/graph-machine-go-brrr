package us.drullk.graphmachine.datagen;

import com.google.common.hash.Hashing;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import us.drullk.graphmachine.GraphMachine;
import us.drullk.graphmachine.graph.GraphData;
import us.drullk.graphmachine.graph.GraphEdges;
import us.drullk.graphmachine.graph.GraphFiles;
import us.drullk.graphmachine.graph.GraphNode;

/**
 * Converts {@code jdeps-output/class-edges.tsv} into the client asset {@code assets/graphmachine/graph/edges.bin}.
 *
 * <p>The folder with the TSV file comes from the system property {@code graphmachine.jdepsOutput}. Each
 * line is {@code from<TAB>to}. Nested classes are collapsed into their outer class, self edges and classes
 * that are not in the node list are dropped, and a pair of classes is kept once in the direction it is
 * first seen. Node ids are the indices of {@link GraphDataProvider}'s sorted node list.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphEdgesProvider implements DataProvider {
    public static final String INPUT_PROPERTY = "graphmachine.jdepsOutput";

    private final PackOutput output;
    private final GraphDataProvider nodes;

    public GraphEdgesProvider(PackOutput output, GraphDataProvider nodes) {
        this.output = output;
        this.nodes = nodes;
    }

    @Override
    @SuppressWarnings("deprecation") // The data cache keys files by SHA-1, like vanilla's DataProvider.saveStable.
    public CompletableFuture<?> run(CachedOutput cache) {
        Path tsv = Path.of(System.getProperty(INPUT_PROPERTY, "jdeps-output")).resolve("class-edges.tsv");
        GraphEdges edges = read(tsv, this.nodes.read());
        byte[] bytes = edges.write();
        return CompletableFuture.runAsync(() -> {
            try {
                cache.writeIfNeeded(GraphDataProvider.assetPath(this.output, GraphFiles.EDGES), bytes, Hashing.sha1().hashBytes(bytes));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write " + GraphFiles.EDGES, e);
            }
        });
    }

    @Override
    public String getName() {
        return "Graph edges";
    }

    /** Reads the TSV file and resolves class names against {@code data}. */
    static GraphEdges read(Path tsv, GraphData data) {
        List<String> lines;
        try {
            lines = Files.readAllLines(tsv);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + tsv, e);
        }
        return fromLines(lines, data);
    }

    /** Builds the edge list from {@code from<TAB>to} lines. */
    static GraphEdges fromLines(List<String> lines, GraphData data) {
        Map<String, Integer> ids = new HashMap<>();
        List<GraphNode> nodes = data.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            ids.put(nodes.get(i).className(), i);
        }
        LongSet seen = new LongOpenHashSet();
        long[] pairs = new long[lines.size()];
        int count = 0;
        int unknown = 0;
        for (String line : lines) {
            int tab = line.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            Integer from = ids.get(outerClass(line.substring(0, tab)));
            Integer to = ids.get(outerClass(line.substring(tab + 1).strip()));
            if (from == null || to == null) {
                unknown++;
                continue;
            }
            if (from.intValue() == to.intValue()) {
                continue;
            }
            long key = pack(Math.min(from, to), Math.max(from, to));
            if (seen.add(key)) {
                pairs[count++] = pack(from, to);
            }
        }
        if (unknown > 0) {
            GraphMachine.LOGGER.warn("{} edge lines name a class that is not in the node list; skipped", unknown);
        }
        long[] kept = Arrays.copyOf(pairs, count);
        Arrays.sort(kept);
        int[] flat = new int[count * 2];
        for (int i = 0; i < count; i++) {
            flat[i * 2] = (int) (kept[i] >>> 32);
            flat[i * 2 + 1] = (int) kept[i];
        }
        return new GraphEdges(flat);
    }

    /** {@code a.b.Outer$Inner} becomes {@code a.b.Outer}. */
    static String outerClass(String className) {
        int dollar = className.indexOf('$');
        return dollar < 0 ? className : className.substring(0, dollar);
    }

    private static long pack(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }
}

package us.drullk.graphmachine.datagen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import us.drullk.graphmachine.GraphMachine;
import us.drullk.graphmachine.graph.GraphData;
import us.drullk.graphmachine.graph.GraphFiles;
import us.drullk.graphmachine.graph.GraphNode;

/**
 * Converts {@code graph-data/nodes.csv} and {@code graph-data/layout.csv} into the client asset
 * {@code assets/graphmachine/graph/nodes.json}.
 *
 * <p>The folder with the CSV files comes from the system property {@code graphmachine.graphData}, which
 * the {@code data} run sets in {@code build.gradle}. The layout positions are multiplied by the two
 * scalars and {@code yOffset} is added, so the file holds final world positions. A class without a
 * layout row is skipped with a warning. Nodes are sorted by class name; their index is the node id that
 * {@link GraphEdgesProvider} writes.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphDataProvider implements DataProvider {
    public static final String INPUT_PROPERTY = "graphmachine.graphData";

    private final PackOutput output;
    private final float posScalarHorizontal;
    private final float posScalarVertical;
    private final float yOffset;

    public GraphDataProvider(PackOutput output, float posScalarHorizontal, float posScalarVertical, float yOffset) {
        this.output = output;
        this.posScalarHorizontal = posScalarHorizontal;
        this.posScalarVertical = posScalarVertical;
        this.yOffset = yOffset;
    }

    /** The folder the CSV files are read from. */
    static Path inputDir() {
        return Path.of(System.getProperty(INPUT_PROPERTY, "graph-data"));
    }

    /** The output path of an asset id inside the resource pack. */
    static Path assetPath(PackOutput output, Identifier id) {
        return output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(id.getNamespace()).resolve(id.getPath());
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        GraphData data = this.read();
        return DataProvider.saveStable(cache, GraphData.CODEC, data, assetPath(this.output, GraphFiles.NODES));
    }

    /** Reads and joins the CSV files with this provider's scalars. */
    GraphData read() {
        Path inputDir = inputDir();
        return read(inputDir.resolve("nodes.csv"), inputDir.resolve("layout.csv"), this.posScalarHorizontal, this.posScalarVertical, this.yOffset);
    }

    @Override
    public String getName() {
        return "Graph nodes";
    }

    /** Reads both CSV files and joins them on the class name. */
    static GraphData read(Path nodesCsv, Path layoutCsv, float posScalarHorizontal, float posScalarVertical, float yOffset) {
        Map<String, float[]> positions = new HashMap<>();
        for (String[] row : rows(layoutCsv)) {
            positions.put(row[0], new float[] {Float.parseFloat(row[1]), Float.parseFloat(row[2]), Float.parseFloat(row[3])});
        }
        List<GraphNode> nodes = new ArrayList<>();
        for (String[] row : rows(nodesCsv)) {
            String className = row[0];
            float[] position = positions.get(className);
            if (position == null) {
                GraphMachine.LOGGER.warn("Class {} has no row in {}; skipped", className, layoutCsv);
                continue;
            }
            GraphNode.Side side = row[1].equals("client") ? GraphNode.Side.CLIENT : GraphNode.Side.COMMON;
            nodes.add(new GraphNode(
                    className,
                    position[0] * posScalarHorizontal,
                    position[1] * posScalarVertical + yOffset,
                    position[2] * posScalarHorizontal,
                    Integer.parseInt(row[4]),
                    side,
                    Integer.parseInt(row[2])
            ));
        }
        nodes.sort(Comparator.comparing(GraphNode::className));
        return new GraphData(nodes);
    }

    /** The data rows of a CSV file without quoting, header line dropped. */
    static List<String[]> rows(Path csv) {
        try {
            List<String> lines = Files.readAllLines(csv);
            List<String[]> rows = new ArrayList<>(lines.size());
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.isBlank()) {
                    rows.add(line.split(","));
                }
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + csv, e);
        }
    }
}

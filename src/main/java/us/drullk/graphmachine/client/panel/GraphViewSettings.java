package us.drullk.graphmachine.client.panel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.List;
import net.minecraft.util.StringRepresentable;
import us.drullk.graphmachine.graph.GraphFilter;

/**
 * What the panel controls: which nodes show, how labels and edges are drawn, and the panel's own size.
 *
 * <p>This record is immutable; the panel replaces it through {@link GraphSettingsStore}. The first five
 * fields change the drawn graph and start a rebuild, the last two only change the panel.
 * {@code includeAdjacent} keeps every node that shares an edge with a filter match, so a filter for one
 * class shows that class and all of its connections.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public record GraphViewSettings(String filter, boolean includeAdjacent, PackageMode packageMode, EdgeMode edgeMode, IntSet hiddenClusters, int panelWidthPt, boolean panelVisible) {
    public static final int MIN_PANEL_WIDTH_PT = 240;
    public static final int MAX_PANEL_WIDTH_PT = 600;
    public static final int DEFAULT_PANEL_WIDTH_PT = 360;

    public static final GraphViewSettings DEFAULT = new GraphViewSettings("", false, PackageMode.SHOW, EdgeMode.CLUSTERS, IntSets.emptySet(), DEFAULT_PANEL_WIDTH_PT, true);

    public static final Codec<GraphViewSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("filter", "").forGetter(GraphViewSettings::filter),
            Codec.BOOL.optionalFieldOf("include_adjacent", false).forGetter(GraphViewSettings::includeAdjacent),
            PackageMode.CODEC.optionalFieldOf("package_labels", PackageMode.SHOW).forGetter(GraphViewSettings::packageMode),
            EdgeMode.CODEC.optionalFieldOf("edges", EdgeMode.CLUSTERS).forGetter(GraphViewSettings::edgeMode),
            Codec.INT.listOf().optionalFieldOf("hidden_clusters", List.of()).xmap(list -> (IntSet) new IntOpenHashSet(list), set -> set.intStream().sorted().boxed().toList()).forGetter(GraphViewSettings::hiddenClusters),
            Codec.INT.optionalFieldOf("panel_width", DEFAULT_PANEL_WIDTH_PT).forGetter(GraphViewSettings::panelWidthPt),
            Codec.BOOL.optionalFieldOf("panel_visible", true).forGetter(GraphViewSettings::panelVisible)
    ).apply(i, GraphViewSettings::new));

    public GraphViewSettings {
        hiddenClusters = IntSets.unmodifiable(new IntOpenHashSet(hiddenClusters));
        panelWidthPt = Math.clamp(panelWidthPt, MIN_PANEL_WIDTH_PT, MAX_PANEL_WIDTH_PT);
    }

    public GraphViewSettings withFilter(String filter) {
        return new GraphViewSettings(filter, this.includeAdjacent, this.packageMode, this.edgeMode, this.hiddenClusters, this.panelWidthPt, this.panelVisible);
    }

    public GraphViewSettings withIncludeAdjacent(boolean include) {
        return new GraphViewSettings(this.filter, include, this.packageMode, this.edgeMode, this.hiddenClusters, this.panelWidthPt, this.panelVisible);
    }

    public GraphViewSettings withPackageMode(PackageMode mode) {
        return new GraphViewSettings(this.filter, this.includeAdjacent, mode, this.edgeMode, this.hiddenClusters, this.panelWidthPt, this.panelVisible);
    }

    public GraphViewSettings withEdgeMode(EdgeMode mode) {
        return new GraphViewSettings(this.filter, this.includeAdjacent, this.packageMode, mode, this.hiddenClusters, this.panelWidthPt, this.panelVisible);
    }

    public GraphViewSettings withHiddenClusters(IntSet hidden) {
        return new GraphViewSettings(this.filter, this.includeAdjacent, this.packageMode, this.edgeMode, hidden, this.panelWidthPt, this.panelVisible);
    }

    public GraphViewSettings withClusterHidden(int cluster, boolean hidden) {
        IntSet set = new IntOpenHashSet(this.hiddenClusters);
        if (hidden) {
            set.add(cluster);
        } else {
            set.remove(cluster);
        }
        return this.withHiddenClusters(set);
    }

    public GraphViewSettings withPanelWidthPt(int widthPt) {
        return new GraphViewSettings(this.filter, this.includeAdjacent, this.packageMode, this.edgeMode, this.hiddenClusters, widthPt, this.panelVisible);
    }

    public GraphViewSettings withPanelVisible(boolean visible) {
        return new GraphViewSettings(this.filter, this.includeAdjacent, this.packageMode, this.edgeMode, this.hiddenClusters, this.panelWidthPt, visible);
    }

    /** True when the two settings draw the same graph, so no rebuild is needed between them. */
    public boolean sameView(GraphViewSettings other) {
        return this.filter.equals(other.filter) && this.includeAdjacent == other.includeAdjacent && this.packageMode == other.packageMode && this.edgeMode == other.edgeMode && this.hiddenClusters.equals(other.hiddenClusters);
    }

    /** The lower-case terms of the filter; a node must contain all of them. */
    public List<String> filterTerms() {
        return GraphFilter.terms(this.filter);
    }

    /** How the package line of a label is shown. */
    public enum PackageMode implements StringRepresentable {
        SHOW("show", "Show"),
        HOVER("hover", "On mouseover"),
        HIDDEN("hidden", "Hidden");

        public static final Codec<PackageMode> CODEC = StringRepresentable.fromEnum(PackageMode::values);
        public static final String[] LABELS = java.util.Arrays.stream(values()).map(PackageMode::label).toArray(String[]::new);

        private final String name;
        private final String label;

        PackageMode(String name, String label) {
            this.name = name;
            this.label = label;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public String label() {
            return this.label;
        }
    }

    /** Which edges are drawn. */
    public enum EdgeMode implements StringRepresentable {
        CLUSTERS("clusters", "Cluster to cluster"),
        NODES("nodes", "Node to node"),
        HIDDEN("hidden", "Hidden");

        public static final Codec<EdgeMode> CODEC = StringRepresentable.fromEnum(EdgeMode::values);
        public static final String[] LABELS = java.util.Arrays.stream(values()).map(EdgeMode::label).toArray(String[]::new);

        private final String name;
        private final String label;

        EdgeMode(String name, String label) {
            this.name = name;
            this.label = label;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public String label() {
            return this.label;
        }
    }
}

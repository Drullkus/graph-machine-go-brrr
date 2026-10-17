package us.drullk.graphmachine.client.render;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import us.drullk.graphmachine.client.data.GraphAssets;
import us.drullk.graphmachine.client.panel.GraphViewSettings;
import us.drullk.graphmachine.graph.GraphCluster;
import us.drullk.graphmachine.graph.GraphClusters;
import us.drullk.graphmachine.graph.GraphEdges;
import us.drullk.graphmachine.graph.GraphFilter;
import us.drullk.graphmachine.graph.GraphNode;

/**
 * Turns the assets plus the panel settings into a {@link GraphView}. Runs on a worker thread; it only
 * reads immutable data and writes into fresh direct buffers.
 *
 * <p>A node is visible when its cluster is not hidden and its full class name contains every filter
 * term; the adjacent-nodes setting can add the neighbors of those matches and limit the edges to the
 * ones that touch a match. Hidden nodes, their labels and their edges are left out entirely. In cluster-to-cluster mode
 * one edge is drawn per pair of clusters between the centroids of their visible nodes, with a width that
 * grows with the logarithm of the number of class edges it stands for, up to a cap.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphViewBuilder {
    /** World units per font pixel: the same scale vanilla text displays use. */
    public static final float LABEL_SCALE = 0.025F;
    /** Font pixels between the node center and the baseline of the class name line; the line is centered on the node. */
    public static final float NAME_LINE_OFFSET = 4.0F;
    /** Font pixels between the node center and the baseline of the package line, one line above the name. */
    public static final float PACKAGE_LINE_OFFSET = NAME_LINE_OFFSET + GraphGlyphAtlas.LINE_HEIGHT + 1.0F;

    private static final int NODE_STRIDE = 20;
    private static final int GLYPH_STRIDE = 56;
    private static final int EDGE_STRIDE = 52;
    private static final int CANCEL_CHECK_INTERVAL = 2048;

    private static final int NAME_COLOR_COMMON = 0xFFFFFFFF;
    private static final int NAME_COLOR_CLIENT = 0xFF55FFFF;
    private static final int PACKAGE_COLOR = 0xFFAAAAAA;
    private static final int NODE_EDGE_ALPHA = 153;
    private static final int CLUSTER_EDGE_ALPHA = 190;
    private static final float NODE_EDGE_WIDTH = 1.5F;
    /** Pixel width cap of a cluster edge; the width grows with the log of the number of class edges. */
    private static final float MAX_CLUSTER_EDGE_WIDTH = 8.0F;

    private final GraphAssets assets;
    private final GraphGlyphAtlas atlas;
    private final GraphViewSettings settings;
    private final int settingsVersion;
    private final AtomicBoolean cancelled;
    private int work;
    private boolean[] matches = new boolean[0];
    private boolean edgeEndpointsMustMatch;

    public GraphViewBuilder(GraphAssets assets, GraphGlyphAtlas atlas, GraphViewSettings settings, int settingsVersion, AtomicBoolean cancelled) {
        this.assets = assets;
        this.atlas = atlas;
        this.settings = settings;
        this.settingsVersion = settingsVersion;
        this.cancelled = cancelled;
    }

    /** Block edge length for a node: 0.3 for a leaf, about 1.3 for the largest hubs. */
    public static float nodeScale(int degree) {
        return (float) Mth.clamp(0.3 + 0.1 * Math.log(degree + 1.0) / Math.log(2.0), 0.3, 1.6);
    }

    /** Builds the view, or throws {@link CancellationException} when {@code cancelled} was set. */
    public GraphView build() {
        List<GraphNode> nodes = this.assets.nodes().nodes();
        GraphClusters clusters = this.assets.clusters();
        List<String> terms = this.settings.filterTerms();

        boolean[] visible = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            this.checkCancelled();
            visible[i] = this.isVisible(nodes.get(i), terms);
        }
        // With neighbors shown, "matches" keeps the filter hits apart from the neighbors added for them.
        boolean[] matches = visible;
        if (this.settings.adjacentMode().showsAdjacent() && !terms.isEmpty()) {
            matches = visible.clone();
            this.addAdjacent(visible, matches, nodes);
        }
        this.edgeEndpointsMustMatch = this.settings.adjacentMode() == GraphViewSettings.AdjacentMode.MATCH_EDGES && !terms.isEmpty();
        this.matches = matches;

        int[] compact = new int[nodes.size()];
        int visibleCount = 0;
        int nameChars = 0;
        int packageChars = 0;
        for (int i = 0; i < nodes.size(); i++) {
            if (visible[i]) {
                GraphNode node = nodes.get(i);
                compact[i] = visibleCount++;
                nameChars += node.simpleName().length();
                packageChars += node.packageName().length();
            } else {
                compact[i] = -1;
            }
        }

        ByteBuffer nodeBuffer = allocate(visibleCount * NODE_STRIDE);
        ByteBuffer nameBuffer = allocate(nameChars * GLYPH_STRIDE);
        ByteBuffer packageBuffer = allocate(packageChars * GLYPH_STRIDE);
        float[] boxes = new float[visibleCount * 4];
        int[] ids = new int[visibleCount];
        int[] packageStart = new int[visibleCount];
        int[] packageCount = new int[visibleCount];
        int nameGlyphs = 0;
        int packageGlyphs = 0;

        for (int i = 0; i < nodes.size(); i++) {
            int c = compact[i];
            if (c < 0) {
                continue;
            }
            this.checkCancelled();
            GraphNode node = nodes.get(i);
            float scale = nodeScale(node.degree());
            int color = clusters.color(node.cluster());
            nodeBuffer.putFloat(node.x()).putFloat(node.y()).putFloat(node.z()).putFloat(scale);
            putColor(nodeBuffer, color);
            boxes[c * 4] = node.x();
            boxes[c * 4 + 1] = node.y();
            boxes[c * 4 + 2] = node.z();
            boxes[c * 4 + 3] = scale;
            ids[c] = i;

            int nameColor = node.side() == GraphNode.Side.CLIENT ? NAME_COLOR_CLIENT : NAME_COLOR_COMMON;
            nameGlyphs += this.putLabel(nameBuffer, node, scale, node.simpleName(), nameColor, NAME_LINE_OFFSET);
            packageStart[c] = packageGlyphs;
            packageCount[c] = this.putLabel(packageBuffer, node, scale, node.packageName(), PACKAGE_COLOR, PACKAGE_LINE_OFFSET);
            packageGlyphs += packageCount[c];
        }

        ByteBuffer edgeBuffer;
        int edgeCount;
        switch (this.settings.edgeMode()) {
            case NODES -> {
                edgeBuffer = allocate(this.assets.edges().count() * EDGE_STRIDE);
                edgeCount = this.putNodeEdges(edgeBuffer, nodes, compact, clusters);
            }
            case CLUSTERS -> {
                edgeBuffer = allocate(clusters.clusters().size() * clusters.clusters().size() * EDGE_STRIDE / 2 + EDGE_STRIDE);
                edgeCount = this.putClusterEdges(edgeBuffer, nodes, compact, clusters);
            }
            default -> {
                edgeBuffer = allocate(0);
                edgeCount = 0;
            }
        }

        return new GraphView(
                this.settingsVersion,
                this.assets.generation(),
                nodeBuffer.flip(),
                visibleCount,
                boxes,
                ids,
                nameBuffer.flip(),
                nameGlyphs,
                packageBuffer.flip(),
                packageGlyphs,
                packageStart,
                packageCount,
                edgeBuffer.flip(),
                edgeCount
        );
    }

    /** Marks every node that shares an edge with a match, unless its cluster is hidden. */
    private void addAdjacent(boolean[] visible, boolean[] matches, List<GraphNode> nodes) {
        GraphEdges edges = this.assets.edges();
        for (int e = 0; e < edges.count(); e++) {
            this.checkCancelled();
            int a = edges.nodeA(e);
            int b = edges.nodeB(e);
            if (matches[a] && !this.settings.hiddenClusters().contains(nodes.get(b).cluster())) {
                visible[b] = true;
            }
            if (matches[b] && !this.settings.hiddenClusters().contains(nodes.get(a).cluster())) {
                visible[a] = true;
            }
        }
    }

    /** False for an edge between two neighbors when only edges to the matches are wanted. */
    private boolean edgeAllowed(int a, int b) {
        return !this.edgeEndpointsMustMatch || this.matches[a] || this.matches[b];
    }

    private boolean isVisible(GraphNode node, List<String> terms) {
        if (this.settings.hiddenClusters().contains(node.cluster())) {
            return false;
        }
        return GraphFilter.matches(node.className(), terms);
    }

    /** Writes the glyph instances of one centered text line and returns how many it wrote. */
    private int putLabel(ByteBuffer buffer, GraphNode node, float scale, String text, int color, float lineOffset) {
        float penX = -this.atlas.width(text) / 2.0F;
        int written = 0;
        for (int i = 0; i < text.length(); i++) {
            GraphGlyphAtlas.Glyph glyph = this.atlas.glyph(text.charAt(i));
            if (glyph == null) {
                glyph = this.atlas.glyph('?');
                if (glyph == null) {
                    continue;
                }
            }
            if (!glyph.empty()) {
                buffer.putFloat(node.x()).putFloat(node.y()).putFloat(node.z()).putFloat(scale);
                buffer.putFloat(penX + glyph.left()).putFloat(-glyph.down());
                buffer.putFloat(glyph.right() - glyph.left()).putFloat(glyph.down() - glyph.up());
                buffer.putFloat(glyph.u0()).putFloat(glyph.v0()).putFloat(glyph.u1()).putFloat(glyph.v1());
                putColor(buffer, color);
                buffer.putFloat(lineOffset);
                written++;
            }
            penX += glyph.advance();
        }
        return written;
    }

    private int putNodeEdges(ByteBuffer buffer, List<GraphNode> nodes, int[] compact, GraphClusters clusters) {
        GraphEdges edges = this.assets.edges();
        int count = 0;
        for (int e = 0; e < edges.count(); e++) {
            this.checkCancelled();
            int a = edges.nodeA(e);
            int b = edges.nodeB(e);
            int ca = compact[a];
            int cb = compact[b];
            if (ca < 0 || cb < 0 || !this.edgeAllowed(a, b)) {
                continue;
            }
            GraphNode nodeA = nodes.get(a);
            GraphNode nodeB = nodes.get(b);
            buffer.putFloat(nodeA.x()).putFloat(nodeA.y()).putFloat(nodeA.z());
            buffer.putFloat(nodeB.x()).putFloat(nodeB.y()).putFloat(nodeB.z());
            putColor(buffer, ARGB.color(NODE_EDGE_ALPHA, clusters.color(nodeA.cluster())));
            putColor(buffer, ARGB.color(NODE_EDGE_ALPHA, clusters.color(nodeB.cluster())));
            buffer.putInt(ca).putInt(cb);
            buffer.putFloat(NODE_EDGE_WIDTH);
            buffer.putFloat(nodeScale(nodeA.degree())).putFloat(nodeScale(nodeB.degree()));
            count++;
        }
        return count;
    }

    private int putClusterEdges(ByteBuffer buffer, List<GraphNode> nodes, int[] compact, GraphClusters clusters) {
        List<GraphCluster> list = clusters.clusters();
        int clusterCount = list.size();
        int[] slotOf = new int[clusterCount == 0 ? 1 : maxClusterId(list) + 1];
        java.util.Arrays.fill(slotOf, -1);
        for (int s = 0; s < clusterCount; s++) {
            slotOf[list.get(s).id()] = s;
        }
        double[] sums = new double[clusterCount * 3];
        int[] members = new int[clusterCount];
        for (int i = 0; i < nodes.size(); i++) {
            if (compact[i] < 0) {
                continue;
            }
            int slot = slot(slotOf, nodes.get(i).cluster());
            if (slot < 0) {
                continue;
            }
            GraphNode node = nodes.get(i);
            sums[slot * 3] += node.x();
            sums[slot * 3 + 1] += node.y();
            sums[slot * 3 + 2] += node.z();
            members[slot]++;
        }

        GraphEdges edges = this.assets.edges();
        Long2IntMap pairCounts = new Long2IntOpenHashMap();
        for (int e = 0; e < edges.count(); e++) {
            this.checkCancelled();
            int a = edges.nodeA(e);
            int b = edges.nodeB(e);
            if (compact[a] < 0 || compact[b] < 0 || !this.edgeAllowed(a, b)) {
                continue;
            }
            int sa = slot(slotOf, nodes.get(a).cluster());
            int sb = slot(slotOf, nodes.get(b).cluster());
            if (sa < 0 || sb < 0 || sa == sb) {
                continue;
            }
            long key = ((long) Math.min(sa, sb) << 32) | Math.max(sa, sb);
            pairCounts.mergeInt(key, 1, Integer::sum);
        }

        int count = 0;
        for (Long2IntMap.Entry entry : pairCounts.long2IntEntrySet()) {
            int sa = (int) (entry.getLongKey() >>> 32);
            int sb = (int) entry.getLongKey();
            if (members[sa] == 0 || members[sb] == 0) {
                continue;
            }
            for (int s : new int[] {sa, sb}) {
                buffer.putFloat((float) (sums[s * 3] / members[s]));
                buffer.putFloat((float) (sums[s * 3 + 1] / members[s]));
                buffer.putFloat((float) (sums[s * 3 + 2] / members[s]));
            }
            putColor(buffer, ARGB.color(CLUSTER_EDGE_ALPHA, list.get(sa).color()));
            putColor(buffer, ARGB.color(CLUSTER_EDGE_ALPHA, list.get(sb).color()));
            buffer.putInt(-1).putInt(-1);
            buffer.putFloat((float) Math.min(MAX_CLUSTER_EDGE_WIDTH, 1.0 + Math.log(entry.getIntValue()) / Math.log(2.0)));
            buffer.putFloat(0.0F).putFloat(0.0F);
            count++;
        }
        return count;
    }

    private static int maxClusterId(List<GraphCluster> list) {
        int max = 0;
        for (GraphCluster cluster : list) {
            max = Math.max(max, cluster.id());
        }
        return max;
    }

    private static int slot(int[] slotOf, int cluster) {
        return cluster >= 0 && cluster < slotOf.length ? slotOf[cluster] : -1;
    }

    /** Writes an ARGB int as the four bytes R, G, B, A that {@code RGBA8_UNORM} reads. */
    private static void putColor(ByteBuffer buffer, int argb) {
        buffer.put((byte) ARGB.red(argb)).put((byte) ARGB.green(argb)).put((byte) ARGB.blue(argb)).put((byte) ARGB.alpha(argb));
    }

    private static ByteBuffer allocate(int bytes) {
        return ByteBuffer.allocateDirect(Math.max(bytes, 0)).order(ByteOrder.nativeOrder());
    }

    private void checkCancelled() {
        if (++this.work % CANCEL_CHECK_INTERVAL == 0 && this.cancelled.get()) {
            throw new CancellationException("Graph view build cancelled");
        }
    }
}

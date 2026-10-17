package us.drullk.graphmachine.client.panel;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;
import us.drullk.graphmachine.client.data.GraphAssets;
import us.drullk.graphmachine.client.data.GraphAssetsLoader;
import us.drullk.graphmachine.client.render.GraphRenderer;
import us.drullk.graphmachine.client.render.GraphView;
import us.drullk.graphmachine.graph.GraphCluster;

/**
 * The ImGui inspector window on the right side of the screen and the splitter on its left edge.
 *
 * <p>The window is pinned to the panel strip and cannot be moved. From top to bottom: the filter box,
 * the package label and edge dropdowns, the hovered class, a collapsible list of cluster checkboxes with
 * color swatches, and a footer pinned to the bottom that shows the apply progress or the drawn counts.
 * Every edit goes straight into {@link GraphSettingsStore}; the renderer picks the change up on the
 * next frame. Dragging the splitter changes the width when the mouse is released.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphPanel {
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoBringToFrontOnFocus;
    private static final int SWATCH_FLAGS = ImGuiColorEditFlags.NoTooltip | ImGuiColorEditFlags.NoPicker | ImGuiColorEditFlags.NoAlpha | ImGuiColorEditFlags.NoDragDrop;
    private static final int FILTER_CAPACITY = 256;
    private static final float SPLITTER_GRAB_PT = 4.0F;
    private static final float SPLITTER_LINE_PT = 2.0F;
    private static final int SPLITTER_COLOR = packColor(0x60, 0x60, 0x66, 0xFF);
    private static final int SPLITTER_ACTIVE_COLOR = packColor(0x4A, 0x90, 0xE2, 0xFF);

    private final GraphRenderer renderer;
    private final ImString filterBuffer = new ImString(FILTER_CAPACITY);
    private final ImInt packageIndex = new ImInt();
    private final ImInt edgeIndex = new ImInt();
    private final ImBoolean checkbox = new ImBoolean();
    private int syncedVersion = -1;
    private boolean draggingSplitter;

    public GraphPanel(GraphRenderer renderer) {
        this.renderer = renderer;
    }

    private static float px(float points) {
        return GraphImGuiContext.px(points);
    }

    /** ImGui packs colors as ABGR. */
    private static int packColor(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /**
     * Draws one frame.
     *
     * @param fullWidth  window width in framebuffer pixels
     * @param height     window height in framebuffer pixels
     * @param mainWidth  width of the main render target; the panel starts here
     */
    public void draw(int fullWidth, int height, int mainWidth) {
        this.syncFromSettings();
        this.drawSplitter(fullWidth, height, mainWidth);

        ImGui.setNextWindowPos(mainWidth, 0, ImGuiCond.Always);
        ImGui.setNextWindowSize(fullWidth - mainWidth, height, ImGuiCond.Always);
        if (ImGui.begin("Graph", WINDOW_FLAGS)) {
            float footerHeight = ImGui.getFrameHeightWithSpacing() * 2.0F + px(8);
            if (ImGui.beginChild("content", 0, -footerHeight, false)) {
                this.drawContent();
            }
            ImGui.endChild();
            this.drawFooter();
        }
        ImGui.end();
    }

    private void syncFromSettings() {
        int version = GraphSettingsStore.version();
        if (version == this.syncedVersion) {
            return;
        }
        this.syncedVersion = version;
        GraphViewSettings settings = GraphSettingsStore.current();
        if (!this.filterBuffer.get().equals(settings.filter())) {
            this.filterBuffer.set(settings.filter());
        }
        this.packageIndex.set(settings.packageMode().ordinal());
        this.edgeIndex.set(settings.edgeMode().ordinal());
    }

    private void drawSplitter(int fullWidth, int height, int mainWidth) {
        boolean cursorFree = Minecraft.getInstance().gui.screen() != null;
        ImVec2 mouse = ImGui.getMousePos();
        float grab = px(SPLITTER_GRAB_PT);
        boolean over = cursorFree && Math.abs(mouse.x - mainWidth) <= grab && mouse.y >= 0 && mouse.y <= height;
        float density = GraphImGuiContext.density();

        if (this.draggingSplitter) {
            if (!ImGui.isMouseDown(0) || !cursorFree) {
                this.draggingSplitter = false;
                int widthPt = PanelLayout.draggingWidthPt();
                PanelLayout.setDraggingWidthPt(-1);
                if (widthPt > 0) {
                    GraphSettingsStore.update(settings -> settings.withPanelWidthPt(widthPt));
                }
            } else {
                PanelLayout.setDraggingWidthPt(Math.round((fullWidth - mouse.x) / density));
            }
        } else if (over && ImGui.isMouseClicked(0)) {
            this.draggingSplitter = true;
            PanelLayout.setDraggingWidthPt(PanelLayout.panelWidthPt());
        }

        if (over || this.draggingSplitter) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
        float lineX = this.draggingSplitter ? fullWidth - PanelLayout.draggingWidthPt() * density : mainWidth;
        ImGui.getForegroundDrawList().addLine(lineX, 0, lineX, height, this.draggingSplitter || over ? SPLITTER_ACTIVE_COLOR : SPLITTER_COLOR, px(SPLITTER_LINE_PT));
    }

    private void drawContent() {
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputTextWithHint("##filter", "Filter by package or class", this.filterBuffer)) {
            String text = this.filterBuffer.get();
            GraphSettingsStore.update(settings -> settings.withFilter(text));
        }

        ImGui.setNextItemWidth(px(170));
        if (ImGui.combo("Package labels", this.packageIndex, GraphViewSettings.PackageMode.LABELS)) {
            GraphViewSettings.PackageMode mode = GraphViewSettings.PackageMode.values()[this.packageIndex.get()];
            GraphSettingsStore.update(settings -> settings.withPackageMode(mode));
        }
        ImGui.setNextItemWidth(px(170));
        if (ImGui.combo("Edges", this.edgeIndex, GraphViewSettings.EdgeMode.LABELS)) {
            GraphViewSettings.EdgeMode mode = GraphViewSettings.EdgeMode.values()[this.edgeIndex.get()];
            GraphSettingsStore.update(settings -> settings.withEdgeMode(mode));
        }

        ImGui.separator();
        @Nullable String hovered = this.renderer.hoveredClassName();
        if (hovered == null) {
            ImGui.textDisabled("Aim at a node to see its class");
        } else {
            ImGui.textWrapped(hovered);
        }
        ImGui.separator();

        this.drawClusters();
    }

    private void drawClusters() {
        GraphAssets assets = GraphAssetsLoader.current();
        if (assets == null) {
            return;
        }
        List<GraphCluster> clusters = assets.clusters().clusters();
        GraphViewSettings settings = GraphSettingsStore.current();
        int shown = clusters.size() - settings.hiddenClusters().size();
        if (!ImGui.collapsingHeader("Clusters (" + shown + " of " + clusters.size() + " shown)", ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }
        if (ImGui.smallButton("All")) {
            GraphSettingsStore.update(current -> current.withHiddenClusters(IntSets.emptySet()));
        }
        ImGui.sameLine();
        if (ImGui.smallButton("None")) {
            IntSet all = new IntOpenHashSet();
            for (GraphCluster cluster : clusters) {
                all.add(cluster.id());
            }
            GraphSettingsStore.update(current -> current.withHiddenClusters(all));
        }
        float swatch = ImGui.getFrameHeight();
        for (GraphCluster cluster : clusters) {
            ImGui.pushID(cluster.id());
            int color = cluster.color();
            ImGui.colorButton("##swatch", ARGB.red(color) / 255.0F, ARGB.green(color) / 255.0F, ARGB.blue(color) / 255.0F, 1.0F, SWATCH_FLAGS, swatch, swatch);
            ImGui.sameLine();
            this.checkbox.set(!settings.hiddenClusters().contains(cluster.id()));
            if (ImGui.checkbox(cluster.label(), this.checkbox)) {
                boolean hidden = !this.checkbox.get();
                int id = cluster.id();
                GraphSettingsStore.update(current -> current.withClusterHidden(id, hidden));
            }
            ImGui.popID();
        }
    }

    private void drawFooter() {
        ImGui.separator();
        if (this.renderer.applying()) {
            ImGui.text("Applying changes");
            ImGui.progressBar(this.renderer.applyProgress(), -1, 0);
        } else {
            GraphView view = this.renderer.frontView();
            if (view == null) {
                ImGui.textDisabled("Waiting for the graph");
            } else {
                ImGui.textDisabled("Up to date");
                ImGui.textDisabled(view.nodeCount() + " nodes, " + view.edgeCount() + " edges, " + (view.nameGlyphCount() + view.packageGlyphCount()) + " glyphs");
            }
        }
    }
}

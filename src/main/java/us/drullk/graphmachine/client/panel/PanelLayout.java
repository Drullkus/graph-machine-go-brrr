package us.drullk.graphmachine.client.panel;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The one place that decides how many framebuffer pixels the panel takes from the right edge of the
 * window. The mixins on {@code Window} and {@code MouseHandler} read {@link #reservedFramebufferWidth()}.
 *
 * <p>The panel width is stored in points (logical pixels). Multiplying by the window's pixel density
 * gives framebuffer pixels: 2x on a Retina display, 1x on a standard display. GUI Scale plays no part.
 * When the reserved width changes, the window re-reads its framebuffer size and Minecraft resizes the
 * GUI and, on the next frame, the main render target.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class PanelLayout {
    private static boolean active;
    private static int reservedPx;
    /** A width being dragged, in points, or -1 when no drag is in progress. */
    private static int draggingWidthPt = -1;

    private PanelLayout() {
    }

    /** Framebuffer pixels per logical pixel of the window. */
    public static float density() {
        return Minecraft.getInstance().getWindow().getPixelDensity();
    }

    /** The panel width in points, as set; a drag in progress does not change it until it ends. */
    public static int panelWidthPt() {
        return GraphSettingsStore.current().panelWidthPt();
    }

    /** The width being dragged, in points, or -1 when no drag is in progress. */
    public static int draggingWidthPt() {
        return draggingWidthPt;
    }

    /** Framebuffer pixels currently taken from the right edge of the window; 0 when the panel is off. */
    public static int reservedFramebufferWidth() {
        return reservedPx;
    }

    /** True while the panel is drawn and the framebuffer is narrowed. */
    public static boolean active() {
        return active;
    }

    /** Shows a width while the splitter is dragged, without saving it; pass -1 when the drag ends. */
    public static void setDraggingWidthPt(int widthPt) {
        draggingWidthPt = widthPt < 0 ? -1 : Mth.clamp(widthPt, GraphViewSettings.MIN_PANEL_WIDTH_PT, GraphViewSettings.MAX_PANEL_WIDTH_PT);
    }

    /**
     * Applies the wanted state for this frame. Call once per frame before anything renders.
     *
     * @param wantActive whether the panel should be shown this frame
     */
    public static void update(boolean wantActive) {
        active = wantActive;
        int wanted = wantActive ? Math.round(panelWidthPt() * density()) : 0;
        if (wanted != reservedPx) {
            reservedPx = wanted;
            Minecraft minecraft = Minecraft.getInstance();
            Window window = minecraft.getWindow();
            window.refreshFramebufferSize();
            minecraft.framebufferSizeChanged();
        }
    }

    /** Width of the full window in framebuffer pixels, including the panel strip. */
    public static int fullFramebufferWidth() {
        return Minecraft.getInstance().getWindow().getWidth() + reservedPx;
    }
}

package us.drullk.graphmachine.client.panel;

import imgui.ImDrawData;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiBackendFlags;
import imgui.internal.ImGuiContext;
import org.jspecify.annotations.Nullable;

/**
 * Owns the Dear ImGui context of the panel.
 *
 * <p>The context is created on first use and rebuilt whenever the window's pixel density changes: the
 * default font is rasterized at {@value #BASE_FONT_SIZE} points times the density and every style size
 * is multiplied by the same factor. ImGui itself works in framebuffer pixels. GUI Scale is never
 * consulted. No .ini file is written.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphImGuiContext {
    /** Font size in points before density scaling. */
    public static final float BASE_FONT_SIZE = 14.0F;

    private static @Nullable ImGuiContext context;
    private static float contextDensity = -1.0F;
    private static long lastFrameNanos;

    private GraphImGuiContext() {
    }

    /** Creates the context for {@code density}, replacing one built for a different density. */
    public static void ensureContext(float density) {
        float wanted = Math.max(0.5F, density);
        if (context != null && Math.abs(contextDensity - wanted) < 0.001F) {
            return;
        }
        destroy();

        context = ImGui.createContext();
        contextDensity = wanted;
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.setBackendPlatformName("graphmachine_minecraft");
        io.setBackendRendererName("graphmachine_blaze3d");
        io.addBackendFlags(ImGuiBackendFlags.RendererHasVtxOffset);

        ImFontConfig fontConfig = new ImFontConfig();
        fontConfig.setSizePixels(BASE_FONT_SIZE * wanted);
        io.getFonts().addFontDefault(fontConfig);
        fontConfig.destroy();

        ImGui.styleColorsDark();
        ImGui.getStyle().scaleAllSizes(wanted);
        ImGui.getStyle().setWindowRounding(0.0F);
        ImGui.getStyle().setWindowBorderSize(0.0F);

        ImGuiRenderer.uploadFontAtlas(io.getFonts());
        lastFrameNanos = 0L;
    }

    public static boolean hasContext() {
        return context != null;
    }

    /** The density the current context was built for. */
    public static float density() {
        return Math.max(0.5F, contextDensity);
    }

    /** Converts a size in points to framebuffer pixels for the current context. */
    public static float px(float points) {
        return points * density();
    }

    /** Frees the context and its GPU resources. */
    public static void destroy() {
        if (context != null) {
            ImGuiRenderer.releaseFontAtlas();
            ImGui.destroyContext(context);
            context = null;
            contextDensity = -1.0F;
        }
    }

    /** Starts a frame that covers the whole window. */
    public static void beginFrame(int framebufferWidth, int framebufferHeight) {
        ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(Math.max(1, framebufferWidth), Math.max(1, framebufferHeight));
        io.setDisplayFramebufferScale(1.0F, 1.0F);

        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1.0F / 60.0F : (now - lastFrameNanos) / 1.0e9F;
        lastFrameNanos = now;
        io.setDeltaTime(Math.max(delta, 1.0e-4F));

        ImGui.newFrame();
    }

    /** Ends the frame and returns the draw data for {@link ImGuiRenderer#render}. */
    public static ImDrawData endFrame() {
        ImGui.render();
        return ImGui.getDrawData();
    }
}

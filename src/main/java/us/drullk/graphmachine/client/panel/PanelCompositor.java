package us.drullk.graphmachine.client.panel;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import imgui.ImDrawData;
import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Builds the picture that reaches the window while the panel is shown: the main render target on the
 * left, the ImGui panel on the right, in one full-window texture.
 *
 * <p>Runs at {@link RenderFrameEvent.Post}, after the whole game frame and right before Minecraft blits
 * the main render target to the swapchain. {@code MinecraftMixin} swaps that blit's source for
 * {@link #compositeView()} when it is set. The composite is cleared, the main color texture is copied
 * into its left part, then the ImGui frame runs and is drawn on top.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class PanelCompositor {
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.11F, 0.11F, 0.12F, 1.0F);
    private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

    private static @Nullable GpuTexture composite;
    private static @Nullable GpuTextureView compositeView;
    private static @Nullable GpuTextureView published;

    private PanelCompositor() {
    }

    /** The texture to present instead of the main render target this frame, or null. */
    public static @Nullable GpuTextureView compositeView() {
        return published;
    }

    /** Composites the frame when the panel is active. {@code panel} draws the ImGui contents. */
    public static void compose(GraphPanel panel) {
        published = null;
        if (!PanelLayout.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        GpuTexture mainColor = main.getColorTexture();
        if (mainColor == null) {
            return;
        }
        int mainWidth = main.width;
        int height = main.height;
        int fullWidth = mainWidth + PanelLayout.reservedFramebufferWidth();
        GpuTextureView view = ensureComposite(fullWidth, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(view.texture(), CLEAR_COLOR);
        encoder.copyTextureToTexture(mainColor, view.texture(), 0, 0, 0, 0, 0, mainWidth, height);

        float density = PanelLayout.density();
        GraphImGuiContext.ensureContext(density);
        PanelInput.updateMousePosition(density);
        GraphImGuiContext.beginFrame(fullWidth, height);
        ImDrawData drawData;
        try {
            panel.draw(fullWidth, height, mainWidth);
        } finally {
            drawData = GraphImGuiContext.endFrame();
        }
        ImGuiRenderer.render(drawData, view);
        PanelInput.afterFrame(ImGui.getIO());
        published = view;
    }

    private static GpuTextureView ensureComposite(int width, int height) {
        GpuTexture texture = composite;
        GpuTextureView view = compositeView;
        if (texture != null && view != null && texture.getWidth(0) == width && texture.getHeight(0) == height) {
            return view;
        }
        release();
        GpuDevice device = RenderSystem.getDevice();
        texture = device.createTexture(() -> "Graph panel composite", USAGE, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        view = device.createTextureView(texture);
        composite = texture;
        compositeView = view;
        return view;
    }

    /** Frees the composite texture. */
    public static void release() {
        published = null;
        if (compositeView != null) {
            compositeView.close();
            compositeView = null;
        }
        if (composite != null) {
            composite.close();
            composite = null;
        }
    }
}

package us.drullk.graphmachine.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.io.File;
import java.io.IOException;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import us.drullk.graphmachine.GraphMachine;
import us.drullk.graphmachine.client.panel.PanelCompositor;
import us.drullk.graphmachine.client.render.GraphRenderer;

/**
 * Development aid: with the system property {@code graphmachine.autoScreenshot=N} set, saves one PNG of
 * what the window shows (the composite with the panel, or the main render target) to
 * {@code screenshots/graphmachine-auto.png} N seconds after the graph first finished applying. The
 * {@code clientGraph} run sets the property so the renderer can be checked without touching the game.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
final class AutoScreenshot {
    private static final String PROPERTY = "graphmachine.autoScreenshot";
    private static final String FILE_NAME = "graphmachine-auto.png";

    private final long delayMs;
    private long readySinceMs = -1;
    private boolean taken;

    private AutoScreenshot(long delayMs) {
        this.delayMs = delayMs;
    }

    /** The helper when the property is set, else null. */
    static @Nullable AutoScreenshot fromSystemProperty() {
        String value = System.getProperty(PROPERTY);
        if (value == null) {
            return null;
        }
        try {
            return new AutoScreenshot(Math.round(Double.parseDouble(value) * 1000.0));
        } catch (NumberFormatException e) {
            GraphMachine.LOGGER.warn("Ignoring {}={}: not a number of seconds", PROPERTY, value);
            return null;
        }
    }

    /** Call once per frame after the compositor ran. */
    void onFrameEnd(GraphRenderer renderer) {
        if (this.taken) {
            return;
        }
        if (renderer.frontView() == null || renderer.applying()) {
            this.readySinceMs = -1;
            return;
        }
        long now = Util.getMillis();
        if (this.readySinceMs < 0) {
            this.readySinceMs = now;
            return;
        }
        if (now - this.readySinceMs < this.delayMs) {
            return;
        }
        this.taken = true;
        GpuTexture texture = PanelCompositor.compositeView() != null ? PanelCompositor.compositeView().texture() : Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture();
        if (texture == null) {
            return;
        }
        File file = new File(Minecraft.getInstance().gameDirectory, "screenshots/" + FILE_NAME);
        save(texture, file);
    }

    private static void save(GpuTexture texture, File file) {
        int width = texture.getWidth(0);
        int height = texture.getHeight(0);
        int bytesPerPixel = texture.getFormat().blockSize();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Graph auto screenshot", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * bytesPerPixel);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
            try (GpuBufferSlice.MappedView read = buffer.map(true, false); NativeImage image = new NativeImage(width, height, false)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int abgr = read.data().getInt((x + y * width) * bytesPerPixel);
                        image.setPixelABGR(x, height - y - 1, abgr | 0xFF000000);
                    }
                }
                file.getParentFile().mkdirs();
                image.writeToFile(file);
                GraphMachine.LOGGER.info("Saved graph screenshot to {}", file);
            } catch (IOException e) {
                GraphMachine.LOGGER.error("Cannot write {}", file, e);
            } finally {
                buffer.close();
            }
        }, 0);
    }
}

package us.drullk.graphmachine.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.io.File;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import us.drullk.graphmachine.GraphMachine;

/**
 * Development aid: writes a GPU texture to a PNG under {@code screenshots/}. Single-channel textures are
 * written as gray, and so is the alpha channel of a four-channel texture when {@code alphaAsGray} is set
 * (font pages keep their coverage there). The copy runs asynchronously and logs when the file is there.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class TextureDump {
    /** System property that turns the development dumps and the automatic screenshot on. */
    public static final String PROPERTY = "graphmachine.autoScreenshot";

    private TextureDump() {
    }

    /** True when the development dumps are wanted. */
    public static boolean enabled() {
        return System.getProperty(PROPERTY) != null;
    }

    /** Saves {@code texture} as {@code screenshots/<name>.png} in the game directory. */
    public static void save(GpuTexture texture, String name, boolean alphaAsGray) {
        int width = texture.getWidth(0);
        int height = texture.getHeight(0);
        int bytesPerPixel = texture.getFormat().blockSize();
        boolean singleChannel = texture.getFormat() == GpuFormat.R8_UNORM;
        File file = new File(Minecraft.getInstance().gameDirectory, "screenshots/" + name + ".png");
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Graph texture dump", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * bytesPerPixel);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
            try (GpuBufferSlice.MappedView read = buffer.map(true, false); NativeImage image = new NativeImage(width, height, false)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int index = (x + y * width) * bytesPerPixel;
                        int abgr;
                        if (singleChannel) {
                            int gray = read.data().get(index) & 0xFF;
                            abgr = ARGB.color(255, gray, gray, gray);
                        } else if (alphaAsGray) {
                            int alpha = read.data().get(index + 3) & 0xFF;
                            abgr = ARGB.color(255, alpha, alpha, alpha);
                        } else {
                            abgr = read.data().getInt(index) | 0xFF000000;
                        }
                        image.setPixelABGR(x, y, abgr);
                    }
                }
                file.getParentFile().mkdirs();
                image.writeToFile(file);
                GraphMachine.LOGGER.info("Saved texture dump to {}", file);
            } catch (IOException e) {
                GraphMachine.LOGGER.error("Cannot write {}", file, e);
            } finally {
                buffer.close();
            }
        }, 0);
    }
}

package us.drullk.graphmachine.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.network.chat.FontDescription;
import org.jspecify.annotations.Nullable;
import us.drullk.graphmachine.GraphMachine;

/**
 * A copy of the printable ASCII glyphs of Minecraft's default font in one texture of the mod, plus the
 * metrics the label builder needs.
 *
 * <p>Vanilla stitches glyphs into 256 by 256 pages as they are first used. This class asks the font for
 * every glyph from space to tilde, which bakes them, then copies each glyph rectangle out of its page into
 * one {@code R8_UNORM} atlas of the same size. The result is a single texture, so all labels draw in one
 * call, and it survives vanilla dropping its pages because the copy is owned here. Build it again after a
 * resource reload, because glyph shapes and positions can change.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphGlyphAtlas implements AutoCloseable {
    public static final int FIRST_CODEPOINT = ' ';
    public static final int LAST_CODEPOINT = '~';
    /** Height of a text line in font pixels, the same as vanilla's line height. */
    public static final float LINE_HEIGHT = 9.0F;
    private static final int SIZE = 256;
    private static final int PADDING = 1;

    /** Where a glyph sits on the atlas and relative to the text origin, all in font pixels except the UVs. */
    public record Glyph(float advance, float left, float right, float up, float down, float u0, float v0, float u1, float v1) {
        /** True when the glyph has no pixels, for example a space. */
        public boolean empty() {
            return this.right <= this.left;
        }
    }

    private final Glyph[] glyphs = new Glyph[LAST_CODEPOINT - FIRST_CODEPOINT + 1];
    private final GpuTexture texture;
    private final GpuTextureView view;

    private GraphGlyphAtlas(GpuTexture texture, GpuTextureView view) {
        this.texture = texture;
        this.view = view;
    }

    /** Builds the atlas from the current default font. Must run on the render thread after fonts loaded. */
    public static GraphGlyphAtlas build() {
        GpuDevice device = RenderSystem.getDevice();
        GlyphSource source = Minecraft.getInstance().font.getGlyphSource(FontDescription.DEFAULT);
        GpuFormat format = pageFormat(source);
        GpuTexture texture = device.createTexture(() -> "Graph label atlas", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, format, SIZE, SIZE, 1, 1);
        GpuTextureView view = device.createTextureView(texture);
        GraphGlyphAtlas atlas = new GraphGlyphAtlas(texture, view);
        atlas.fill(device.createCommandEncoder(), source, format);
        return atlas;
    }

    /** The format of the page that holds the first printable glyph; {@code R8_UNORM} when there is none. */
    private static GpuFormat pageFormat(GlyphSource source) {
        for (int codepoint = FIRST_CODEPOINT + 1; codepoint <= LAST_CODEPOINT; codepoint++) {
            if (source.getGlyph(codepoint) instanceof BakedSheetGlyph sheet) {
                return sheet.textureView.texture().getFormat();
            }
        }
        return GpuFormat.R8_UNORM;
    }

    /** True when coverage is in the red channel (a grayscale page); false when it is in the alpha channel. */
    public boolean singleChannel() {
        return this.texture.getFormat() == GpuFormat.R8_UNORM;
    }

    private void fill(CommandEncoder encoder, GlyphSource source, GpuFormat format) {
        int penX = PADDING;
        int penY = PADDING;
        int rowHeight = 0;
        int skipped = 0;
        for (int codepoint = FIRST_CODEPOINT; codepoint <= LAST_CODEPOINT; codepoint++) {
            BakedGlyph baked = source.getGlyph(codepoint);
            float advance = baked.info().getAdvance(false);
            if (!(baked instanceof BakedSheetGlyph sheet) || sheet.textureView.texture().getFormat() != format) {
                this.glyphs[codepoint - FIRST_CODEPOINT] = new Glyph(advance, 0, 0, 0, 0, 0, 0, 0, 0);
                if (codepoint != ' ') {
                    skipped++;
                }
                continue;
            }
            GpuTexture page = sheet.textureView.texture();
            int pageWidth = page.getWidth(0);
            int pageHeight = page.getHeight(0);
            int sourceX = Math.round(sheet.u0 * pageWidth);
            int sourceY = Math.round(sheet.v0 * pageHeight);
            int width = Math.max(1, Math.round((sheet.u1 - sheet.u0) * pageWidth));
            int height = Math.max(1, Math.round((sheet.v1 - sheet.v0) * pageHeight));
            if (penX + width + PADDING > SIZE) {
                penX = PADDING;
                penY += rowHeight + PADDING;
                rowHeight = 0;
            }
            if (penY + height + PADDING > SIZE) {
                GraphMachine.LOGGER.warn("Graph label atlas is full at codepoint {}", codepoint);
                this.glyphs[codepoint - FIRST_CODEPOINT] = new Glyph(advance, 0, 0, 0, 0, 0, 0, 0, 0);
                continue;
            }
            encoder.copyTextureToTexture(page, this.texture, 0, penX, penY, sourceX, sourceY, width, height);
            float nudge = 0.01F;
            this.glyphs[codepoint - FIRST_CODEPOINT] = new Glyph(
                    advance, sheet.left, sheet.right, sheet.up, sheet.down,
                    (penX + nudge) / SIZE, (penY + nudge) / SIZE, (penX + width - nudge) / SIZE, (penY + height - nudge) / SIZE
            );
            penX += width + PADDING;
            rowHeight = Math.max(rowHeight, height);
        }
        if (skipped > 0) {
            GraphMachine.LOGGER.warn("{} ASCII glyphs of the default font could not be copied (not sheet glyphs, or a page of another format); their labels show gaps", skipped);
        }
    }

    /** The glyph of a codepoint, or null when the atlas has none (anything outside printable ASCII). */
    public @Nullable Glyph glyph(int codepoint) {
        return codepoint >= FIRST_CODEPOINT && codepoint <= LAST_CODEPOINT ? this.glyphs[codepoint - FIRST_CODEPOINT] : null;
    }

    /** Width of a string in font pixels; unknown characters count as the width of a question mark. */
    public float width(String text) {
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            Glyph glyph = this.glyph(text.charAt(i));
            if (glyph == null) {
                glyph = this.glyph('?');
            }
            width += glyph == null ? 0 : glyph.advance();
        }
        return width;
    }

    public GpuTextureView view() {
        return this.view;
    }

    @Override
    public void close() {
        this.view.close();
        this.texture.close();
    }
}

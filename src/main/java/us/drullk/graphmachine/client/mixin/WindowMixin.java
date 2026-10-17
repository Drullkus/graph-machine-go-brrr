package us.drullk.graphmachine.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import us.drullk.graphmachine.client.panel.PanelLayout;

/**
 * Takes the panel's pixels off the framebuffer width that Minecraft believes it has, so the main
 * render target, the GUI scale and every screen fit to the left of the panel.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
@Mixin(Window.class)
abstract class WindowMixin {
    @ModifyReturnValue(method = "framebufferWidthPadding", at = @At("RETURN"))
    private int graphmachine$reservePanel(int original) {
        return original + PanelLayout.reservedFramebufferWidth();
    }
}

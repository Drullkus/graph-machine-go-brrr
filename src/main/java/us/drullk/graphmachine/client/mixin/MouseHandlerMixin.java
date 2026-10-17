package us.drullk.graphmachine.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import us.drullk.graphmachine.client.panel.PanelLayout;

/**
 * Keeps GUI mouse coordinates right while the panel narrows the framebuffer. Vanilla divides by the
 * logical window width; with the panel on, only the part left of it maps onto the GUI.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @ModifyReturnValue(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("RETURN"))
    private static double graphmachine$scaleToMainArea(double original, Window window, double x) {
        int reserved = PanelLayout.reservedFramebufferWidth();
        if (reserved == 0) {
            return original;
        }
        double mainLogicalWidth = window.getScreenWidth() - reserved / PanelLayout.density();
        return mainLogicalWidth <= 0 ? original : x * window.getGuiScaledWidth() / mainLogicalWidth;
    }
}

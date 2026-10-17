package us.drullk.graphmachine.client.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import us.drullk.graphmachine.client.panel.PanelCompositor;

/**
 * Presents the composite texture (main render target plus the panel) instead of the main render target
 * while the panel is shown.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @ModifyArg(
            method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"),
            index = 1
    )
    private GpuTextureView graphmachine$presentComposite(GpuTextureView main) {
        @Nullable GpuTextureView composite = PanelCompositor.compositeView();
        return composite != null ? composite : main;
    }
}

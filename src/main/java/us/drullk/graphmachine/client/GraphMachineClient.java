package us.drullk.graphmachine.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;
import us.drullk.graphmachine.GraphMachine;
import us.drullk.graphmachine.client.data.GraphAssetsLoader;
import us.drullk.graphmachine.client.panel.GraphPanel;
import us.drullk.graphmachine.client.panel.GraphSettingsStore;
import us.drullk.graphmachine.client.panel.ImGuiRenderer;
import us.drullk.graphmachine.client.panel.PanelCompositor;
import us.drullk.graphmachine.client.panel.PanelInput;
import us.drullk.graphmachine.client.panel.PanelLayout;
import us.drullk.graphmachine.client.render.GraphPipelines;
import us.drullk.graphmachine.client.render.GraphRenderer;

/**
 * Client entry point: wires the asset loader, the render pipelines, the graph renderer, the panel and
 * the F12 key mapping. This class only loads on the physical client.
 *
 * <p>Frame order: {@link RenderFrameEvent.Pre} decides whether the panel is active (which may narrow
 * the framebuffer) and lets the renderer start builds and uploads; the level stage events draw the
 * graph; {@link RenderFrameEvent.Post} composes the panel next to the finished frame.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
@Mod(value = GraphMachine.MODID, dist = Dist.CLIENT)
public final class GraphMachineClient {
    public static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(GraphMachine.KEY_CATEGORY_ID);
    /** Shows or hides the panel; F12 by default. */
    public static final KeyMapping TOGGLE_PANEL = new KeyMapping(GraphMachine.TOGGLE_PANEL_KEY, KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYBOARD, InputConstants.KEY_F12, KEY_CATEGORY);

    private final GraphRenderer renderer = new GraphRenderer();
    private final GraphPanel panel = new GraphPanel(this.renderer);
    private final @Nullable AutoScreenshot autoScreenshot = AutoScreenshot.fromSystemProperty();

    public GraphMachineClient(IEventBus modBus) {
        GraphSettingsStore.load();

        modBus.addListener(GraphMachineClient::registerPipelines);
        modBus.addListener(GraphMachineClient::registerKeyMappings);
        modBus.addListener(GraphMachineClient::addReloadListeners);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(this::onFrameStart);
        gameBus.addListener(this.renderer::onOpaque);
        gameBus.addListener(this.renderer::onOpaqueFeatures);
        gameBus.addListener(this::onFrameEnd);
        gameBus.addListener(GraphMachineClient::onClientTick);
        PanelInput.register(gameBus);
    }

    private static void registerPipelines(RegisterRenderPipelinesEvent event) {
        GraphPipelines.register(event);
        ImGuiRenderer.registerPipeline(event);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);
        event.register(TOGGLE_PANEL);
    }

    private static void addReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(GraphAssetsLoader.ID, new GraphAssetsLoader());
    }

    /** Flips the panel between shown and hidden. */
    public static void togglePanel() {
        GraphSettingsStore.update(settings -> settings.withPanelVisible(!settings.panelVisible()));
    }

    private void onFrameStart(RenderFrameEvent.Pre event) {
        PanelLayout.update(GraphRenderer.shouldDraw() && GraphSettingsStore.current().panelVisible());
        this.renderer.onFrameStart(event);
    }

    private void onFrameEnd(RenderFrameEvent.Post event) {
        PanelCompositor.compose(this.panel);
        if (this.autoScreenshot != null) {
            this.autoScreenshot.onFrameEnd(this.renderer);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE_PANEL.consumeClick()) {
            if (GraphRenderer.shouldDraw()) {
                togglePanel();
            }
        }
    }
}

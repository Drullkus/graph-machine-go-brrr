package us.drullk.graphmachine.datagen;

import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.PackGenerator;
import us.drullk.graphmachine.GraphMachine;

/**
 * Registers every data provider of the mod with the {@code runData} task.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
@EventBusSubscriber(modid = GraphMachine.MODID)
public final class GraphMachineDatagen {
    /** Horizontal scale applied to {@code layout.csv}. */
    private static final float HORIZONTAL_SCALE = 2;
    /** Vertical scale applied to {@code layout.csv}. */
    private static final float VERTICAL_SCALE = 4;
    /** Height of the graph's center above {@code y = 0}, in blocks. The floor sits at 64. */
    private static final float GRAPH_Y = 128;

    private GraphMachineDatagen() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        PackGenerator pack = event.getDefaultPackGenerator();
        // The dimension type and the world preset must exist before the tag provider validates its entry.
        pack.createWorldRegistryObjects(new RegistrySetBuilder()
                .add(Registries.DIMENSION_TYPE, GraphDimensionTypeBootstrap::bootstrap)
                .add(Registries.WORLD_PRESET, GraphWorldPresets::bootstrap));
        pack.createProvider(GraphWorldPresetTagsProvider::new);
        pack.createProvider(GraphLanguageProvider::new);
        GraphDataProvider nodes = pack.createProvider((output, _) -> new GraphDataProvider(output, HORIZONTAL_SCALE, VERTICAL_SCALE, GRAPH_Y));
        pack.createProvider((output, _) -> new GraphEdgesProvider(output, nodes));
        pack.createProvider((output, _) -> new GraphClustersProvider(output));
    }
}

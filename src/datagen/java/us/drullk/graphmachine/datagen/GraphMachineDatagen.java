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
 */
@EventBusSubscriber(modid = GraphMachine.MODID)
public final class GraphMachineDatagen {
    private GraphMachineDatagen() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        PackGenerator pack = event.getDefaultPackGenerator();
        // The world preset must exist before the tag provider validates its entry.
        pack.createWorldRegistryObjects(new RegistrySetBuilder().add(Registries.WORLD_PRESET, GraphWorldPresets::bootstrap));
        pack.createProvider(GraphWorldPresetTagsProvider::new);
        pack.createProvider(GraphLanguageProvider::new);
        pack.createProvider(GraphDataProvider::new);
    }
}

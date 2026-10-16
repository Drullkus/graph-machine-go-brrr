package us.drullk.graphmachine.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import us.drullk.graphmachine.GraphMachine;
import us.drullk.graphmachine.worldgen.GraphPresets;

/**
 * Writes {@code assets/graphmachine/lang/en_us.json}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 */
public final class GraphLanguageProvider extends LanguageProvider {
    public GraphLanguageProvider(PackOutput output) {
        super(output, GraphMachine.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        this.add(GraphPresets.GRAPH.identifier().toLanguageKey("generator"), "Class Graph");
    }
}

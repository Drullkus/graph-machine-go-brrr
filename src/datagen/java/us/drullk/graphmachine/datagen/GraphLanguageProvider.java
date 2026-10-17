package us.drullk.graphmachine.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import us.drullk.graphmachine.GraphMachine;
import us.drullk.graphmachine.worldgen.GraphPresets;

/**
 * Writes {@code assets/graphmachine/lang/en_us.json}. The ImGui panel uses plain strings, so only the
 * world preset and the key mapping have translation keys.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphLanguageProvider extends LanguageProvider {
    public GraphLanguageProvider(PackOutput output) {
        super(output, GraphMachine.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        this.add(GraphPresets.GRAPH.identifier().toLanguageKey("generator"), "Class Graph");
        this.add(GraphMachine.KEY_CATEGORY_ID.toLanguageKey("key.category"), "Graph Machine");
        this.add(GraphMachine.TOGGLE_PANEL_KEY, "Toggle Graph Panel");
    }
}

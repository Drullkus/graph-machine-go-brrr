package us.drullk.graphmachine.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.WorldPresetTagsProvider;
import net.minecraft.tags.WorldPresetTags;
import us.drullk.graphmachine.GraphMachine;
import us.drullk.graphmachine.worldgen.GraphPresets;

/**
 * Puts the graph preset into {@code #minecraft:extended}, the list the world type button shows while Alt is held.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 */
public final class GraphWorldPresetTagsProvider extends WorldPresetTagsProvider {
    public GraphWorldPresetTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, GraphMachine.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(WorldPresetTags.EXTENDED).add(GraphPresets.GRAPH);
    }
}

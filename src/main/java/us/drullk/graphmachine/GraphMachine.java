package us.drullk.graphmachine;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import us.drullk.graphmachine.worldgen.GraphChunkGenerator;

/**
 * Mod entry point. Registers the graph chunk generator. Everything that draws the graph lives on the
 * client, in {@code us.drullk.graphmachine.client}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
@Mod(GraphMachine.MODID)
public class GraphMachine {
    public static final String MODID = "graphmachine";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Id of the key mapping category; its label key is {@code key.category.graphmachine.graphmachine}. */
    public static final Identifier KEY_CATEGORY_ID = Identifier.fromNamespaceAndPath(MODID, MODID);
    /** Translation key of the key mapping that shows and hides the panel. */
    public static final String TOGGLE_PANEL_KEY = "key." + MODID + ".toggle_panel";

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);

    /** Codec of the chunk generator that builds the graph world's floor; its id is {@code graphmachine:graph}. */
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<GraphChunkGenerator>> GRAPH_GENERATOR = CHUNK_GENERATORS.register("graph", () -> GraphChunkGenerator.CODEC);

    public GraphMachine(IEventBus modBus) {
        CHUNK_GENERATORS.register(modBus);
    }
}

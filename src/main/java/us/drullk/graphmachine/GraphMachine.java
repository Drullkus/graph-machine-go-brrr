package us.drullk.graphmachine;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import us.drullk.graphmachine.graph.GraphDataLoader;
import us.drullk.graphmachine.worldgen.GraphChunkGenerator;

/**
 * Mod entry point. Registers the graph chunk generator and the loader for the graph data.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 */
@Mod(GraphMachine.MODID)
public class GraphMachine {
    public static final String MODID = "graphmachine";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);

    /** Codec of the chunk generator that places the class graph; its id is {@code graphmachine:graph}. */
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<GraphChunkGenerator>> GRAPH_GENERATOR = CHUNK_GENERATORS.register("graph", () -> GraphChunkGenerator.CODEC);

    public GraphMachine(IEventBus modBus) {
        CHUNK_GENERATORS.register(modBus);
        NeoForge.EVENT_BUS.addListener(GraphMachine::addReloadListeners);
    }

    private static void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(GraphDataLoader.ID, new GraphDataLoader());
    }
}

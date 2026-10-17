package us.drullk.graphmachine.client.panel;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import net.minecraft.util.Util;
import net.neoforged.fml.loading.FMLPaths;
import us.drullk.graphmachine.GraphMachine;

/**
 * Holds the current {@link GraphViewSettings} and saves them to {@code config/graphmachine-client.json}.
 *
 * <p>Writes are debounced: a change marks the store dirty, and {@link #saveIfDue()} writes at most once
 * per second. {@link #version()} increases with every change, so the renderer can see that it has to
 * rebuild without comparing records.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphSettingsStore {
    private static final String FILE_NAME = "graphmachine-client.json";
    private static final long SAVE_DELAY_MS = 1000;

    private static GraphViewSettings current = GraphViewSettings.DEFAULT;
    private static int version;
    private static long dirtySinceMs = -1;

    private GraphSettingsStore() {
    }

    public static GraphViewSettings current() {
        return current;
    }

    /** The number of changes since startup; a load counts as one. */
    public static int version() {
        return version;
    }

    /** Replaces the settings and schedules a save when they changed. */
    public static void update(UnaryOperator<GraphViewSettings> change) {
        GraphViewSettings next = change.apply(current);
        if (!next.equals(current)) {
            current = next;
            version++;
            if (dirtySinceMs < 0) {
                dirtySinceMs = Util.getMillis();
            }
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /** Reads the config file; a missing or broken file leaves the defaults. */
    public static void load() {
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        try (var reader = Files.newBufferedReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);
            current = GraphViewSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            version++;
        } catch (IOException | RuntimeException e) {
            GraphMachine.LOGGER.warn("Cannot read {}; using default panel settings", file, e);
        }
    }

    /** Writes the file when a change is at least one second old. */
    public static void saveIfDue() {
        if (dirtySinceMs < 0 || Util.getMillis() - dirtySinceMs < SAVE_DELAY_MS) {
            return;
        }
        dirtySinceMs = -1;
        Path file = file();
        try {
            JsonElement json = GraphViewSettings.CODEC.encodeStart(JsonOps.INSTANCE, current).getOrThrow();
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
        } catch (IOException | RuntimeException e) {
            GraphMachine.LOGGER.warn("Cannot write {}", file, e);
        }
    }
}

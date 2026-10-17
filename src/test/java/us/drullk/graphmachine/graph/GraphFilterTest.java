package us.drullk.graphmachine.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks the panel's substring filter.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
class GraphFilterTest {
    private static final String RENDERER = "net.minecraft.client.renderer.LevelRenderer";

    @Test
    void blankFilterMatchesEverything() {
        assertEquals(List.of(), GraphFilter.terms("   "));
        assertTrue(GraphFilter.matches(RENDERER, GraphFilter.terms("")));
    }

    @Test
    void termsAreCaseInsensitiveSubstrings() {
        assertTrue(GraphFilter.matches(RENDERER, GraphFilter.terms("LEVELrenderer")));
        assertTrue(GraphFilter.matches(RENDERER, GraphFilter.terms("client.renderer")));
        assertFalse(GraphFilter.matches(RENDERER, GraphFilter.terms("server")));
    }

    @Test
    void everyTermMustMatch() {
        List<String> terms = GraphFilter.terms("  client   Level ");
        assertEquals(List.of("client", "level"), terms);
        assertTrue(GraphFilter.matches(RENDERER, terms));
        assertFalse(GraphFilter.matches("net.minecraft.server.level.ServerLevel", terms));
    }
}

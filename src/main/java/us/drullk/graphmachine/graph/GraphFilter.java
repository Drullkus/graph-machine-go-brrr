package us.drullk.graphmachine.graph;

import java.util.List;
import java.util.Locale;

/**
 * The panel's text filter: space-separated terms, each matched as a case-insensitive substring of the
 * full class name. A class matches when it contains every term; an empty filter matches everything.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphFilter {
    private GraphFilter() {
    }

    /** Splits the filter text into lower-case terms; empty for a blank filter. */
    public static List<String> terms(String filter) {
        String trimmed = filter.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
    }

    /** True when {@code className} contains every term. */
    public static boolean matches(String className, List<String> terms) {
        if (terms.isEmpty()) {
            return true;
        }
        String name = className.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (!name.contains(term)) {
                return false;
            }
        }
        return true;
    }
}

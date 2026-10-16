# Graph-Machine-Go-Brrr

NeoForge mod that turns `minecraft.jar` class dependencies into a graph that's navigable in-game.

## Versions

- Minecraft 26.3
- NeoForge 26.3.0.76 (ModDevGradle 2.0.148)
- Java 25 toolchain

## Codebase conventions

- Documentation must be written in simple American English, following principles of ASD-STE100:
  - Unambiguous and instructive language.
  - Present tense and active tone.
- Every package must be annotated with `@org.jspecify.annotations.NullMarked`.

## AI attribution procedure

The repository owner cites AI-generated work, so every session Claude works in must follow these steps.

1. Transcripts live at `llm/transcripts/transcript_{start date, UTC}_{HH-MM, UTC}_{session uuid}.md` and are generated only by the owner, never on Claude's own initiative.
2. Generate one with `python3 llm/tools/claude_transcript_to_md.py` from the repo root; it picks the most recent session for this project and names the file itself.
3. A session may span several commits, so re-run the same command whenever the owner asks for an update and commit the changed transcript alongside the code; Git tracks its history.
4. Every class Claude creates or modifies gets a class-level Javadoc line `<p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript-....md}.` using the repo-relative path (a modified class that already cites an earlier transcript keeps that line and gains one for the new session).
5. Find the path to cite before the transcript exists with `python3 llm/tools/claude_transcript_to_md.py --print-path`.
6. The desktop app sometimes resumes a conversation under a new session uuid; the tool detects this, renames the earlier transcript with `git mv`, and rewrites references in the project's text files, so check its output and stage the renamed file.
7. Do not add the owner's email, token usage, request IDs or Claude's reasoning to anything committed; the tool already strips them from transcripts.

## Transcript tool usage

- `python3 llm/tools/claude_transcript_to_md.py --list` lists the sessions recorded for this project with their uuid, start and end time, and opening prompt.
- `python3 llm/tools/claude_transcript_to_md.py` exports the latest session to `llm/transcripts/`.
- `--session <uuid prefix>` selects a specific session; `--file <path.jsonl>` exports a transcript file directly.
- `--print-path` prints the repo-relative output path without writing anything.
- `--include-results` embeds truncated tool output; `--max-chars N` changes the truncation limit (0 = unlimited); `-o <file>` or `-o -` overrides the destination.
- Sessions are read from `~/.claude/projects/<project path with slashes replaced by dashes>/*.jsonl`; the desktop app's `local_…` session handle is not a file name.

## Graph data pipeline

The class graph is computed offline, never in the running game.

1. `./jdeps-minecraft.sh` runs `jdeps` on the patched Minecraft jar and writes `jdeps-output/class-edges.tsv` (one edge between top-level classes per line) and `jdeps-output/class-nodes.tsv` (every top-level class with its `client` or `common` dist). The raw `.dot` files in that folder are not tracked by git. The jar only exists after a Gradle sync.
2. `python3 tools/jdeps_to_graph.py` reads those files, computes a spectral embedding and k-means clusters, and writes `graph-data/nodes.csv`, `graph-data/clusters.csv` and `graph-data/cluster-edges.csv`. It needs only `numpy`. Pass `--k` to change the cluster count and `--help` for the rest.
3. Nested classes are collapsed into their outer class and `package-info` is dropped. Classes with no edges stay in the node list on purpose.
4. `python3 tools/layout_graph.py` reads `graph-data/nodes.csv`, `graph-data/cluster-edges.csv` and `jdeps-output/class-edges.tsv`, runs a two-level force-directed layout (cluster discs first, then classes inside them with edge springs, grid repulsion and cluster gravity) and writes `graph-data/layout.csv` with columns `class,x,y,z` in block units, `y` vertical. No two classes end closer than 1 block, the main layout spans about 240 blocks, and the 13 isolated classes sit on a ring outside it. It runs in about 20 seconds and needs only `numpy`. Pass `--spacing`, `--gravity`, `--vscale` or `--seed` to change the look and `--help` for the rest.

## Datagen

- Datagen classes must stay in a separate (`src/datagen`) source set and never packed into the mod jar.
- `./gradlew runData` writes into `src/generated/resources` (included in the jar). Commit the generated JSON.
- `assets/noodling/lang/en_us.json` must stay generated.

## Tests

- Test classes must stay in a separate (`src/datagen`) source set and never packed into the mod jar.

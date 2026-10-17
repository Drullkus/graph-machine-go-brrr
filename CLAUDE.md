# Graph-Machine-Go-Brrr

NeoForge mod that turns `minecraft.jar` class dependencies into a graph that's navigable in-game.

## Planning

If the user asks you to plan, please use AskUserQuestions to ensure that all details are exhaustively chosen by the user. Leave nothing to assumption by you.

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
5. `./gradlew runData` writes the client assets under `src/generated/resources/assets/graphmachine/graph/`: `nodes.json` (nodes.csv joined with layout.csv, scaled, with the graph height baked into `y`), `edges.bin` (`jdeps-output/class-edges.tsv` as little-endian int32 node index pairs, nested classes collapsed, one entry per unordered pair) and `clusters.json` (clusters.csv with a fixed color per cluster). It also writes the `graphmachine:graph` dimension type and world preset, the `#minecraft:extended` tag entry and the lang file. Re-run it after every change to the CSV or TSV files and commit the result.

## In-game rendering

The graph is drawn on the client only; the server generates a flat floor and nothing else. Worlds from before this change still hold display entities; create a new world.

- The world preset `graphmachine:graph` is in `#minecraft:extended`. In the world creation screen, hold Alt while cycling the world type to reach it. Its overworld uses the `graphmachine:graph` dimension type (a copy of the overworld), and that dimension type is how the client knows to draw the graph and show the panel.
- `GraphAssetsLoader` (client reload listener) loads the three assets. `GraphViewBuilder` runs on a worker thread and compacts the visible nodes, glyphs and edges into instance buffers; `GraphGpuBuffers` uploads them at most 4 MiB per frame into a back buffer set and swaps; `GraphRenderer` draws nodes and labels in `RenderLevelStageEvent.AfterOpaqueBlocks` and edges in `AfterOpaqueFeatures` (the translucent stage event only fires with classic transparency) with one `drawIndirect` per stream (direct draws on devices without indirect support).
- All geometry comes from `gl_VertexIndex`; the only vertex binding is the per-instance stream at slot 0 with step rate 1. Shaders live in `assets/graphmachine/shaders/core/` and share `include/graphinfo.glsl`, which must match the `Std140Builder` order in `GraphRenderer.writeUniforms`. On macOS OpenGL there is no multi-draw indirect and no base instance, so every draw uses `firstInstance = 0`; sub-ranges are selected with vertex buffer slices.
- Labels use `GraphGlyphAtlas`: the printable ASCII glyphs of the default font copied out of vanilla's font pages into one texture at the first frame after a resource reload. The pages are read back to the CPU and the glyph rectangles written with `writeToTexture`, because vanilla's OpenGL `copyTextureToTexture` blits a wrong rectangle for any offset other than the origin. Do not use `copyTextureToTexture` with offsets.
- `src/main/resources/META-INF/accesstransformer.cfg` opens `Window.refreshFramebufferSize`, `Font.getGlyphSource` and the `BakedSheetGlyph` fields. Add new entries there instead of using reflection.

## ImGui panel

- Dear ImGui comes from imgui-java (binding plus natives, nested with Jar-in-Jar). Only the binding is used: `ImGuiRenderer` draws through the GPU abstraction and `ImGuiInput` maps SDL scancodes.
- The panel never scales with GUI Scale. Everything is sized in points times `Window.getPixelDensity()` (2 on Retina, 1 on a standard display); `GraphImGuiContext` rebuilds the context with a 14 pt font times that density whenever the density changes.
- `PanelLayout` reserves the panel's framebuffer pixels. Three mixins (`graphmachine.mixins.json`) make the strip real: `WindowMixin` adds the reserved width to the framebuffer padding so the main render target, GUI scale and screens shrink; `MouseHandlerMixin` scales GUI mouse coordinates to the narrowed area; `MinecraftMixin` presents the composite texture that `PanelCompositor` builds in `RenderFrameEvent.Post` (main target copied left, ImGui drawn right).
- The panel is drawn only in the graph dimension and is interactive only while a screen is open (open the chat with T). F12 (`key.graphmachine.toggle_panel`) hides and shows it. Settings persist in `config/graphmachine-client.json` through `GraphSettingsStore`.
- Panel strings are plain literals in `GraphPanel`; only the key mapping and the world preset have translation keys.

## Checking the renderer without a keyboard

- `./gradlew runClientGraph` quick-plays the save `run/saves/Graph Test` (another save with `-Pgraphmachine.save=Name`) and, 8 seconds after the graph finished applying, writes `run/screenshots/graphmachine-auto.png` with what the window shows (graph plus panel). The save must exist: create a world named `Graph Test` with the Class Graph preset once. Settings for the run can be prepared in `run/config/graphmachine-client.json`. The same property also dumps the label atlas to `run/screenshots/graphmachine-atlas.png`.

## Datagen

- Datagen classes must stay in a separate (`src/datagen`) source set and never packed into the mod jar.
- `./gradlew runData` writes into `src/generated/resources` (included in the jar). Commit the generated JSON.
- `assets/graphmachine/lang/en_us.json` must stay generated.

## Tests

- JUnit tests live in `src/test` and run with `./gradlew test` through the FML JUnit launcher; they are never packed into the mod jar. Keep them on pure classes (the `graph` package); the client classes need a running game.

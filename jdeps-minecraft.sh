#!/usr/bin/env bash
# Extracts the class dependency graph of the Minecraft jar with jdeps.
#
# Writes into jdeps-output/:
#   <jar>.dot, summary.dot   raw jdeps output (not tracked by git)
#   class-edges.tsv          "source<TAB>target", one line per edge between top-level
#                            classes of the jar; nested classes are collapsed into
#                            their outer class, package-info and self-loops are dropped
#   class-nodes.tsv          "class<TAB>dist" for every top-level class in the jar,
#                            including classes with no edges; dist is "client" when the
#                            jar manifest marks the class client-only, else "common"
#
# Run from any directory; the jar must exist, so run a Gradle sync first.
#
# Written by Claude Code; conversation transcript: llm/transcripts/transcript_2026-10-16_5c2c690e-3be6-49dd-9724-3b112e8d00c0.md
set -euo pipefail
cd "$(dirname "$0")"

# Java SE's built-in class dependency analyzer:
# https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdeps.html
neo_version=$(sed -n 's/^neo_version=//p' gradle.properties)
jar="build/moddev/artifacts/minecraft-patched-${neo_version}.jar"
jar_name="${jar##*/}"
out=jdeps-output
[[ -f "$jar" ]] || { echo "missing $jar; run a Gradle sync first" >&2; exit 1; }
mkdir -p "$out"

# -verbose is -verbose:class -filter:none: class-level edges, same-package edges included.
jdeps -verbose --dot-output "$out" "$jar"

tab=$'\t'
export LC_ALL=C

# Edges. Keep only targets inside the Minecraft jar, then normalize the class names.
sed -nE "s/^ *\"([^\"]+)\" *-> *\"([^\" ]+) \(${jar_name}\)\";\$/\1${tab}\2/p" "$out/${jar_name}.dot" \
  | awk -F'\t' -v OFS='\t' '
      { sub(/\$.*/, "", $1); sub(/\$.*/, "", $2) }
      $1 != $2 && $1 !~ /\.package-info$/ && $2 !~ /\.package-info$/ { print }' \
  | sort -u > "$out/class-edges.tsv"

# Nodes. Every top-level class in the jar, joined with its dist from the manifest.
unzip -Z1 "$jar" '*.class' \
  | sed -E 's#/#.#g; s/\.class$//; s/\$.*//' \
  | grep -vE '\.(package|module)-info$' \
  | sort -u > "$out/.nodes.tmp"

# The manifest uses CRLF line endings and wraps long lines; a line starting with a
# space continues the previous one.
unzip -p "$jar" META-INF/MANIFEST.MF \
  | tr -d '\r' \
  | awk -v OFS='\t' '
      function handle(line) {
        if (line ~ /^Name: /) { name = substr(line, 7) }
        else if (line ~ /^Minecraft-Dist: / && name ~ /\.class$/ && name !~ /\$/) {
          sub(/\.class$/, "", name); gsub("/", ".", name)
          print name, substr(line, 17)
        }
      }
      /^ / { buf = buf substr($0, 2); next }
      { if (buf != "") handle(buf); buf = $0 }
      END { if (buf != "") handle(buf) }' \
  | sort -u > "$out/.dist.tmp"

join -t "$tab" -a 1 -e common -o 1.1,2.2 "$out/.nodes.tmp" "$out/.dist.tmp" > "$out/class-nodes.tsv"
rm -f "$out/.nodes.tmp" "$out/.dist.tmp"

du -h "$out"/*
printf 'edges: %s\nnodes: %s\n' "$(wc -l < "$out/class-edges.tsv")" "$(wc -l < "$out/class-nodes.tsv")"

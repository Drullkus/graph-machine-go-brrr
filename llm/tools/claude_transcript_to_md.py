#!/usr/bin/env python3
"""Export a Claude Code session transcript to Markdown for citation.

Claude Code (including the Claude desktop app's Code tab) stores every session as a JSONL file under
    ~/.claude/projects/<escaped project path>/<session uuid>.jsonl
where the escaped path is the absolute project directory with every "/" replaced by "-".

This script turns one of those files into a readable Markdown transcript containing only:
  * the prompts you typed,
  * Claude's visible replies,
  * the tool calls Claude made (name, description, and the command / file / arguments),
  * optionally, truncated tool results.
It deliberately drops reasoning ("thinking") blocks, token usage, cost, request IDs, hook output,
and the context the harness injects into prompts (system reminders), so the result is a clean
record of the human/AI exchange rather than a dump of internal metadata.

By default the transcript is written to
    llm/transcripts/transcript-<start date, UTC>-<session uuid>.md
inside the project. If the desktop app resumed the conversation into a new session uuid (it copies
the whole history into a fresh file), the previously exported file for the same conversation is
renamed to the new uuid and every reference to the old path in the project's text files is rewritten.

Usage:
    python3 llm/tools/claude_transcript_to_md.py --list
    python3 llm/tools/claude_transcript_to_md.py                 # export the latest session to llm/transcripts/
    python3 llm/tools/claude_transcript_to_md.py --print-path    # just print where the latest session would go
    python3 llm/tools/claude_transcript_to_md.py --session 17647dd8 --include-results -o out.md

This tool was itself written by Claude Code; see https://github.com/Drullkus/noodling/blob/main/llm/transcripts/transcript_2026-10-06_17647dd8-8de1-4b0b-b68d-89a2d0ee63a8.md
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

SYSTEM_REMINDER = re.compile(r"<system-reminder>.*?</system-reminder>\s*", re.DOTALL)
PASTED_TAGS = re.compile(r"</?pasted_content[^>]*>")
TRANSCRIPT_DIR = Path("llm") / "transcripts"
TEXT_SUFFIXES = {".java", ".kt", ".md", ".txt", ".gradle", ".kts", ".properties", ".json", ".toml", ".py", ".yml", ".yaml"}
SKIP_DIRS = {".git", ".gradle", ".idea", "build", "run", "out", "node_modules"}


def project_dir(project: Path) -> Path:
    escaped = str(project.resolve()).replace("/", "-")
    return Path.home() / ".claude" / "projects" / escaped


def read_records(path: Path):
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    yield json.loads(line)
                except json.JSONDecodeError:
                    continue


def is_prompt(r: dict) -> bool:
    return r.get("type") == "user" and not r.get("isMeta") and not r.get("isSidechain") \
        and isinstance(r.get("message", {}).get("content"), str)


def session_summary(path: Path) -> dict:
    title = None
    first_prompt = None
    first_ts = last_ts = None
    prompts = 0
    for r in read_records(path):
        t = r.get("type")
        if t == "custom-title":
            title = r.get("customTitle")
        elif is_prompt(r):
            prompts += 1
            if first_prompt is None:
                first_prompt = clean_user_text(r["message"]["content"])
        ts = r.get("timestamp")
        if ts and t in ("user", "assistant"):
            first_ts = first_ts or ts
            last_ts = ts
    return {
        "id": path.stem,
        "path": path,
        "title": title,
        "first_prompt": first_prompt,
        "prompts": prompts,
        "start": first_ts,
        "end": last_ts,
    }


def clean_user_text(text: str) -> str:
    text = SYSTEM_REMINDER.sub("", text)
    text = PASTED_TAGS.sub("", text)
    return text.strip()


def fence(body: str, lang: str = "") -> str:
    # Pick a fence longer than any run of backticks inside the body so it cannot be broken.
    longest = max((len(m) for m in re.findall(r"`+", body)), default=0)
    ticks = "`" * max(3, longest + 1)
    return f"{ticks}{lang}\n{body.rstrip()}\n{ticks}"


def truncate(text: str, limit: int) -> str:
    if limit <= 0 or len(text) <= limit:
        return text
    return text[:limit].rstrip() + f"\n… [{len(text) - limit} more characters omitted]"


def render_tool_use(block: dict, max_chars: int) -> str:
    name = block.get("name", "?")
    inp = block.get("input") or {}
    desc = inp.get("description")
    head = f"**Tool call: `{name}`**" + (f" — {desc}" if desc else "")
    body = []
    if name == "Bash" and "command" in inp:
        body.append(fence(truncate(inp["command"], max_chars), "bash"))
    elif name in ("Write", "Edit") and "file_path" in inp:
        body.append(f"File: `{inp['file_path']}`")
        if name == "Write":
            body.append(fence(truncate(inp.get("content", ""), max_chars)))
        else:
            body.append("Replace:")
            body.append(fence(truncate(inp.get("old_string", ""), max_chars)))
            body.append("With:")
            body.append(fence(truncate(inp.get("new_string", ""), max_chars)))
    elif name == "Read" and "file_path" in inp:
        body.append(f"File: `{inp['file_path']}`")
    else:
        rest = {k: v for k, v in inp.items() if k != "description"}
        if rest:
            body.append(fence(truncate(json.dumps(rest, indent=2, ensure_ascii=False), max_chars), "json"))
    inner = "\n\n".join(body)
    return f"<details>\n<summary>{head}</summary>\n\n{inner}\n\n</details>" if inner else head


def tool_result_text(block: dict) -> str:
    content = block.get("content")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for c in content:
            if isinstance(c, dict) and c.get("type") == "text":
                parts.append(c.get("text", ""))
            elif isinstance(c, dict):
                parts.append(f"[{c.get('type')} content omitted]")
        return "\n".join(parts)
    return ""


def parse_ts(ts: str | None) -> dt.datetime | None:
    if not ts:
        return None
    try:
        return dt.datetime.fromisoformat(ts.replace("Z", "+00:00"))
    except ValueError:
        return None


def fmt_ts(ts: str | None) -> str:
    d = parse_ts(ts)
    return d.strftime("%Y-%m-%d %H:%M:%S UTC") if d else (ts or "unknown")


def export(path: Path, include_results: bool, max_chars: int, self_path: str | None) -> str:
    title = None
    models: dict[str, int] = {}
    versions: set[str] = set()
    cwd = branch = None
    start = end = None
    tool_names: dict[str, str] = {}  # tool_use_id -> name, for labelling results
    turns: list[list[str]] = []
    counts = {"prompts": 0, "replies": 0, "tool_calls": 0}

    for r in read_records(path):
        t = r.get("type")
        ts = r.get("timestamp")
        if ts and t in ("user", "assistant"):
            start = start or ts
            end = ts
        if t == "custom-title":
            title = r.get("customTitle") or title
            continue
        if t not in ("user", "assistant") or r.get("isSidechain"):
            continue
        cwd = cwd or r.get("cwd")
        branch = branch or r.get("gitBranch")
        if r.get("version"):
            versions.add(r["version"])
        msg = r.get("message") or {}
        content = msg.get("content")

        if t == "user":
            if r.get("isMeta"):
                continue
            if isinstance(content, str):
                text = clean_user_text(content)
                if not text:
                    continue
                counts["prompts"] += 1
                turns.append([f"----\n## Prompt {counts['prompts']} — {fmt_ts(ts)}\n\n### User:\n\n{text}\n\n----"])
                continue
            if not isinstance(content, list):
                continue
            texts = [clean_user_text(b.get("text", "")) for b in content if b.get("type") == "text"]
            texts = [x for x in texts if x]
            if texts:
                counts["prompts"] += 1
                turns.append([f"----\n## Prompt {counts['prompts']} — {fmt_ts(ts)}\n\n### User:\n\n" + "\n\n".join(texts) + "\n\n----"])
            if include_results and turns:
                for b in content:
                    if b.get("type") != "tool_result":
                        continue
                    name = tool_names.get(b.get("tool_use_id"), "tool")
                    flag = " (error)" if b.get("is_error") else ""
                    body = truncate(tool_result_text(b), max_chars)
                    turns[-1].append(f"<details>\n<summary>Result of `{name}`{flag}</summary>\n\n{fence(body)}\n\n</details>")
            continue

        # assistant
        model = msg.get("model")
        if model:
            models[model] = models.get(model, 0) + 1
        if not isinstance(content, list) or not turns:
            continue
        for b in content:
            kind = b.get("type")
            if kind == "text":
                text = (b.get("text") or "").strip()
                if text:
                    counts["replies"] += 1
                    turns[-1].append(f"### Claude:\n\n{text}")
            elif kind == "tool_use":
                counts["tool_calls"] += 1
                tool_names[b.get("id")] = b.get("name", "tool")
                turns[-1].append(render_tool_use(b, max_chars))
            # "thinking" and any other block types are intentionally omitted.

    header = [
        f"# {title or 'Claude Code session transcript'}",
        "",
        "| Field | Value |",
        "|---|---|",
        "| Tool | Claude Code (Claude desktop app) |",
        f"| Model | {', '.join(sorted(models)) or 'unknown'} |",
        f"| Claude Code version | {', '.join(sorted(versions)) or 'unknown'} |",
        f"| Session transcript ID | `{path.stem}` |",
        f"| Project | `{cwd or 'unknown'}` (branch `{branch or '?'}`) |",
        f"| Started | {fmt_ts(start)} |",
        f"| Ended | {fmt_ts(end)} |",
        f"| Prompts / replies / tool calls | {counts['prompts']} / {counts['replies']} / {counts['tool_calls']} |",
    ]
    if self_path:
        header.append(f"| Repository path | `{self_path}` |")
    header += [
        "",
        "Generated by `llm/tools/claude_transcript_to_md.py` from the local Claude Code transcript. "
        "Reasoning blocks, token usage, request identifiers, hook output and harness-injected context are omitted"
        + ("; tool results are included, truncated." if include_results else "; tool results are omitted."),
        "",
    ]
    return "\n".join(header) + "\n" + "\n\n".join("\n\n".join(turn) for turn in turns) + "\n"


def default_relative_path(summary: dict) -> Path:
    started = parse_ts(summary["start"])
    date = started.strftime("%Y-%m-%d") if started else "unknown-date"
    return TRANSCRIPT_DIR / f"transcript_{date}_{summary['id']}.md"


def find_superseded(project: Path, summary: dict, new_rel: Path) -> Path | None:
    """An earlier export of the same conversation under a different session uuid, if any.

    The desktop app resumes a conversation by copying its history into a new session file, so the start date and
    opening prompt stay identical while the uuid changes.
    """
    directory = project / TRANSCRIPT_DIR
    if not directory.is_dir() or not summary["first_prompt"]:
        return None
    started = parse_ts(summary["start"])
    date = started.strftime("%Y-%m-%d") if started else None
    if not date:
        return None
    needle = summary["first_prompt"][:300]
    for candidate in sorted(directory.glob(f"transcript-{date}-*.md")):
        if candidate.name == new_rel.name:
            continue
        try:
            text = candidate.read_text(encoding="utf-8")
        except OSError:
            continue
        if needle in text:
            return candidate
    return None


def rename_with_git(project: Path, old: Path, new: Path) -> None:
    if shutil.which("git") and (project / ".git").exists():
        result = subprocess.run(["git", "-C", str(project), "mv", "-k", str(old), str(new)], capture_output=True, text=True)
        if result.returncode == 0 and new.exists() and not old.exists():
            return
    old.replace(new)


def rewrite_references(project: Path, old_rel: str, new_rel: str) -> list[Path]:
    changed = []
    for path in project.rglob("*"):
        if any(part in SKIP_DIRS for part in path.parts) or not path.is_file() or path.suffix not in TEXT_SUFFIXES:
            continue
        if path.is_relative_to(project / TRANSCRIPT_DIR):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        if old_rel in text:
            path.write_text(text.replace(old_rel, new_rel), encoding="utf-8")
            changed.append(path)
    return changed


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--project", type=Path, default=Path.cwd(), help="project directory the session was run in (default: cwd)")
    ap.add_argument("--list", action="store_true", help="list the sessions recorded for the project and exit")
    ap.add_argument("--print-path", action="store_true", help="print the repo-relative transcript path for the selected session and exit")
    sel = ap.add_mutually_exclusive_group()
    sel.add_argument("--session", help="session transcript UUID, or a unique prefix of it")
    sel.add_argument("--latest", action="store_true", help="export the most recently modified session (default)")
    sel.add_argument("--file", type=Path, help="export this JSONL file directly")
    ap.add_argument("-o", "--out", type=Path, help="output file; '-' for stdout (default: llm/transcripts/transcript-<date>-<uuid>.md)")
    ap.add_argument("--include-results", action="store_true", help="include tool results, truncated")
    ap.add_argument("--max-chars", type=int, default=4000, help="truncate commands, file contents and results to this many characters (0 = no limit)")
    args = ap.parse_args(argv)
    project = args.project.resolve()

    if args.file:
        path = args.file
    else:
        pdir = project_dir(project)
        files = sorted(pdir.glob("*.jsonl"), key=lambda p: p.stat().st_mtime, reverse=True)
        if not files:
            print(f"No transcripts found in {pdir}", file=sys.stderr)
            return 1
        if args.list:
            for s in map(session_summary, files):
                prompt = (s["first_prompt"] or "").replace("\n", " ")[:80]
                print(f"{s['id']}  {fmt_ts(s['start'])} → {fmt_ts(s['end'])}  prompts={s['prompts']:<3} {s['title'] or ''}\n    {prompt}")
            return 0
        if args.session:
            matches = [p for p in files if p.stem.startswith(args.session)]
            if len(matches) != 1:
                print(f"--session {args.session!r} matches {len(matches)} transcripts; use --list", file=sys.stderr)
                return 1
            path = matches[0]
        else:
            path = files[0]

    summary = session_summary(path)
    rel = default_relative_path(summary)
    if args.print_path:
        print(rel.as_posix())
        return 0

    if args.out and str(args.out) == "-":
        sys.stdout.write(export(path, args.include_results, args.max_chars, None))
        return 0

    if args.out:
        out = args.out
        self_path = out.as_posix()
    else:
        out = project / rel
        self_path = rel.as_posix()
        old = find_superseded(project, summary, rel)
        if old is not None:
            old_rel = old.relative_to(project).as_posix()
            rename_with_git(project, old, out)
            changed = rewrite_references(project, old_rel, self_path)
            print(f"Session was resumed under a new uuid: renamed {old_rel} -> {self_path}", file=sys.stderr)
            for c in changed:
                print(f"  updated reference in {c.relative_to(project).as_posix()}", file=sys.stderr)

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(export(path, args.include_results, args.max_chars, self_path), encoding="utf-8")
    print(f"Wrote {self_path} from {path}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

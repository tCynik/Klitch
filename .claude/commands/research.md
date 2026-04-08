# MeshTactics Research

You are the research agent for the MeshTactics project. Your job is to investigate external topics (APIs, frameworks, protocols, libraries) and return **only compact, actionable findings** — not raw web content.

**Language rules:**
- Chat output (summary, status messages) — match the language of the request.
- Saved `.md` files in `.claude/research/` — always in English, regardless of request language.

---

## Purpose

This skill exists to protect the main conversation context from web search noise. All `WebSearch` and `WebFetch` calls happen inside this skill. The main conversation receives only the distilled summary.

---

## Input

`$ARGUMENTS` — research topic or question. Examples:
- `Meshtastic BLE packet format for position telemetry`
- `MapLibre Android: PointAnnotation click events`
- `SQLDelight 2.0: async queries with coroutines`

---

## Process

### Step 1. Scope the research

From `$ARGUMENTS`, identify:
- What exactly needs to be answered (1–3 specific questions)
- What you already know from project context (do not re-research confirmed patterns)
- What sources are most likely authoritative (official docs, GitHub, spec)

### Step 2. Research using subagent

Spawn a **general-purpose agent** with:
- The specific questions to answer
- Instruction to use `WebSearch` + `WebFetch` for authoritative sources (official docs, GitHub README/source, spec pages)
- Instruction to return findings as **bullet points only** — no raw HTML, no long quotes, no reproduced article text
- Max 3 sources per question

The subagent does all web fetching. You receive only its summary.

### Step 3. Synthesize findings

From the subagent output, produce the final research report:

```
## Research: <topic>

### Findings
- <concrete fact or API detail>
- <concrete fact or API detail>
- ...

### Constraints for this project
- <what this means for MeshTactics specifically>
- ...

### Open questions (unresolved)
- <anything the research did not answer>

### Sources
- <name>: <URL> (one line per source, max 5)
```

**Hard limits:**
- Findings: max 10 bullets
- Constraints: max 5 bullets
- Total output: fit in one screen

### Step 4. Save if requested

If the research is for a specific feature plan, save to `.claude/research/<topic-slug>.md` and report the path.

---

## Token discipline

- Do NOT reproduce article text, documentation paragraphs, or code samples verbatim — summarize
- Do NOT search for things already established in `architect.md` canonical patterns
- Do NOT include "background" or "introduction" sections — findings only
- If a question is unanswerable from available sources, say so in one line and move on

---

## Integration with `/planner`

This skill is the **only** way to do web research in Phase 0. After `/research` completes:
1. The findings are saved to `.claude/research/<topic-slug>.md`
2. The main conversation receives only the summary (one message)
3. The user runs `/compact` before Phase 1 begins — this discards the research traffic from context

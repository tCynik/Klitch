# MeshTactics Debug Log

You are the debug log skill for the MeshTactics project. Your only job is to capture and retrieve debugging history for feature areas. You do not diagnose, fix, or change code.

**Language rules:**
- Chat output — match the language of the request.
- Debug log files (`.claude/debug/`) — always in English.

---

## Purpose

`.claude/debug/<feature-slug>.md` is a chronological log of all debugging sessions for a feature area. It prevents circular debugging — repeated hypotheses, regressions to previously-tried approaches, and lost context across compacted conversations.

---

## Commands

Determine action from `$ARGUMENTS`:

- **`save:`** — append a new debug entry to the log for the given feature
- **`show:`** — read and summarize the debug history for a feature
- Anything else — ask which action fits

---

## SAVE

**Request**: $ARGUMENTS (format: `save: <feature-slug>`)

### Step 1. Identify the feature slug

Derive from `$ARGUMENTS` or from the current conversation context if not explicit.

### Step 2. Gather entry data

Derive from the current conversation context:
- **Problem**: one sentence — what symptom or behaviour was being debugged
- **Tried**: list of approaches attempted — each with commit hash if available, and why it didn't work (or what it revealed)
- **Result**: `fixed (commit <hash>)` | `partially fixed` | `unresolved` | `root cause identified, fix deferred`

If context is insufficient, ask the user for missing fields only. Confirm the full entry before writing.

### Step 3. Write the entry

Open or create `.claude/debug/<feature-slug>.md`.

**File header** (first time only):
```
# Debug History: <Feature Name>
Ref: [.claude/docs/<feature-slug>.md](.claude/docs/<feature-slug>.md)

---
```

**Entry format:**
```
## <YYYY-MM-DD> | <Problem statement>

**Symptom:** <what was observed>
**Tried:**
- <approach> (`<commit>`) — <why it didn't work / what it revealed>
- <approach> (`<commit>`) — <why it didn't work / what it revealed>
**Result:** <fixed/unresolved/etc> — <one sentence on root cause or final fix>

---
```

Append the new entry at the bottom of the file. Never edit existing entries.

Pseudocode is allowed in **Tried** items when the approach cannot be described in plain words — keep it to 1–2 lines maximum.

### Step 4. Confirm

Report: file path and the entry as written. One line.

---

## SHOW

**Request**: $ARGUMENTS (format: `show: <feature-slug>`)

Read `.claude/debug/<feature-slug>.md` and produce:

```
Feature: <name>
Entries: <count> | Last debug: <date>

History:
- <date> — <problem> → <result>
- <date> — <problem> → <result>
...

Recurring patterns:
<any hypothesis or approach that appears more than once — flag as "tried before, didn't work">
```

If the file does not exist: "No debug history for `<feature-slug>` yet."

---

## Auto-check (called by /iterate)

When `/iterate bug:` reaches Entry Protocol step 3 (debug history check):

1. Check if `.claude/debug/<feature-slug>.md` exists
2. If yes — run SHOW and present the result inline; highlight recurring patterns prominently
3. If no — silently continue (do not interrupt the flow)

This surfaces past failed approaches before a new fix attempt begins.

---

## Principles

- **Append only.** Never edit or delete existing entries — they are the historical record.
- **Brief over complete.** Each entry should be scannable in 30 seconds. Code details live in commits.
- **Honest results.** If the problem was not fixed, say so — an `unresolved` entry is more valuable than a false `fixed`.
- **Feature-scoped.** One file per feature slug, matching the slug used in `.claude/docs/`.

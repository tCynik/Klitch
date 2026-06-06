# MeshTactics Iterate

You are the iteration and debugging skill for the MeshTactics project. Your job is to guide work on an existing feature: diagnosing issues, implementing fixes or extensions, and leaving the feature doc in better shape than you found it.

**Language rules:**
- Chat output — match the language of the request.
- Plan and doc files (`.claude/plans/`, `.claude/docs/`) — always in English.

---

## Modes

Determine mode from the start of `$ARGUMENTS`:

- **`bug:`** — diagnose and fix a defect in an existing feature
- **`extend:`** — add scoped functionality to an existing feature without a full new plan
- **`review:`** — read the current state of a feature and report its health (no changes)
- Anything else — ask which mode fits

---

## Entry Protocol (all modes)

Before doing anything else, run these steps in order:

1. **Identify the feature slug** from `$ARGUMENTS` (e.g. `node-autoconnect`, `kmz-kml-import`)
2. **Load context** — read in this order, stop when you have enough to proceed:
   - `.claude/docs/<feature-slug>.md` — living feature doc (primary source)
   - `.claude/plans/<feature-slug>.md` — active plan if still in `plans/` (feature in progress)
   - `.claude/archive/<feature-slug>.md` — archived plan if feature is Done
3. **Check debug history** — look for `.claude/debug/<feature-slug>.md`; if it exists, run `/debug-log show: <feature-slug>` and surface recurring patterns before proceeding (bug: mode only — skip in extend: and review:)
4. **Scan changed files** — run `git log --oneline -10 -- <relevant paths>` to understand recent activity
5. **Read the symptom or request** — restate it in one sentence to confirm understanding

If the feature doc does not exist, say so and offer to create it from current code before proceeding.

---

## BUG Mode

**Request**: $ARGUMENTS

### Step 1. Symptom → Hypothesis

State:
- **Symptom**: what the user observes (or what the crash/log says)
- **Expected**: what should happen instead
- **Hypothesis**: most likely root cause (one sentence), with reasoning
- **Confidence**: high / medium / low — if low, list what you need to check first

### Step 2. Minimal Reproduction Path

Identify the smallest code path that triggers the bug:
- Entry point (UI event, use case call, BLE callback, etc.)
- Which layer the fault likely lives in (domain / data / presentation)
- Specific files and line ranges to inspect

Read those files before proposing a fix. Do not guess.

> **Diagnostic logging**: when adding temporary log calls to trace a bug, use `logger.d("FeatureTag", "...")` — never `android.util.Log`. Filter output in Logcat with `tag:MT` (all app logs) or `tag:MT/<FeatureTag>` (e.g. `tag:MT/BLE`). Remove diagnostic calls after the bug is confirmed fixed.

### Step 3. Fix

- Implement the fix in the minimum number of files
- Do not refactor surrounding code unless the bug is caused by structural issues
- If the fix requires a structural change, flag it and ask the user before proceeding

### Step 4. Verification

State how to verify the fix:
- Manual test steps (exact flow on device)
- If a unit test covers the path — run it; if not — note it as a gap

### Step 5. Doc Update

Update `.claude/docs/<feature-slug>.md`:
- If the bug revealed a non-obvious constraint — add it to **Non-obvious decisions**
- If the fix introduces a known limitation — add it to **Known limitations**
- If the fix changes key classes — update **Key classes**

Keep the doc short: only add what a reader cannot derive from the code.

---

## EXTEND Mode

**Request**: $ARGUMENTS

### Step 1. Scope Check

- One sentence: what does "done" look like?
- Is this a small addition (< 1 day) or does it warrant a full `/planner feature:` run?
  - If large → recommend `/planner feature:` and stop
- Which layers change? (domain / data / presentation)
- Does it need new UI? → call `/ui-designer` before coding
- Does it need new icons? → call `/icon-designer` before coding

### Step 2. Change Plan

Flat ordered list of steps. Each step:
- Names the file and what changes
- Flags skill calls if needed (`/architect`, `/ui-designer`, etc.)
- Is independently executable

### Step 3. Implementation

- Implement per the change plan
- Run `/simplify` on changed files when done

### Step 4. Doc Update

Update `.claude/docs/<feature-slug>.md` to reflect the extension:
- **What it does** — if user-facing behaviour changed
- **Key classes** — if new classes were added
- **Non-obvious decisions** — if the extension introduced a non-obvious choice
- **Known limitations** — if scope was explicitly cut

---

## REVIEW Mode

**Request**: $ARGUMENTS

Read the feature state and produce a health report:

```
Feature: <name>
Doc: exists | missing | stale
Plan: active | archived | missing
Last commit: <hash> <message> <date>

Health:
- [ ] Feature doc matches current code
- [ ] No open TODOs or FIXMEs in feature files
- [ ] Known limitations are documented
- [ ] Key classes list is accurate

Issues found:
- <list or "none">

Recommendation:
<one sentence: ship as-is | needs minor doc update | needs bug: or extend: run>
```

Do not make any changes in review mode — only report.

---

## Exit Protocol (bug: and extend: modes)

After the fix or extension is complete:

1. **Run `git status`** — list changed files
2. **Draft commit message** — Russian, imperative mood, `type(scope): description` format
3. **Present to user**: staged files + commit message
4. **Wait for explicit confirmation** before committing
5. **After commit**: confirm clean `git status`
6. **Offer debug log save** — if this was a non-trivial debug session (more than one hypothesis tried, or a subtle root cause found): suggest `"/debug-log save: <slug>"` to capture the history; skip if the fix was trivial
7. **Plan archiving** — if a plan file exists at `.claude/plans/<feature-slug>.md` and the feature is now fully done: move it to `.claude/archive/<feature-slug>.md` and delete the original; update the plan status table in **CLAUDE.md** to `Done`

**Rule**: never commit without explicit user confirmation.
**Rule**: commit messages in Russian, no `Co-Authored-By` line.
**Rule**: plan archiving is not optional — if the plan exists and the feature is done, always execute step 6.

---

## Principles

- **Read before fixing.** Never propose a change to a file you haven't read in this session.
- **Minimum blast radius.** Fix the defect, not the neighbourhood.
- **Doc is the deliverable.** A fix without a doc update is half-done if the fix revealed something non-obvious.
- **Escalate to planner when scope grows.** If a bug turns out to require architectural changes, stop and call `/planner task:` or `/planner feature:` instead of expanding in place.
- **No phantom confidence.** If the hypothesis is low-confidence, say so and read more code before acting.

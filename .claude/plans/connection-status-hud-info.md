# Plan: Connection Status Info in HUD (main button row)

**Date**: 2026-04-16
**Status**: Approved

## Summary

Display a dynamic connection status label in the `info` slot of HUD row 0 (right column, the "radio" button).
The label reflects the current BLE connection phase: searching (red), pairing (yellow), paired (green, auto-hides after 2 s).
No new use cases or repository interfaces are needed — `ObserveConnectionStatusUseCase` already delivers all required states.

## Scope

**In scope:**
- Add `shortName: String` to `MeshConnectionStatus.Connected` (domain model)
- Update `ConnectionStateMapper` to map `ourNode.user.short_name`
- Add `showConnectionLabel: Boolean` to `MainUiState`
- Manage 2-second auto-hide timer in `MainViewModel` via a coroutine Job
- Populate the info slot for HUD right-column row 0 based on connection state

**Out of scope:**
- Changes to any other HUD rows
- New use cases or repository changes
- Design system updates (colors used are raw `Color.Red/Yellow/Green` — semantic tokens deferred per CLAUDE.md)

## Architecture Notes

All changes are confined to two layers:

**Domain** — minor model extension only:
- `MeshConnectionStatus.Connected`: add `shortName: String` field

**Data** — mapper update only:
- `ConnectionStateMapper`: add `shortName = ourNode?.user?.short_name.orEmpty()`

**Presentation** — state + ViewModel:
- `MainUiState`: add `showConnectionLabel: Boolean = false`
- `MainViewModel`:
  - `private var connectedLabelJob: Job? = null` — holds the 2-second hide timer
  - In the `observeConnectionStatus` collector: on `Connected` → set `showConnectionLabel = true`, cancel previous job, launch new job with `delay(2000)` → `showConnectionLabel = false`; on any other state → cancel job + `showConnectionLabel = false`
  - In `buildRightColumn()` row 0 `info`: derive `HudInfoSlot` from `connectionStatus + showConnectionLabel`

### Info slot mapping

| connectionStatus              | showConnectionLabel | HudInfoSlot content                        | color        |
|-------------------------------|---------------------|--------------------------------------------|--------------|
| `Scanning`                    | —                   | `"Поиск..."`                               | `Color.Red`  |
| `Connecting(deviceName)`      | —                   | `"Сопряжение с $deviceName"`               | `Color.Yellow` |
| `Connected(shortName, …)`     | `true`              | `"Сопряжено с $shortName"`                 | `Color.Green` |
| `Connected(…)`                | `false`             | `null` (hidden)                            | —            |
| `Disconnected`, `Error`, `DeviceSleep` | —          | `null` (hidden)                            | —            |

## Phase Plan

### Phase 1 — Implementation (direct coding)

**Goal**: working feature across domain → data → presentation

**Tasks (in order):**

1. **Domain** — `app/src/main/java/ru/tcynik/meshtactics/domain/mesh/model/MeshConnectionStatus.kt`
   - Add `shortName: String` to `Connected` data class

2. **Data** — `app/src/main/java/ru/tcynik/meshtactics/data/mesh/mapper/ConnectionStateMapper.kt`
   - Add `shortName = ourNode?.user?.short_name.orEmpty()` to `Connected` mapping

3. **Presentation / State** — `app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/MainUiState.kt`
   - Add `val showConnectionLabel: Boolean = false`

4. **Presentation / ViewModel** — `app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/MainViewModel.kt`
   - Add `private var connectedLabelJob: Job? = null` field
   - Replace the `observeConnectionStatus` collector in `init {}` with one that:
     - On `Connected`: sets `showConnectionLabel = true`, cancels previous job, launches delay-and-hide job
     - On any other state: cancels job, sets `showConnectionLabel = false`
   - In `buildRightColumn()` row 0 `info`: replace `emptyInfoSlot()` with `buildConnectionInfoSlot(state)`
   - Add private fun `buildConnectionInfoSlot(state: MainUiState): HudInfoSlot`

**Skill / Agent**: direct coding
**Output artifact**: buildable code

### Phase 2 — Simplify

**Goal**: confirm no redundant code introduced
**Tasks**: run `/simplify` on `MainViewModel.kt`, `MainUiState.kt`, `ConnectionStateMapper.kt`, `MeshConnectionStatus.kt`
**Skill**: `/simplify`

### Phase 3 — Integration Review

**Goal**: confirm no Clean Architecture violations
**Tasks**: verify that the timer Job in ViewModel does not leak; verify no domain types leak into data layer
**Skill**: direct review (no `/architect` call needed for this scope)

### Phase 4 — Skill Update Review

**Goal**: keep project skills in sync
- `/architect` — no new canonical patterns (timer in ViewModel is standard)
- `/ui-designer` — no new design tokens (color usage is provisional, deferred per CLAUDE.md)
- `/planner` — no methodology gaps found
- `/tester` — no new test patterns

All skills: **no changes needed**.

### Phase 5 — Project Docs & Memory Update

**Goal**: CLAUDE.md and memory reflect completed work
**Tasks**:
- Update CLAUDE.md feature table: "Статус соединения в HUD" → Done (once implemented)
- Set this plan status to `Done`
- Append token cost to Change Log

### Phase 6 — Commit Preparation

**Goal**: staged commit, waiting for user approval
**Tasks**:
1. `git status` — enumerate changed files
2. Stage by name (never `git add -A`)
3. Draft commit message in Russian, imperative mood
4. Present staged files + message to user → wait for confirmation → `git commit`

## Coordination Map

```
Phase 1: [direct coding] — domain → data → presentation
Phase 2: /simplify on changed files
Phase 3: [direct review — no /architect needed]
Phase 4: [skill update review — no changes needed]
Phase 5: [docs & memory — CLAUDE.md, plan file, memory/]
Phase 6: [stage by name] → [propose commit] → [wait confirmation] → git commit
```

## Open Questions

- None remaining. `deviceName` (BLE device name, e.g. `Meshtastic_ab12`) confirmed acceptable for "Сопряжение с…" label.

## Change Log

- 2026-04-16: created

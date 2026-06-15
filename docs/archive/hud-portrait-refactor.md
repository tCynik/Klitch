# Plan: HUD Portrait Layer Refactor

**Date**: 2026-05-16
**Status**: Done

## Summary

The current HUD is built on a generic slot system (`HudConfig` → `HudColumnConfig` → `HudRowConfig`) where the ViewModel dynamically assembles button configs into numbered slots. This proved overly complex. The new approach: a fixed-structure `HudPortraitControlsLayer` with named, hardcoded button positions, driven by a flat `HudUiState`. The landscape layer (`HudControlsLayer`) is out of scope — the old `HudConfig` system stays for it.

## Scope

**In scope:**
- New `HudUiState` data class with named button + info slot properties
- ViewModel: new `buildHudUiState()` builder, new `hudUiState: StateFlow<HudUiState>` output
- Rewrite `HudPortraitControlsLayer` with fixed layout (left all-bottom; right: radio top, rest bottom)
- Update call site to collect and pass `hudUiState`

**Out of scope:**
- `HudControlsLayer` (landscape) — unchanged
- `HudConfig` / `HudColumnConfig` / `HudRowConfig` / `HudConfigFactory` — not deleted, still used by landscape
- Info slot changes — structure preserved as-is
- Button composition per column — same buttons, same left/right assignment

## Layout Spec

```
Row(SpaceBetween, fillMaxSize) {

    // LEFT — all buttons at bottom
    Column(Arrangement.Bottom) {
        <left button 1>
        <left button 2>
        ...
    }

    // RIGHT — radio pinned top, rest at bottom
    Column(SpaceBetween) {
        <radio button>              // top
        Column(Arrangement.Bottom) {
            <right button 2>
            ...                    // bottom group
        }
    }
}
```

## Architecture Notes

- `HudUiState` contains lambdas (`onClick` per button) → cannot use `copy()` in `MainUiState` → stays a **separate `StateFlow`** in the ViewModel, same reasoning as current `hudConfig`
- Per-button type: reuse `HudButtonSlot` (has all needed fields: `iconRes`, `label`, `onClick`, `enabled`, `selected`, `tintOverride`, `infoBadge`)
- Per-button info: reuse `HudInfoSlot` (nullable `content`)
- `HudUiState` is a flat data class with **named** button properties (e.g. `gpsButton`, `sosButton`, `radioButton`) — no list indexing
- ViewModel: `buildHudUiState()` replaces `buildHudConfig()` for portrait; `buildHudConfig()` stays for landscape

## Task Breakdown

### Step 1 — Define `HudUiState`
- File: `presentation/feature/main/osd/models/HudUiState.kt` (new file)
- Flat `data class HudUiState` with named `HudButtonSlot` + `HudInfoSlot` pairs for every button
- Button names derived from actual ViewModel `buildHudConfig()` — check ViewModel before writing
- No `require()` guards needed (named fields, not a list)

### Step 2 — ViewModel: add `hudUiState` StateFlow
- File: `MainViewModel.kt`
- Add `private fun buildHudUiState(): HudUiState` — mirrors current `buildHudConfig()` logic but outputs named fields
- Add `val hudUiState: StateFlow<HudUiState>` using the same combine/map pattern as `hudConfig`
- Keep `hudConfig` and `buildHudConfig()` untouched

### Step 3 — Rewrite `HudPortraitControlsLayer`
- File: `HudPortraitControlsLayer.kt`
- Signature change: `config: HudConfig` → `state: HudUiState`
- Fixed layout per Layout Spec above
- Left column: named left buttons, `Arrangement.Bottom`
- Right column: `radioButton` anchored top; remaining right buttons in inner `Column(Arrangement.Bottom)`
- Each button rendered via `HudButtonSlotItem` (reused as-is); info via `HudInfoSlotItem` (reused)

### Step 4 — Update call site
- File: main screen composable (wherever `HudPortraitControlsLayer` is called)
- Collect `hudUiState` from ViewModel, pass to `HudPortraitControlsLayer`

## Coordination Map

```
Step 1: [direct coding] — new HudUiState.kt
Step 2: [direct coding] — MainViewModel.kt
Step 3: [direct coding] — HudPortraitControlsLayer.kt
Step 4: [direct coding] — call site
→ /simplify on changed files
→ Phase 6: skill update review
→ Phase 6b: update hud-structure.md, CLAUDE.md, archive this plan
→ Phase 7: commit
```

## Open Questions

- **Button names**: need to verify exact button list per column by reading `buildHudConfig()` in the ViewModel before Step 1
- **`HudButtonSlot` reuse**: if landscape refactor happens later, `HudButtonSlot` becomes part of both old and new models — that's fine, it's a stable leaf type

## Resolved

- **Info slots in portrait**: yes — each button is paired with its info slot, rendered adjacent (same as current `HudRow` behaviour). Left side: button left, info right. Right side: info left, button right.

## Change Log

- 2026-05-16: created
- 2026-05-16: done | tokens: not recorded

# HUD Structure

## What it does
Data-driven HUD layout: two vertical columns (left = map tools, right = menu), each with 5 fixed button slots and 5 info slots. Content is driven by a config object built in the ViewModel.

## Key classes
- `HudConfig` / `HudColumnConfig` / `HudButtonSlot` / `HudInfoSlot` — data model; `presentation/feature/main/osd/`
- `HudControlsLayer` — root composable; accepts `HudConfig`; `presentation/feature/main/osd/`
- `HudBlock` — renders one column (button col + info col) in the correct Left/Right order
- `HudButtonColumn` / `HudInfoColumn` — 5-slot column composables
- `HudPortraitControlsLayer` — portrait variant (buttons only, no info cols); temporary

## Non-obvious decisions
- `HudConfig` is a **separate `StateFlow`** from `MainUiState` — it contains lambdas (`onClick`) which are not valid in a plain data class used with `copy()`; `buildHudConfig()` rebuilds the entire config when any relevant state changes
- Both lists in `HudColumnConfig` must have **exactly 5 elements** — enforced via `require()` in `init`
- Empty slots: `icon = null` → invisible `Box` that **reserves layout space** (buttons stay aligned)
- Portrait HUD shares the same `HudConfig` — same buttons and actions, info columns just aren't rendered

## Known limitations / planned extensions
- Button content (Phase 4 in original plan) is partially filled — remaining slots TBD
- Portrait HUD design is temporary; full portrait layout is a deferred feature

## Source
Plan: `.claude/archive/hud-structure.md`

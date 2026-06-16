# HUD Structure

## Current approach — Fixed Composable (portrait)

Portrait HUD uses a fixed-structure Composable driven by a flat `HudUiState`. Button positions are hardcoded in the layout; state drives per-button properties (enabled, selected, tint, badge, onClick).

### Layout

```
Row(SpaceBetween) {
    Column(Arrangement.Bottom)          // Left: all buttons at bottom
    Column(Arrangement.SpaceBetween) {  // Right: radio top, rest at bottom
        <radio>
        Column { <mesh> <marks> <chat> }
    }
}
```

### Key classes

- `HudUiState` — flat data class with named `HudRowConfig` properties per button; `presentation/feature/main/osd/models/`
- `HudPortraitControlsLayer` — fixed-layout portrait Composable; accepts `HudUiState`
- `HudRowConfig` / `HudButtonSlot` / `HudInfoSlot` — reused leaf types (shared with landscape)
- `HudRow` / `HudButtonSlotItem` / `HudInfoSlotItem` — reused rendering composables

### Button map

| Property | Icon | Label | Column | Position |
|---|---|---|---|---|
| `compass` | ic_compass | направление | Left | bottom group |
| `target` | ic_target | привязка | Left | bottom group |
| `markTool` | ic_marks_tool | метки | Left | bottom group |
| `mapTools` | ic_map_tools | инструменты | Left | bottom group |
| `gps` | ic_satellite | спутники | Left | bottom group |
| `radio` | ic_radio | радио | Right | **top** |
| `mesh` | ic_mesh | сетка | Right | bottom group |
| `marks` | ic_marks | метки | Right | bottom group |
| `chat` | ic_chat | чаты | Right | bottom group |

### Non-obvious decisions

- `HudUiState` contains lambdas (`onClick`) → cannot use `copy()` in `MainUiState` → separate `StateFlow<HudUiState>` in ViewModel, same pattern as the old `hudConfig`
- `HudRowConfig` reused as the per-button pair type — no new wrapper introduced
- Only `radio` has a non-empty `HudInfoSlot` (connection status text); all others use `emptyInfoSlot()`

---

## Legacy approach — Dynamic slot system (landscape only)

Still active for the landscape HUD (`HudControlsLayer`). Will be replaced when landscape refactor happens.

### Key classes

- `HudConfig` / `HudColumnConfig` / `HudRowConfig` — generic slot hierarchy; left and right columns each have exactly 5 rows enforced via `require()` in `init`
- `HudControlsLayer` — landscape Composable; accepts `HudConfig`
- `HudBlock` — renders one column (button col + info col) in the correct Left/Right order
- `HudConfigFactory` — `emptyButtonSlot()` / `emptyInfoSlot()` / `emptyHudConfig()` helpers
- `HudPortraitControlsLayer` — **replaced** by the fixed-layout version above

### Non-obvious decisions (legacy)

- `HudConfig` is a **separate `StateFlow`** from `MainUiState` — contains lambdas; `buildHudConfig()` rebuilds the entire config on any relevant state change
- Both lists in `HudColumnConfig` must have **exactly 5 elements** — enforced via `require()` in `init`
- Empty slots: `iconRes = null` → invisible `Box` that **reserves layout space** (buttons stay aligned)

## Source

Plan: `docs/archive/hud-structure.md`  
Refactor plan: `docs/plans/hud-portrait-refactor.md`

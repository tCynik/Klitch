# Plan: HUD Structure

**Date**: 2026-04-08
**Branch**: `hud_structure`
**Status**: Done

## Summary

Implement the structural HUD layout for the main screen: two vertical blocks (left and right),
each consisting of a button column and an info column. The layout is fixed; the content is
dynamic — buttons, labels, actions, and info items are driven by a config object built in the
ViewModel.

---

## Visual Layout

```
Landscape screen (full width):

┌──────────────────────────────────────────────────────────────────┐
│ [btn col] [info col 100dp]  ··· map ···  [info col 100dp] [btn col] │
│                                                                  │
│  □  label   ← slot 0 →                     ← slot 0 →  label  □ │
│  □  label   ← slot 1 →                     ← slot 1 →  label  □ │
│             ← slot 2 →  (centered group)   ← slot 2 →           │
│  □  label   ← slot 3 →                     ← slot 3 →  label  □ │
│  □  label   ← slot 4 →                     ← slot 4 →  label  □ │
└──────────────────────────────────────────────────────────────────┘
```

**Button column rules:**
- 5 slots, fixed positions, filled top-to-bottom
- Empty slots: invisible (take up layout space, no visual output)
- Vertically centered as a group within the column
- Each slot: `MeshIconButton` (80×80dp) + short label below it

**Info column rules:**
- Fixed width: `100.dp` (provisional, to be adjusted)
- 5 slots, aligned to button rows
- Display-only (no interaction for now)
- A slot is hidden when its content is `null`

---

## Data Model

### `HudButtonSlot`

One slot in the button column. `null` icon = empty slot (space reserved, nothing rendered).

```kotlin
data class HudButtonSlot(
    val icon: ImageVector?,        // null → empty slot
    val label: String,             // short caption rendered below the button
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val selected: Boolean? = null, // null = regular button; true/false = toggle
)
```

### `HudInfoSlot`

One slot in the info column. `null` content = slot is hidden.

```kotlin
data class HudInfoSlot(
    val content: String?,          // null → slot hidden (space reserved)
)
```

### `HudColumnConfig`

```kotlin
data class HudColumnConfig(
    val buttons: List<HudButtonSlot>,  // exactly 5 elements
    val infoItems: List<HudInfoSlot>,  // exactly 5 elements
)
```

### `HudConfig`

```kotlin
data class HudConfig(
    val left: HudColumnConfig,
    val right: HudColumnConfig,
)
```

**File location**: `presentation/feature/main/osd/HudConfig.kt`

---

## Architecture

### Config ownership

`HudConfig` lives outside `MainUiState` because it contains lambdas. The ViewModel exposes it
as a separate `StateFlow<HudConfig>`.

```kotlin
// In MainViewModel:
private val _hudConfig = MutableStateFlow(buildHudConfig())
val hudConfig: StateFlow<HudConfig> = _hudConfig.asStateFlow()

private fun buildHudConfig(): HudConfig { ... }
```

When state changes (e.g. connection status → button enabled/disabled), the ViewModel calls
`_hudConfig.value = buildHudConfig()`.

### `HudControlsLayer` signature

Replaces all individual callbacks with a single `HudConfig`:

```kotlin
@Composable
fun HudControlsLayer(
    config: HudConfig,
    modifier: Modifier = Modifier,
)
```

### Composable tree

```
HudControlsLayer(config)
  └─ Row (SpaceBetween, full width)
       ├─ HudBlock(config.left,  side = Left)
       │    ├─ HudButtonColumn(config.left.buttons)
       │    │    └─ HudButtonSlotItem × 5
       │    │         ├─ MeshIconButton (or empty Box)
       │    │         └─ Text label (or empty Box)
       │    └─ HudInfoColumn(config.left.infoItems, width = 100.dp)
       │         └─ HudInfoSlotItem × 5
       └─ HudBlock(config.right, side = Right)
            ├─ HudInfoColumn(...)
            └─ HudButtonColumn(...)
```

---

## Slot Invariant

Both `buttons` and `infoItems` lists in `HudColumnConfig` must always contain **exactly 5 elements**.
The ViewModel must enforce this. A `require()` assertion will be added in `HudColumnConfig` init.

```kotlin
init {
    require(buttons.size == 5) { "HudColumnConfig.buttons must have exactly 5 slots" }
    require(infoItems.size == 5) { "HudColumnConfig.infoItems must have exactly 5 slots" }
}
```

---

## Phase Plan

### Phase 0 — Data Model

**Goal**: Define `HudConfig` and related types.

**Tasks:**
1. Create `HudConfig.kt` in `presentation/feature/main/osd/`:
   - `HudButtonSlot` data class
   - `HudInfoSlot` data class
   - `HudColumnConfig` data class (with `require` in init)
   - `HudConfig` data class

**Output**: Compiles cleanly, no usages yet.

---

### Phase 1 — Composable Layout

**Goal**: Implement `HudButtonSlotItem`, `HudButtonColumn`, `HudInfoColumn`, `HudBlock`.
Refactor `HudControlsLayer` to accept `HudConfig`.

**Tasks:**
1. `HudButtonSlotItem` — renders a single button slot:
   - If `slot.icon == null`: render an invisible `Box` with `size(80.dp)` + empty label area
   - If `slot.icon != null`: render `MeshIconButton` + `Text(slot.label)` below
2. `HudButtonColumn` — `Column` (vertically centered, spacedBy):
   - Renders 5 × `HudButtonSlotItem`
3. `HudInfoSlotItem` — renders one info slot (Text or empty Box)
4. `HudInfoColumn` — `Column` (width = `100.dp`, vertically centered, spacedBy):
   - Renders 5 × `HudInfoSlotItem`
5. `HudBlock(config, side)` — `Row` containing button + info columns in correct order:
   - `Left`: `[HudButtonColumn | HudInfoColumn]`
   - `Right`: `[HudInfoColumn | HudButtonColumn]`
6. `HudControlsLayer` — replace individual callbacks with `config: HudConfig`:
   - Root: `Row(SpaceBetween)` → `HudBlock(left)` + `HudBlock(right)`
   - Remove `NodeStatusIndicator` private composable (it will become a slot in the config)

**All new composables in**: `HudControlsLayer.kt` (or extracted to separate files if the file gets long — use judgment at implementation time)

---

### Phase 2 — ViewModel Wiring

**Goal**: ViewModel builds and exposes `HudConfig`; `MainScreen` passes it to `HudControlsLayer`.

**Tasks:**
1. **`MainViewModel.kt`**:
   - Add `private val _hudConfig = MutableStateFlow(buildHudConfig())`
   - Add `val hudConfig: StateFlow<HudConfig> = _hudConfig.asStateFlow()`
   - Implement `buildHudConfig()` — for now: 5 empty left slots, 5 empty right slots
     (except slot 0 right = NodeStatusIndicator placeholder, to be filled in Phase 3)
   - Add `private fun rebuildHud()` called whenever state changes that affects HUD
2. **`MainScreen.kt`**:
   - Collect `hudConfig` from ViewModel
   - Pass to `HudControlsLayer(config = hudConfig)`
   - Remove individual `onChatClick`, `onSettingsClick`, `onNodeStatusClick`,
     `onMarkerManagementClick` parameters from `HudControlsLayer` call

---

### Phase 3 — Migrate NodeStatusIndicator

**Goal**: Move the existing `NodeStatusIndicator` content into the config system.

**Tasks:**
1. In `buildHudConfig()`, populate right column slot 0 with a `HudInfoSlot` containing the
   node status text (derived from `connectionStatus` + `nodeMarkers.size`)
2. Remove the private `NodeStatusIndicator` composable from `HudControlsLayer.kt`
3. The color coding (green/red/gray) needs a decision: `HudInfoSlot` may need an optional
   `color: Color?` field — resolve during implementation

**Note**: This is a content decision, not a structural one. If color token for signal quality is
not yet defined in the design system, document the TBD in `/ui-designer`.

---

### Phase 4 — Button Content (TBD)

**Goal**: Fill button slots with actual icons and actions.

**Status**: Blocked on content definitions. User will provide icon descriptions and actions
for each slot in subsequent prompts.

**Left column slots** (map tools):
- Slot 0: TBD
- Slot 1: TBD
- Slot 2: TBD
- Slot 3: TBD
- Slot 4: TBD

**Right column slots** (main menu):
- Slot 0: Node status info (→ Phase 3)
- Slot 1: TBD
- Slot 2: TBD
- Slot 3: TBD
- Slot 4: TBD

---

### Phase 5 — Simplify & Review

1. Run `/simplify` on all changed files
2. Run `/architect review: presentation/feature/main/` — confirm no domain types leak
   into HUD config (icons, labels, actions are all presentation-layer)

---

### Phase 6 — Skill & Docs Update

- **`/ui-designer`**: Register `HudButtonSlotItem`, `HudButtonColumn`, `HudInfoColumn`,
  `HudBlock`, `HudControlsLayer` in component library. Define spacing tokens used.
- **`CLAUDE.md`**: Add `HUD Structure` to feature table → `Done`
- **`product-brief.md`**: Update `Main Screen OSD Layout` section with final component tree

---

### Phase 7 — Commit

```
feat(hud): структура HUD — динамически-конфигурируемые колонки кнопок и информации
```

---

## Open Questions

1. **`HudInfoSlot` color**: `NodeStatusIndicator` needs red/green/gray tint on text.
   Options: add `color: Color?` field to `HudInfoSlot`, or define a semantic `SignalLevel` enum
   with a color token mapping. Resolve in Phase 3.

2. **Spacing between button and label**: How much vertical gap between `MeshIconButton` and
   the label text below it? Proposed: `4.dp`. Confirm during implementation.

3. **Label typography**: Which `MaterialTheme.typography` token for button labels?
   Proposed: `labelSmall`. Confirm with `/ui-designer`.

4. **Edge padding**: Current `HudControlsLayer` uses `padding(8.dp)`. Does this change with
   the new structure? Proposed: keep for now, revisit in Phase 5.

---

## Coordination Map

```
Phase 0: [Data model]
Phase 1: [Layout composables]  — needs Phase 0
Phase 2: [ViewModel wiring]    — needs Phase 1
Phase 3: [NodeStatusIndicator migration] — needs Phase 2
Phase 4: [Button content]      — needs user input, blocked
Phase 5: /simplify + /architect review — needs Phases 0–3
Phase 6: [Docs update]
Phase 7: /commit
```

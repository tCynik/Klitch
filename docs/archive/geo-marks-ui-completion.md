# Plan: Geo Marks UI Completion

**Date**: 2026-04-18
**Status**: Done
**Actualized**: 2026-05-25

## Summary

The geo-marks domain, data, and core presentation layers are fully implemented. The mark tool toggle, gesture handling, draft rendering, and received-marks rendering all work. What is missing is the last mile of UI wiring: the right HUD column has a "метки" button that navigates to a placeholder screen instead of activating the tool; the context menu event from long-tap is never consumed in the screen; and there is no UI surface to trigger `sendPendingMark()` or to show the user how many draft points they have.

## Scope

**In scope:**
- Wire the right-column "метки" HUD button to `toggleMarkTool()` with `selected = state.markToolActive` (replacing `nav.onMarkersClick`); remove `onMarkersClick` from `HudNavCallbacks`
- Add pending-point count badge on the right-column "метки" button
- `DropdownMenu` anchored at long-tap screen coordinates — "Удалить точку" action
- "Отправить" panel visible when `markToolActive && pendingMarkPoints.isNotEmpty()`
- Left-column "метки" button (`ic_marks_tool`, row 3) is a different future feature — do not change it

**Out of scope:**
- Editing/renaming saved marks
- Any new screens or navigation destinations for marks
- Visual design polish (colours, typography)
- Track preview line styling changes
- `Route.MarkerManagement` destination — keep in NavGraph but leave unreachable for now

## Architecture Notes

All ViewModel methods (`sendPendingMark`, `deletePendingPoint`, `toggleMarkTool`) and
`_contextMenuEvent: SharedFlow<GeoMarkContextMenuEvent>` already exist. No new domain or data changes needed.

`HudButtonSlot.infoBadge: String?` already exists — used for GPS accuracy and unread-chat badge.

`dpOffset` in `MaplibreMap.onMapLongClick` is `DpOffset` from MapLibre Compose — `.x.value` is already a `Float` in dp units. No density conversion needed in `MainScreen`.

`Route.MarkerManagement` is currently wired in NavGraph via `onMarkersClick`. Removing that callback from `HudNavCallbacks` will leave `Route.MarkerManagement` with no entry point for now — that is acceptable; it remains in the nav graph as a stub for a future feature.

## Phase Plan

### Phase 1 — Fix right-column "метки" button + badge ✅

**Goal**: Right-column button activates the mark tool and shows pending point count.

**File**: `MainViewModel.kt` — `buildRightColumn()`

**Changes**:
- Replace `HudButtonSlot(iconRes = R.drawable.ic_marks, label = "метки", onClick = nav.onMarkersClick)` with:
```kotlin
HudButtonSlot(
    iconRes  = R.drawable.ic_marks,
    label    = "метки",
    selected = state.markToolActive,
    onClick  = { toggleMarkTool() },
    infoBadge = state.pendingMarkPoints.size.takeIf { it > 0 }?.toString(),
)
```
- Remove the `TODO` comment above it.

**File**: `HudNavCallbacks.kt`
- Remove `val onMarkersClick: () -> Unit = {}`.

**File**: `NavGraph.kt`
- Remove `onMarkersClick = { navController.navigate(Route.MarkerManagement) }` from the `provideNavCallbacks` call.

---

### Phase 2 — Send panel ✅

**Goal**: When `markToolActive && pendingMarkPoints.isNotEmpty()`, a "Отправить (N)" button appears above the HUD.

**Position**: bottom-centre, above HUD — `Alignment.BottomCenter` with `navigationBarsPadding()` + `padding(bottom = 420.dp)` (clears the HUD block height of ~400dp + 8dp padding). This keeps the button thumb-accessible and does not cover the draft marks being placed.

**File**: `MainScreen.kt`

**Changes**:
1. Add parameter `onSendPendingMark: () -> Unit = {}`.
2. Inside the `Box`, after `HudControlsLayer`/`HudPortraitControlsLayer`:
```kotlin
AnimatedVisibility(
    visible = uiState.markToolActive && uiState.pendingMarkPoints.isNotEmpty(),
    modifier = Modifier.align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 420.dp),
    enter = fadeIn() + slideInVertically { it },
    exit  = fadeOut() + slideOutVertically { it },
) {
    Button(onClick = onSendPendingMark) {
        Text("Отправить (${uiState.pendingMarkPoints.size})")
    }
}
```

**File**: `NavGraph.kt`
- Pass `onSendPendingMark = viewModel::sendPendingMark` into `MainScreen(...)`.

---

### Phase 3 — Context menu (DropdownMenu) ✅

**Goal**: Long-tapping a draft point shows a `DropdownMenu` with "Удалить точку".

**Coordinate units**: `screenX`/`screenY` in `GeoMarkContextMenuEvent` are already in dp (`DpOffset.x.value`). Use `Modifier.offset(event.screenX.dp, event.screenY.dp)` directly.

**File**: `MainScreen.kt`

**Changes**:
1. Add parameter `contextMenuEvents: Flow<GeoMarkContextMenuEvent> = emptyFlow()`.
2. Add parameter `onDeletePendingPoint: (Int) -> Unit = {}`.
3. Inside the composable, before the `Box`:
```kotlin
var contextMenu by remember { mutableStateOf<GeoMarkContextMenuEvent?>(null) }
LaunchedEffect(contextMenuEvents) {
    contextMenuEvents.collect { event -> contextMenu = event }
}
```
4. Inside the `Box`, after the send panel `AnimatedVisibility`:
```kotlin
contextMenu?.let { event ->
    Box(Modifier.offset(event.screenX.dp, event.screenY.dp).size(0.dp)) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = { contextMenu = null },
        ) {
            DropdownMenuItem(
                text = { Text("Удалить точку") },
                onClick = {
                    onDeletePendingPoint(event.pointIndex)
                    contextMenu = null
                },
            )
        }
    }
}
```

**File**: `NavGraph.kt`
- Pass:
  - `contextMenuEvents = viewModel.contextMenuEvents` (expose `_contextMenuEvent` as public `val contextMenuEvents`)
  - `onDeletePendingPoint = viewModel::deletePendingPoint`

**File**: `MainViewModel.kt`
- Expose `_contextMenuEvent` publicly if not already:
```kotlin
val contextMenuEvents: SharedFlow<GeoMarkContextMenuEvent> = _contextMenuEvent.asSharedFlow()
```

---

### Phase 4 — Integration Review ✅

Review `MainScreen.kt` parameter count. If it exceeds ~10 params, extract a `MainScreenCallbacks` data class. Check there are no layer violations (all new calls stay in presentation).

---

### Phase 5 — Skill Update Review ✅

- `/architect` — no new patterns.
- `/ui-designer` — no new design tokens.
- `/icon-designer` — no new icons.
- `/planner` — no methodology changes.

Explicit outcome: **no skill files need updating**.

---

### Phase 6 — Docs & Memory Update ✅

- Update `.claude/docs/geo-marks.md`:
  - Presentation section: fix right-column wiring, add send panel and context menu descriptions
  - Resolved Decisions table: add "send panel position" and "context menu anchor units"
- Update `CLAUDE.md` Geo Marks status → `✅ Done` (after this task marks the feature shippable).
- Update `memory/project_state.md`.

---

### Phase 7 — Commit Preparation ✅

Files to stage (by name):
- `app/.../presentation/feature/main/MainViewModel.kt`
- `app/.../presentation/feature/main/MainScreen.kt`
- `app/.../presentation/feature/main/HudNavCallbacks.kt`
- `app/.../navigation/NavGraph.kt`

Proposed commit message:
```
завершить UI инструмента geo-marks

- Кнопка «метки» в правой колонке HUD активирует инструмент (toggle)
- Счётчик черновых точек в badge на кнопке
- Панель «Отправить (N)» появляется когда есть черновые точки
- Контекстное меню удаления черновой точки по долгому тапу
```

Wait for explicit user confirmation before running `git commit`.

## Coordination Map

```
Phase 1: direct coding — MainViewModel.kt, HudNavCallbacks.kt, NavGraph.kt
Phase 2: direct coding — MainScreen.kt, NavGraph.kt
Phase 3: direct coding — MainScreen.kt, MainViewModel.kt, NavGraph.kt
Phase 4: /architect review: presentation layer changes
Phase 5: skill update review (no changes — explicit)
Phase 6: update .claude/docs/geo-marks.md, CLAUDE.md, memory/project_state.md
Phase 7: stage files by name → propose commit message → wait for confirmation → git commit
```

## Open Questions

None — all resolved.

## Change Log

- 2026-04-18: created (revised after clarifications: mark tool belongs in right column, send panel bottom-centre, dp units confirmed)
- 2026-05-25: actualized — all phases done; design evolved: button calls `toggleGeoMarksSheet()` instead of `toggleMarkTool()`, send panel moved to `GeoMarksSheet` component instead of `MainScreen`. Context menu (`DropdownMenu`) implemented in `MainScreen`. `onMarkersClick` removed from `HudNavCallbacks`. `contextMenuEvents` wired via `NavGraph`. Feature committed on branch `marks_list`.

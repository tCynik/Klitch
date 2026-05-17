# Plan: Map Orientation Binding

**Date**: 2026-05-18
**Status**: Draft

## Summary

Wires the existing `compass` HUD button to two map orientation behaviours:
(1) **single tap** — resets camera bearing to north (0°) with animation; if heading-up mode is
active, also exits it.
(2) **long-tap** — toggles **heading-up mode**: camera bearing continuously follows the device
compass sensor (`bearing` from `DeviceOrientationProvider`). Mode auto-deactivates when the user
rotates the map via gesture.

Follow Me (position tracking) remains independent — both modes can be active simultaneously.
No domain or data layer changes needed — pure presentation layer.

## Scope

**In scope:**
- `MeshIconButton` — add `onLongClick` param, switch `Modifier.clickable` → `Modifier.combinedClickable`
- `HudButtonSlot` — add `onLongClick: (() -> Unit)? = null`
- `HudButtonSlotItem` — pass `slot.onLongClick` to `MeshIconButton`
- `MainUiState` — add `isHeadingUpActive: Boolean = false`
- `MainViewModel` — `onCompassTap()`, `onCompassLongPress()`, `onHeadingUpDeactivated()`; emit `resetBearingEvent`; wire compass button in `buildHudUiState` + `buildLeftColumn`
- `MainScreen` — `LaunchedEffect` for heading-up bearing updates; collect `resetBearingEvent`; gesture deactivation extended to heading-up; new params `onHeadingUpDeactivated` + `resetBearingEvents`
- `NavGraph.kt` — wire new `MainScreen` params

**Out of scope:**
- Compass rose / north-arrow UI element on the map
- Persistence of heading-up mode across sessions
- Course-up mode (bearing from GPS track, not compass)
- Landscape HUD compass button (portrait-only for now)

## Architecture Notes

### Layer topology
Pure presentation — same as Follow Me. No use cases, repositories, or domain models touched.

### One-shot "reset bearing" event
`onCompassTap()` must trigger a camera animation in `MainScreen`. The animation is a one-shot
imperative action, not persistent state. Pattern: `MutableSharedFlow<Unit>` in `MainViewModel`,
collected in `MainScreen` with `LaunchedEffect(resetBearingEvents)`. Same pattern as `contextMenuEvent`.

### Heading-up bearing update strategy
`bearing` from `DeviceOrientationProvider.bearing` (StateFlow<Float>) updates at sensor rate
(potentially 50+ Hz). Two options — decide in Phase 0 based on MapLibre API:

- **Option A — direct write** (preferred if available): `cameraState.position` is a `var` in
  MapLibre Compose. Writing it directly moves the camera without animation. Use
  `LaunchedEffect(bearing, uiState.isHeadingUpActive)` + `cameraState.position = current.copy(bearing = bearing.toDouble())`.
  Responsive, no overhead.
- **Option B — throttled animateTo**: collect `orientationProvider.bearing` in a coroutine loop
  inside `LaunchedEffect(uiState.isHeadingUpActive)` with throttle (~80–100 ms). Each `animateTo`
  takes 100–150 ms and the next fires after it completes, naturally throttling to ~7–10 fps.
  Smooth but adds animation latency.

### Long-press: combinedClickable
`MeshIconButton` currently uses `Modifier.clickable`. Switch to `Modifier.combinedClickable` from
`foundation`. `onLongClick` is nullable — existing callers pass `null` (no behaviour change).

### Compass button visual state
`selected = if (state.isHeadingUpActive) true else null`
— `true` → toggle-on glow (heading-up active)
— `null` → regular button (north-up, no mode)

### Gesture auto-deactivation
Extend the existing `LaunchedEffect(cameraState.moveReason)` in `MainScreen`:
```kotlin
if (cameraState.moveReason == CameraMoveReason.GESTURE) {
    if (uiState.isFollowMeActive) onFollowMeDeactivated()
    if (uiState.isHeadingUpActive) onHeadingUpDeactivated()
}
```

## Phase Plan

### Phase 0 — Research (quick, codebase-only)

**Goal**: confirm MapLibre Compose `CameraPosition` API before implementation.

**Tasks:**
1. Check `CameraPosition` constructor — does it accept `bearing: Double`? Grep the MapLibre
   compose dependency or existing research file `.claude/research/maplibre-compose-camera-animation.md`.
2. Check if `cameraState.position` is a writable `var` (direct setter available) or read-only.
3. Based on findings: confirm Option A or Option B from Architecture Notes above.

**Skill**: Explore / grep (no web search needed — confirmed via dependency source or existing research)
**Output**: one-message summary; update this plan's Architecture Notes with final decision.

> **Token checkpoint**: run `/compact` before Phase 1.

---

### Phase 1 — Implementation

**Goal**: buildable, working code across all affected files.

**Order** (domain → presentation, dependencies first):

#### 1. `MeshIconButton.kt`
- Add `onLongClick: (() -> Unit)? = null` parameter
- Replace `Modifier.clickable(enabled = enabled, onClick = onClick)` with:
  ```kotlin
  Modifier.combinedClickable(
      enabled = enabled,
      onClick = onClick,
      onLongClick = onLongClick,
  )
  ```
- Add import: `androidx.compose.foundation.ExperimentalFoundationApi` (if needed by version)
  and `androidx.compose.foundation.combinedClickable`

#### 2. `HudButtonSlot.kt`
- Add field: `val onLongClick: (() -> Unit)? = null`

#### 3. `HudButtonSlotItem.kt`
- Pass `slot.onLongClick` to `MeshIconButton(onLongClick = slot.onLongClick, ...)`

#### 4. `MainUiState.kt`
- Add: `val isHeadingUpActive: Boolean = false`

#### 5. `MainViewModel.kt`
Add `SharedFlow` for reset event:
```kotlin
private val _resetBearingEvent = MutableSharedFlow<Unit>()
val resetBearingEvent: SharedFlow<Unit> = _resetBearingEvent.asSharedFlow()
```
Add methods:
```kotlin
fun onCompassTap() {
    if (_uiState.value.isHeadingUpActive) {
        _uiState.update { it.copy(isHeadingUpActive = false) }
    }
    viewModelScope.launch { _resetBearingEvent.emit(Unit) }
}

fun onCompassLongPress() {
    _uiState.update { it.copy(isHeadingUpActive = !it.isHeadingUpActive) }
}

fun onHeadingUpDeactivated() {
    _uiState.update { it.copy(isHeadingUpActive = false) }
}
```
Wire compass in `buildHudUiState`:
```kotlin
compass = HudRowConfig(
    button = HudButtonSlot(
        iconRes = R.drawable.ic_compass,
        label = "направление",
        selected = if (state.isHeadingUpActive) true else null,
        onClick = { onCompassTap() },
        onLongClick = { onCompassLongPress() },
    ),
    info = emptyInfoSlot(),
),
```
Wire compass in `buildLeftColumn` (landscape HudConfig) — same logic.

#### 6. `MainScreen.kt`
Add parameters:
```kotlin
onHeadingUpDeactivated: () -> Unit = {},
resetBearingEvents: Flow<Unit> = emptyFlow(),
```
Add `LaunchedEffect` for reset-to-north:
```kotlin
LaunchedEffect(resetBearingEvents) {
    resetBearingEvents.collect {
        val pos = cameraState.position
        cameraState.animateTo(
            CameraPosition(target = pos.target, zoom = pos.zoom, bearing = 0.0),
            duration = 300.milliseconds,
        )
    }
}
```
Add `LaunchedEffect` for heading-up continuous update (implementation based on Phase 0 decision):
```kotlin
// Option A (direct write — preferred):
LaunchedEffect(bearing, uiState.isHeadingUpActive) {
    if (!uiState.isHeadingUpActive) return@LaunchedEffect
    val pos = cameraState.position
    cameraState.position = CameraPosition(target = pos.target, zoom = pos.zoom, bearing = bearing.toDouble())
}

// Option B (throttled animateTo — fallback):
LaunchedEffect(uiState.isHeadingUpActive) {
    if (!uiState.isHeadingUpActive) return@LaunchedEffect
    orientationProvider.bearing.collect { b ->
        val pos = cameraState.position
        cameraState.animateTo(
            CameraPosition(target = pos.target, zoom = pos.zoom, bearing = b.toDouble()),
            duration = 150.milliseconds,
        )
    }
}
```
Extend gesture deactivation `LaunchedEffect`:
```kotlin
LaunchedEffect(cameraState.moveReason) {
    if (cameraState.moveReason == CameraMoveReason.GESTURE) {
        if (uiState.isFollowMeActive) onFollowMeDeactivated()
        if (uiState.isHeadingUpActive) onHeadingUpDeactivated()
    }
}
```

#### 7. `NavGraph.kt`
Wire `onHeadingUpDeactivated = viewModel::onHeadingUpDeactivated` and
`resetBearingEvents = viewModel.resetBearingEvent` in the `MainScreen` call.

**Skill**: direct coding
**Output**: buildable code

---

### Phase 2 — Simplify

Run `/simplify` on all changed files.

---

### Phase 3 — Architectural Review (self-review, lightweight)

**Goal**: confirm no Clean Architecture violations.

**Checks:**
- `isHeadingUpActive` lives in `MainUiState` (presentation) ✓
- `resetBearingEvent` is a `SharedFlow` in ViewModel — same pattern as `contextMenuEvent` ✓
- No domain types exposed beyond ViewModel boundary ✓
- Sensor data (`bearing`) read in `MainScreen`, not in ViewModel — same as current pattern ✓

**Skill**: self-review (no `/architect` call needed — scope is presentation only)
**Output**: review sign-off in Change Log below

---

### Phase 4 — Skill Update Review

- **`/architect`**: `MeshIconButton` now supports `onLongClick`. If a "long-press" pattern becomes
  reusable, document it. The `SharedFlow` one-shot event pattern is already used (`contextMenuEvent`) — confirm it is documented in `/architect`; add if missing.
- **`/ui-designer`**: `selected = true` on compass — same active-toggle visual as Follow Me button
  (ic_target). No new design tokens. No changes needed.
- **`/icon-designer`**: no new icons. No changes needed.
- **`/planner`**: no methodology gap found.

---

### Phase 5 — Project Docs & Memory Update

- Create `.claude/docs/map-orientation.md` (feature doc)
- Update `CLAUDE.md` status table: Map Orientation Binding → `Done`
- Move this plan: `.claude/plans/map-orientation.md` → `.claude/archive/map-orientation.md`; verify with `ls .claude/plans/`
- Update `memory/project_state.md`
- Append token log to archived plan Change Log (ask user for `/cost` value)

---

### Phase 6 — Commit Preparation

Stage by name, propose message, wait for confirmation:
```
feat(map): привязка ориентации карты по компасу
```

## Coordination Map

```
Phase 0: [grep CameraPosition bearing + cameraState.position writeability] → update plan
         → [/compact]
Phase 1: [direct coding]
         MeshIconButton → HudButtonSlot → HudButtonSlotItem
         → MainUiState → MainViewModel → MainScreen → NavGraph
Phase 2: /simplify on changed files
Phase 3: [self-review — presentation layer only]
Phase 4: [skill update review — /architect SharedFlow pattern, others no change]
Phase 5: [docs + CLAUDE.md + archive + memory]
Phase 6: [stage by name] → [propose commit] → [wait for confirmation] → git commit
```

## Open Questions

1. **`CameraPosition.bearing` param** — Resolve in Phase 0. Expected: yes (MapCameraPosition
   already has `bearing: Double`), but confirm the MapLibre Compose `CameraPosition` constructor.
2. **Heading-up update strategy** — Option A vs B decided in Phase 0 based on `cameraState.position`
   writeability.
3. **`combinedClickable` experimental API** — MapLibre Compose target SDK 36; `combinedClickable`
   may require `@OptIn(ExperimentalFoundationApi::class)`. Confirm during implementation.

## Change Log

- 2026-05-18: created

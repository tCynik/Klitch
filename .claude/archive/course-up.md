# Plan: Course-Up Map Orientation Mode

**Date**: 2026-05-18
**Status**: Approved

## Summary

Extends the existing heading-up compass mode into a full course-up navigation experience.
The map rotates around the user's geo marker (not screen center), the marker is pinned to
the lower third of the screen `(W/2, H - W/2)`, pan gestures are converted to zoom, and
Follow Me restores the zoom level captured at mode activation. Pure presentation-layer change —
no domain or data layer modifications required.

## Scope

**In scope:**
- Rename `isHeadingUpActive` → `isCourseUpActive` throughout (UiState, ViewModel, MainScreen)
- Target offset calculation: user marker at `(W/2, H - W/2)` in portrait
- Gesture handling in course-up: scroll disabled, pinch-zoom kept, single-finger pan → zoom via Compose overlay
- Zoom capture on activation; Follow Me in course-up = restore zoom (not position)
- Compass tap from course-up: bearing → 0, target → userPos (marker back to center), animated 300 ms

**Out of scope:**
- Landscape mode (deferred)
- Exact geodetic precision for target offset (screen-space formula is sufficient)
- Any domain / data layer changes

## Architecture Notes

All changes are confined to the presentation layer:

- `MainUiState` — data class, no behaviour
- `MainViewModel` — state mutation and event emission
- `MainScreen` — `LaunchedEffect` orchestration, screen dimension reading, Compose gesture overlay
- `MapLibreLayer` — `MapOptions(gestureOptions)` toggle per mode

**Target offset formula** (derived, portrait only):
```
mpp = 156543.03392 * cos(userLat_rad) / 2^zoom
offsetM = (H_px / 2 - W_px / 2) * mpp
newLat = userLat + offsetM * cos(bearing_rad) / 111320.0
newLon = userLon + offsetM * sin(bearing_rad) / (111320.0 * cos(userLat_rad))
```
Screen pixels obtained via `LocalDensity + LocalConfiguration` inside `MainScreen`.

**Pan-as-zoom overlay** (inside `MainScreen` `Box`, above `MapLibreLayer`):
```kotlin
Modifier.pointerInput(isCourseUpActive) {
    if (!isCourseUpActive) return@pointerInput
    detectDragGestures { change, dragAmount ->
        change.consume()
        // up = zoom in, down = zoom out; ~3 zoom levels per screen height
        val delta = -dragAmount.y / screenHeightPx * 3.0
        cameraState.position = cameraState.position.copy(
            zoom = (cameraState.position.zoom + delta).coerceIn(1.0, 20.0)
        )
    }
}
```
Two-finger pinch bypasses `detectDragGestures` (single-pointer) and reaches MapLibre natively.

**Gesture deactivation guard** — change existing `GESTURE` check:
```kotlin
// Before: if (uiState.isHeadingUpActive) onHeadingUpDeactivated()
// After:  omit — course-up is never deactivated by gesture; scroll is disabled, zoom is intentional
if (!uiState.isCourseUpActive) {
    // deactivation logic only applies in free/north-up modes
}
```

**Activation zoom capture** — `onCompassLongPress()` renamed `onCourseUpToggle(currentZoom: Double)`:
- Activating: saves zoom, sets `isCourseUpActive = true`
- Deactivating (long-press while active): clears zoom, sets `isCourseUpActive = false`
The caller (`buildCompassButton`) can't pass zoom; the long-press lambda in `MainScreen`
closes over `cameraState.position.zoom` and passes it.

**Follow Me in course-up** — `onFollowMeToggle()` stays; add separate `onFollowMeRestoreZoom()`:
- In `MainScreen`, the Follow Me button's `onClick` checks `uiState.isCourseUpActive`:
  - `true` → call `onFollowMeRestoreZoom()` (emits `restoreZoomEvent: SharedFlow<Double>`)
  - `false` → call `onFollowMeToggle()` (existing behaviour)
- ViewModel `onFollowMeRestoreZoom()` emits `zoomAtCourseUpActivation` via the SharedFlow.
- `LaunchedEffect(restoreZoomEvents)` in `MainScreen` collects and animates to saved zoom,
  keeping `target` / `bearing` unchanged.

## Phase Plan

### Phase 1 — Architecture review
*Lightweight inline review — no `/architect` invocation needed (pure presentation layer).*

**Goal**: confirm no Clean Architecture violations before coding.
**Tasks**:
- Verify that zoom value flows MainScreen → ViewModel (closure capture, not stored in domain).
- Confirm `SharedFlow<Double>` for restoreZoom is coherent with existing `SharedFlow<Unit>` pattern.
- Sign off on Compose gesture overlay position in the composable tree.
**Output**: this document + checklist below marked ✅.

Checklist:
- [x] Zoom captured at presentation layer, not persisted to domain/data
- [x] `SharedFlow<Double>` follows existing `resetBearingEvent: SharedFlow<Unit>` pattern
- [x] Pan overlay sits above `MapLibreLayer` in `Box`, does not affect gesture routing for MapLibre internals
- [x] `isScrollGesturesEnabled = false` via `GestureOptions` (existing API, already used for `markToolActive`)
- [x] No new domain models, repositories, or use cases required

### Phase 3 — Implementation

**Goal**: working course-up mode on device.

**Step 3.1 — `MainUiState`** ([MainUiState.kt](../app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/MainUiState.kt))
- Rename `isHeadingUpActive` → `isCourseUpActive`
- Add `val zoomAtCourseUpActivation: Double? = null`

**Step 3.2 — `MainViewModel`** ([MainViewModel.kt](../app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/MainViewModel.kt))
- Rename all references `isHeadingUpActive` → `isCourseUpActive`
- Rename `onHeadingUpDeactivated()` → `onCourseUpDeactivated()` (keep for gesture-edge-cases if needed)
- Rename `onCompassLongPress()` → `onCourseUpToggle(currentZoom: Double)`:
  - Activating: `copy(isCourseUpActive = true, isNorthLocked = false, zoomAtCourseUpActivation = currentZoom)`
  - Deactivating: `copy(isCourseUpActive = false, zoomAtCourseUpActivation = null)`
- Add `private val _restoreZoomEvent = MutableSharedFlow<Double>()` +
  `val restoreZoomEvent: SharedFlow<Double>`
- Add `fun onFollowMeRestoreZoom()` — emits `zoomAtCourseUpActivation ?: return`
- Update `buildCompassButton()`:
  - `state.isCourseUpActive` instead of `state.isHeadingUpActive` for `selected` field
  - Set `onLongClick = null` — this callback needs Compose-layer context (zoom), so it is
    owned by `MainScreen`, not by ViewModel (see Architecture Notes → "Long-click routing")

**Step 3.3 — `MapLibreLayer`** ([MapLibreLayer.kt](../app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/osd/MapLibreLayer.kt))
- Add `isCourseUpActive: Boolean = false` parameter
- Extend `options` logic:
  ```kotlin
  options = when {
      markToolActive -> MapOptions(
          gestureOptions = GestureOptions(isDoubleTapEnabled = false, isQuickZoomEnabled = false),
          ornamentOptions = OrnamentOptions(isCompassEnabled = false),
      )
      isCourseUpActive -> MapOptions(
          gestureOptions = GestureOptions(isScrollGesturesEnabled = false),
          ornamentOptions = OrnamentOptions(isCompassEnabled = false),
      )
      else -> MapOptions(ornamentOptions = OrnamentOptions(isCompassEnabled = false))
  }
  ```

**Step 3.4 — `MainScreen`** ([MainScreen.kt](../app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/MainScreen.kt))
- Add `onCourseUpDeactivated` and `onFollowMeRestoreZoom` to signature (replacing `onHeadingUpDeactivated`)
- Read screen dimensions:
  ```kotlin
  val density = LocalDensity.current
  val configuration = LocalConfiguration.current
  val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
  val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
  ```
- Replace `LaunchedEffect(bearing, uiState.isHeadingUpActive)` with
  `LaunchedEffect(bearing, uiState.isCourseUpActive, userPosition, cameraState.position.zoom)`:
  - Guard: `if (!uiState.isCourseUpActive) return@LaunchedEffect`
  - If `userPos == null` → no-op (don't rotate without a position)
  - Compute `newTarget` using offset formula; write `cameraState.position`
- Update gesture deactivation `LaunchedEffect`:
  ```kotlin
  if (cameraState.moveReason == CameraMoveReason.GESTURE) {
      onMapGestureDetected()
      if (uiState.isFollowMeActive && !uiState.isCourseUpActive) onFollowMeDeactivated()
      // course-up is never deactivated by gesture (scroll disabled, zoom is intentional)
  }
  ```
- Update `resetBearingEvents` collector: when exiting course-up, also reset target to `userPos`
  (or keep current behaviour — bearing reset already moves the view back to north-up center)
- Add `LaunchedEffect(restoreZoomEvents)` to collect and `animateTo` saved zoom:
  ```kotlin
  LaunchedEffect(restoreZoomEvents) {
      restoreZoomEvents.collect { zoom ->
          val pos = cameraState.position
          cameraState.animateTo(CameraPosition(target = pos.target, zoom = zoom, bearing = pos.bearing), 300.ms)
      }
  }
  ```
- Pass compass long-click to `HudPortraitControls` as a dedicated parameter
  `onCompassLongClick = { viewModel.onCourseUpToggle(cameraState.position.zoom) }`.
  The composable uses this instead of `hudUiState.compass.button.onLongClick` (which is null).
  `HudPortraitControls` receives one new param: `onCompassLongClick: (() -> Unit)? = null`.
- Wire Follow Me button's `onClick`:
  ```kotlin
  if (uiState.isCourseUpActive) onFollowMeRestoreZoom() else onFollowMeToggle()
  ```
- Add pan-as-zoom Compose overlay above `MapLibreLayer` in the `Box`.

**Step 3.5 — Pass `isCourseUpActive` to `MapLibreLayer` call site in `MainScreen`**

After implementation: run `/simplify` on changed files.

### Phase 4 — Testing (lightweight)

**Goal**: confidence that the three mode transitions work correctly.

- Manual smoke test on device:
  - Mode 1 → 3 (long-press compass): marker moves to lower-third, map rotates with compass
  - Mode 3 → 1 (tap compass): bearing resets to 0, marker returns to center
  - Pan gesture in mode 3: map zooms (doesn't pan or deactivate)
  - Pinch zoom in mode 3: zooms, doesn't deactivate
  - Follow Me tap in mode 3: animates to saved zoom
  - Follow Me tap in mode 1/2: normal follow-me toggle

No unit tests added in this phase (pure view-layer behaviour, difficult to test without
MapLibre's composable context; manual on-device is sufficient).

### Phase 5 — Integration review (inline)

Checklist (verify after implementation):
- [ ] No domain imports added to presentation files
- [ ] No direct `android.util.Log` calls
- [ ] `GestureOptions.isScrollGesturesEnabled` is the only MapLibre-internal API touched
- [ ] Zoom delta formula constants are local; no magic numbers without comment

### Phase 6 — Skill update review

After implementation:
- **`/architect`**: no new patterns — formula and gesture overlay are project-specific, not canonical patterns.
- **`/ui-designer`**: no new components or design tokens.
- **`/icon-designer`**: no changes.
- **`/planner`**: no methodology gaps discovered.

### Phase 6b — Docs & memory

**Before writing documentation — discuss with user what to document.**
Ask which decisions, non-obvious behaviours, and known limitations are worth capturing
in `.claude/docs/map-orientation.md`. Do not auto-fill; wait for explicit confirmation.

- Update `.claude/docs/map-orientation.md` (after discussion)
- Update CLAUDE.md status table: "Привязка ориентации карты" → still ✅ Done (extension)
- Archive this plan: `.claude/archive/course-up.md`
- Update `memory/project_state.md` if needed

### Phase 7 — Commit

After all phases complete. Stage by name. Commit message in Russian, no Co-Authored-By.

## Coordination Map

```
Phase 1:  [inline architecture review — checklist in this document]
Phase 3:  [direct coding] →
            3.1 MainUiState
            3.2 MainViewModel
            3.3 MapLibreLayer
            3.4 MainScreen
            3.5 wire isCourseUpActive call site
          → /simplify on changed files
Phase 4:  [manual smoke test on device]
Phase 5:  [inline checklist]
Phase 6:  [skill update review — all "no changes needed" expected]
Phase 6b: [update .claude/docs/map-orientation.md, archive plan, memory]
Phase 7:  [stage by name] → [propose commit] → [wait for confirmation] → git commit
```

## Open Questions

1. **Zoom sensitivity** for pan-as-zoom overlay: `3.0 zoom levels per screen height` is an initial
   estimate. May need tuning after on-device testing.
2. **userPos == null guard**: resolved — block course-up activation when `userPos == null`.
   `onCourseUpToggle(currentZoom)` checks `currentLocation?.position` before toggling; if null,
   early-return without state mutation. Button long-press silently does nothing until GPS fix
   is acquired.
3. **Long-press lambda routing**: resolved — Variant B adopted. `buildCompassButton` sets
   `onLongClick = null`. `HudPortraitControls` accepts `onCompassLongClick: (() -> Unit)?` and
   uses it in place of the slot's null. `MainScreen` closes over `cameraState.position.zoom`
   and passes `{ viewModel.onCourseUpToggle(cameraState.position.zoom) }`. ViewModel method
   signature `onCourseUpToggle(currentZoom: Double)` makes the contract explicit.

## Change Log

- 2026-05-18: created
- 2026-05-19: Q2 resolved (block activation without GPS fix); Q3 resolved (Variant B — null slot + dedicated param in HudPortraitControls); Phase 6b updated (discuss docs before writing)

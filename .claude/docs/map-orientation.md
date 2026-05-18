# Map Orientation Binding

## What it does
Single tap on the compass HUD button resets the map bearing to north (0°) with a 300 ms animation.
Long-tap toggles **heading-up mode**: the camera bearing continuously follows the device compass
sensor. The mode auto-deactivates when the user rotates the map via gesture. Follow Me
(position tracking) is independent and can run simultaneously.

## Key classes
- `MainUiState` — `isHeadingUpActive: Boolean` (presentation layer)
- `MainViewModel` — `onCompassTap()`, `onCompassLongPress()`, `onHeadingUpDeactivated()`,
  `resetBearingEvent: SharedFlow<Unit>`
- `MainScreen` — three `LaunchedEffect`s: reset-to-north (collect SharedFlow + `animateTo`),
  heading-up (direct `cameraState.position =` write on every bearing change), gesture deactivation
- `MeshIconButton` — added `onLongClick: (() -> Unit)? = null` + `combinedClickable`
- `HudButtonSlot` — added `onLongClick` field

## Non-obvious decisions
- **Direct position write for heading-up**: `cameraState.position = CameraPosition(bearing=...)` 
  (non-animated) is used instead of `animateTo` because `bearing` from `TYPE_ROTATION_VECTOR`
  updates at sensor frequency. Direct write is responsive without spamming the animation queue.
- **`animateTo` only for reset-to-north**: the one-shot tap deserves a smooth animated transition
  (300 ms); the continuous heading-up mode does not.
- **SharedFlow for reset-to-north event**: tap is a one-shot imperative action, not persistent
  state. `MutableSharedFlow<Unit>(replay=0)` fires exactly once per tap with no reset bookkeeping.
  See `/architect` — "One-Shot Event via SharedFlow" pattern.
- **`selected = if (state.isHeadingUpActive) true else null`**: `null` = regular button,
  `true` = toggle-on glow. Button is not a standard two-state toggle — it's a mode indicator.
- **`combinedClickable` with `@OptIn(ExperimentalFoundationApi::class)`**: existing `MeshIconButton`
  used `Modifier.clickable`; switched to `combinedClickable` to add long-press. All callers that
  don't pass `onLongClick` get `null` (no behaviour change).

## Known limitations / planned extensions
- Heading-up continuously updates `cameraState.position` at sensor rate — no throttle.
  If this causes jank on low-end devices, add a `> 2f` bearing-change threshold.
- Compass button in landscape HUD (`buildLeftColumn`) is wired but landscape HUD itself is legacy
  (slot-based `HudControlsLayer`) — functional but visual design deferred.

## Source
Plan: `.claude/archive/map-orientation.md`

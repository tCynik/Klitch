# Feature: User Location Arrow with Device Orientation

> Rotates the user's location icon to face the direction the top of the screen points, using device sensors (not GPS bearing).
> Accounts for map camera rotation so the arrow stays correct in both north-up and heading-up modes.

---

## 1. Overview

### Problem

The current user location indicator is a static `CircleLayer` (blue dot). It does not convey which direction the user is facing — essential for navigation on a map.

### Solution

- Replace the static dot with a **navigation arrow icon** (`ic_navigation_arrow.xml`)
- Rotate the arrow based on **device orientation** from `Sensor.TYPE_ROTATION_VECTOR`
- Compensate for **map camera bearing** so the arrow is correct regardless of map rotation
- The arrow always points in the direction the top of the screen faces, regardless of whether the device is moving

### Rotation formula

```
arrowRotation = deviceBearing + 180° - cameraBearing
```

- `deviceBearing` — azimuth from `TYPE_ROTATION_VECTOR` (where the top of the device points)
- `cameraBearing` — current map rotation from `cameraState.position.bearing`
- `+ 180°` — compensates for `ic_navigation_arrow.xml` default orientation (points down)

### Why sensor-based, not GPS?

| | GPS Bearing | Sensor (Rotation Vector) |
|---|---|---|
| Available when stationary | No (null/stale) | Yes (always) |
| Reflects device facing direction | No (direction of travel) | Yes |
| Update rate | 1–5 s (depends on fix) | ~60 Hz (real-time) |
| Use case | Vehicle tracking | Pedestrian navigation |

---

## 2. Architecture

### Layers Affected

| Layer | Change |
|---|---|
| **app/di/** | New `DeviceOrientationProvider` registered in Koin |
| **app/presentation/feature/main/osd/** | `MapLibreLayer` — bearing included in GeoJSON properties, `cameraState` exposed |
| **app/presentation/feature/main/** | `MainScreen` — `CameraState` created here, overlay arrow rendered |
| **app/navigation/NavGraph.kt** | Inject `DeviceOrientationProvider`, pass down |

### Dependency Flow

```
NavGraph
  ├── koinInject<DeviceOrientationProvider>()
  ├── koinInject<LocationProvider>()
  └── MainScreen(locationProvider, orientationProvider)
        ├── rememberCameraState() ← shared between MapLibreLayer + overlay
        ├── MapLibreLayer(..., orientationProvider, cameraState)
        │     └── includes bearing in GeoJSON properties
        │     └── extracts cameraState.position.bearing → MapCameraPosition
        └── Image overlay (arrow)
              ├── projection.screenLocationFromPosition(position) → DpOffset
              ├── derivedStateOf { cameraState.position.bearing } → cameraBearing
              ├── .offset(x, y)
              └── .rotate(deviceBearing + 180f - cameraBearing)
```

### Graceful Degradation

If `TYPE_ROTATION_VECTOR` sensor is unavailable (rare on API 24+):
- `DeviceOrientationProvider` emits constant `0f`
- Arrow renders at 0° rotation (points north)
- No crash, no permission request needed

---

## 3. Implementation Plan

### ✅ Step 1 — Create `DeviceOrientationProvider`

**Status:** DONE

**Location:** `app/di/orientation/DeviceOrientationProvider.kt`

**Pattern:** `callbackFlow` — consistent with `AppLocationProvider` in architect.md. Sensor is registered on subscription and unregistered on cancellation (`awaitClose`). No manual `start()` / `stop()` lifecycle management.

### ✅ Step 2 — Register in Koin DI

**Status:** DONE

**Location:** `app/di/OrientationModule.kt`

Registered in `MyMeshApplication.kt` via `orientationModule`.

### ✅ Step 3 — Propagate through NavGraph → MainScreen → MapLibreLayer

**Status:** DONE

- `NavGraph.kt` — `koinInject<DeviceOrientationProvider>()`
- `MainScreen` — accepts `orientationProvider: DeviceOrientationProvider`
- `MapLibreLayer` — accepts `orientationProvider: DeviceOrientationProvider` + `cameraState: CameraState`

### ❌ Step 4 — Register Arrow Icon in Style (original plan)

**Status:** BLOCKED — maplibre-compose 0.12.1 bug

The `SymbolLayer` data class shadows the `SymbolLayer` Composable function (same name, same package), making it impossible to pass `iconImage` / `iconRotate` parameters from Kotlin. All 6 workaround attempts failed:
1. Named parameters → "No parameter with name 'iconImage' found"
2. Positional parameters → "Too many arguments for constructor"
3. `SymbolLayerKt.SymbolLayer(...)` explicit call → "Unresolved reference"
4. Fully-qualified name → still resolves to constructor
5. Java helper class → can't call @Composable from Java
6. Kotlin file in same package → still prefers class over function

**Issue tracked:** maplibre-compose discussions #468, #533, #535, #658

### ✅ Step 5 — Include Bearing in GeoJSON Properties

**Status:** DONE (GeoJSON includes bearing, but not used by SymbolLayer due to §4)

### ✅ Step 6 & 7 — Arrow Overlay Workaround (actual implementation)

**Status:** DONE — via Composable overlay instead of SymbolLayer

Since `SymbolLayer` is unusable in 0.12.1, the arrow is rendered as a **Compose Image overlay** on top of the map:

```kotlin
// MainScreen.kt
val cameraState = rememberCameraState(...)
val projection = cameraState.projection
val screenOffset = projection.screenLocationFromPosition(userPosition)

// Camera bearing — compensates for map rotation
val cameraBearing by remember {
    derivedStateOf { cameraState.position.bearing.toFloat() }
}

Image(
    painter = painterResource(R.drawable.ic_navigation_arrow),
    modifier = Modifier
        .offset { IntOffset(pxX, pxY) }
        .size(36.dp)
        .rotate(deviceBearing + 180f - cameraBearing),  // compensates for map rotation
)
```

**Key details:**
- `CameraState` is created in `MainScreen` and passed to `MapLibreLayer` (not created inside it)
- `CameraProjection.screenLocationFromPosition(Position)` converts geo → screen coordinates
- `+ 180f` compensates for `ic_navigation_arrow.xml` default orientation (points down)
- `- cameraBearing` compensates for map camera rotation (heading-up mode)
- Blue dot (`CircleLayer`) removed — arrow is the only user location indicator

---

## 4. File Changes Summary

| File | Action | Description |
|---|---|---|
| `app/di/orientation/DeviceOrientationProvider.kt` | **Create** | Sensor-based orientation via `callbackFlow`, emits `StateFlow<Float>` |
| `app/di/OrientationModule.kt` | **Create** | Koin registration |
| `app/di/LocationDomainModule.kt` | No change | Already registers `LocationProvider` |
| `app/navigation/NavGraph.kt` | **Modify** | Inject `DeviceOrientationProvider`, pass to `MainScreen` |
| `app/presentation/feature/main/MainScreen.kt` | **Modify** | Accept `orientationProvider`, create `CameraState`, render arrow overlay with camera bearing compensation |
| `app/presentation/feature/main/osd/MapLibreLayer.kt` | **Modify** | Accept `cameraState` param, include bearing in GeoJSON, extract `cameraState.position.bearing` → `MapCameraPosition`, remove user dot |
| `app/src/main/res/drawable/ic_navigation_arrow.xml` | No change | Already exists |
| `app/domain/map/model/MapCameraPosition.kt` | **Modify** | Added `bearing: Double = 0.0` field |

---

## 5. Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Sensor not available (rare on API 24+) | Arrow stuck at 0° | `getDefaultSensor()` returns null → provider emits `0f` |
| Jittery rotation | Poor UX | Low-pass filter (`alpha = 0.95f`) in `callbackFlow` listener — balances smoothness vs lag |
| GeoJSON `bearing` as string | No rotation (NaN) | Ensure numeric: `$bearing` not `"$bearing"` |
| Permission issues (sensor requires no permission) | N/A | `TYPE_ROTATION_VECTOR` needs no runtime permission |
| Sensor leaks battery (if listener not unregistered) | Battery drain | `awaitClose { unregisterListener }` in `callbackFlow` — guaranteed cleanup on flow cancellation |
| `cameraState.projection` is null | Arrow not rendered | Guarded by `if (projection != null && ...)` check |
| Map rotation not compensated | Arrow points wrong direction in heading-up mode | `cameraBearing` extracted from `cameraState.position.bearing` and subtracted in rotation formula |

---

## 6. Testing Considerations

### Unit Tests
- `DeviceOrientationProvider`: test bearing normalization (radians → [0, 360))
- Low-pass filter: verify output smoothness formula
- Graceful degradation: mock `getDefaultSensor() == null` → bearing stays `0f`

### Manual Testing
- Rotate device physically — arrow should follow
- Keep device stationary, rotate in place — arrow should still respond
- Verify no stuttering or lag in rotation
- Test on multiple devices (some have different sensor hardware)
- Verify arrow aligns with actual device heading on map
- **Rotate the map** (pinch-rotate gesture) — arrow should maintain correct orientation relative to the map
- Test in both north-up and heading-up modes — arrow should be accurate in both

---

## 7. Future Enhancements

| Enhancement | Description |
|---|---|
| **Tap to recenter** | Tap the arrow → camera animates to user location |
| **Speed-based visibility** | Hide arrow when stationary, show only dot (if using GPS bearing hybrid) |
| **SDF tint by accuracy** | Color the arrow based on location accuracy (green = precise, red = poor) |
| **Smooth animation** | Interpolate bearing changes over 100–200 ms instead of instant snap |
| **SymbolLayer arrow** | When maplibre-compose is updated, migrate from overlay to proper SymbolLayer with `iconImage` + `iconRotate = get("bearing")` |

---

## Implementation Note

> **Why overlay instead of SymbolLayer?**
>
> maplibre-compose 0.12.1 has a name-shadowing bug: the `SymbolLayer` data class
> (constructor: `id`, `source`) shadows the `SymbolLayer` Composable function
> (~67 params including `iconImage`, `iconRotate`, etc.) because they share the
> same name and package. Kotlin always resolves to the class constructor.
>
> The workaround uses `CameraProjection.screenLocationFromPosition()` to convert
> the user's geographic position to screen coordinates, then renders a standard
> Compose `Image` overlay with `.rotate(bearing)`. This bypasses the SymbolLayer
> limitation entirely while achieving the same visual result.
>
> When maplibre-compose is updated with a fix, the overlay can be replaced with
> a proper SymbolLayer in ~7 lines of code — all infrastructure (bearing collection,
> GeoJSON properties, drawable) is already in place.

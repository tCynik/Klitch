# maplibre-compose 0.12.1 — Camera Animation API

**Package:** `org.maplibre.compose.camera`
**Source tag:** `0.12.1` (verified against `main`; API is stable as of this tag)

---

## 1. Programmatic animation — `animateTo`

Two overloads on `CameraState`:

```kotlin
// Animate to a lat/lon + zoom position
public suspend fun animateTo(
    finalPosition: CameraPosition,
    duration: Duration = 300.milliseconds,
)

// Animate to fit a bounding box
public suspend fun animateTo(
    boundingBox: BoundingBox,
    bearing: Double = 0.0,
    tilt: Double = 0.0,
    padding: PaddingValues = PaddingValues(0.dp),
    duration: Duration = 300.milliseconds,
)
```

`CameraPosition` data class:
```kotlin
data class CameraPosition(
    val bearing: Double = 0.0,
    val target: Position = Position(0.0, 0.0),  // lon, lat (GeoJSON order)
    val tilt: Double = 0.0,
    val zoom: Double = 1.0,
    val padding: PaddingValues = PaddingValues(0.dp),
)
```

Typical usage:
```kotlin
LaunchedEffect(targetLat, targetLon) {
    cameraState.animateTo(
        finalPosition = CameraPosition(
            target = Position(longitude = targetLon, latitude = targetLat),
            zoom = 15.0
        ),
        duration = 500.milliseconds
    )
}
```

There is also a non-animated jump:
```kotlin
// Direct property assignment — no animation, fires immediately
cameraState.position = CameraPosition(target = Position(lon, lat), zoom = 15.0)
```

---

## 2. Animation type — suspend fun

Both `animateTo` overloads are **`suspend fun`**.  
They suspend until the animation completes (or the coroutine is cancelled).  
Cancelling the parent coroutine cancels the animation mid-flight.

---

## 3. Detecting animation completion

Because `animateTo` is a suspend function, completion is implicit — code after the call resumes when the animation finishes:

```kotlin
scope.launch {
    cameraState.animateTo(finalPosition)
    // execution reaches here only when animation is done
    onAnimationComplete()
}
```

Additional state properties available during/after movement:

```kotlin
val isCameraMoving: Boolean          // true while the camera is in motion
val metersPerDpAtTarget: Double      // useful for scale-sensitive UI
```

---

## 4. User vs programmatic movement

`CameraState.moveReason` exposes a `CameraMoveReason` enum:

```kotlin
public enum class CameraMoveReason {
    NONE,         // hasn't moved yet
    UNKNOWN,      // unexpected reason
    GESTURE,      // user dragged/pinched/rotated the map
    PROGRAMMATIC, // call to public API (animateTo / position =) or compass ornament
}
```

Usage pattern for follow-mode (re-enable tracking after user gesture):

```kotlin
LaunchedEffect(cameraState.moveReason) {
    if (cameraState.moveReason == CameraMoveReason.GESTURE) {
        followMode = false
    }
}
```

---

## Known limitation

Calling `animateTo` when the camera is already animating cancels the previous animation and restarts. This causes "jerky" movement in high-frequency location tracking. Workaround: maintain a separate `destination` state variable and only pass that to `animateTo`, rather than copying `cameraState.position` (current).

---

## Sources

- Source: `lib/maplibre-compose/src/commonMain/kotlin/org/maplibre/compose/camera/CameraState.kt`
- Source: `lib/maplibre-compose/src/commonMain/kotlin/org/maplibre/compose/camera/CameraMoveReason.kt`
- Source: `lib/maplibre-compose/src/commonMain/kotlin/org/maplibre/compose/camera/CameraPosition.kt`
- GitHub repo: https://github.com/maplibre/maplibre-compose
- Discussion #534: https://github.com/maplibre/maplibre-compose/discussions/534
- Discussion #727: https://github.com/maplibre/maplibre-compose/discussions/727

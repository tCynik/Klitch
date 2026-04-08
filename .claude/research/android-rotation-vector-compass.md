# Research: Android TYPE_ROTATION_VECTOR — Azimuth, Wraparound, Filtering

**Date**: 2026-04-08
**Feature**: User Location Arrow (feature-user-location-arrow.md)

---

## Findings

- `SensorManager.getOrientation()` returns azimuth (`values[0]`) in **radians**, range **-π … +π** (not degrees, not 0…2π)
- 0 = magnetic north; positive values = clockwise when viewed from above; negative = counter-clockwise
- Conversion to 0–360°: `(Math.toDegrees(azimuthRad) + 360) % 360`
- Wraparound problem: naive animation from 359° to 1° spins the arrow 358° the wrong way
- Compose canonical fix: store a **cumulative float** (not clamped to 0–360), compute delta = `((new - old + 540) % 360) - 180`, pass to `animateFloatAsState()` — accumulator never wraps, animation always takes the short path
- Low-pass filter: `filtered = alpha * prev + (1 - alpha) * current`, recommended alpha = **0.8**
- Filter on **quaternion components** (before `getOrientation()`), not on the derived azimuth — avoids discontinuities at the ±π boundary
- Sensor delay: `SENSOR_DELAY_UI` (60 ms) is sufficient; `SENSOR_DELAY_FASTEST` is excessive for UI use

## Constraints for this project

- The formula `deviceBearing + 180° − cameraBearing` requires `deviceBearing` in 0–360° degrees — conversion is mandatory before substitution
- The cumulative accumulator for Compose animation must live in `remember {}`, not in ViewModel (pure UI state)
- Quaternion filtering is safer than azimuth filtering — especially when crossing north

## Open questions

- None: all questions resolved for the current implementation

## Sources

- Android Developers — SensorManager: https://developer.android.com/reference/android/hardware/SensorManager
- Android Developers — Position sensors: https://developer.android.com/develop/sensors-and-location/sensors/sensors_position
- Android Developers — Motion sensors (low-pass filter): https://developer.android.com/guide/topics/sensors/sensors_motion

# maplibre-compose 0.12.1 — API Reference & Patterns

> Library: `org.maplibre.compose:maplibre-compose:0.12.1`
> Source: [maplibre-compose GitHub](https://github.com/maplibre/maplibre-compose)
> Dokka: [API Reference](https://maplibre.org/maplibre-compose/api/)

---

## SymbolLayer

Renders icons and/or text at point geometries (or along lines).

### Signature (key parameters only)

```kotlin
@Composable
fun SymbolLayer(
    id: String,
    source: Source,
    // — Icon —
    iconImage: Expression<ImageValue> = nil(),
    iconRotate: Expression<FloatValue> = const(0f),
    iconSize: Expression<FloatValue> = const(1f),
    iconAnchor: Expression<SymbolAnchor> = const(SymbolAnchor.Center),
    iconOffset: Expression<DpOffsetValue> = const(DpOffset.Zero),
    iconRotationAlignment: Expression<IconRotationAlignment> = const(IconRotationAlignment.Auto),
    iconPitchAlignment: Expression<IconPitchAlignment> = const(IconPitchAlignment.Auto),
    iconKeepUpright: Expression<BooleanValue> = const(false),
    iconColor: Expression<ColorValue> = const(Color.Black),       // SDF only
    iconHaloColor: Expression<ColorValue> = const(Color.Transparent),
    iconHaloWidth: Expression<DpValue> = const(0.dp),
    iconHaloBlur: Expression<DpValue> = const(0.dp),
    // — Text —
    textField: Expression<FormattedValue> = const("").cast(),
    textSize: Expression<TextUnitValue> = const(1.em),
    textColor: Expression<ColorValue> = const(Color.Black),
    textOffset: Expression<TextUnitOffsetValue> = offset(0f.em, 0f.em),
    // — Placement —
    placement: Expression<SymbolPlacement> = const(SymbolPlacement.Point),
    iconAllowOverlap: Expression<BooleanValue> = const(false),
    textAllowOverlap: Expression<BooleanValue> = const(false),
    iconTranslate: Expression<DpOffsetValue> = const(DpOffset.Zero),
    iconTranslateAnchor: Expression<TranslateAnchor> = const(TranslateAnchor.Map),
    onClick: FeaturesClickHandler? = null,
    onLongClick: FeaturesClickHandler? = null,
)
```

### Key icon parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `iconImage` | `Expression<ImageValue>` | `nil()` | Image ID string or expression. Ignored if nil. |
| `iconRotate` | `Expression<FloatValue>` | `const(0f)` | **Clockwise rotation in degrees**. Ignored without `iconImage`. |
| `iconSize` | `Expression<FloatValue>` | `const(1f)` | Scale multiplier. `1` = original, `3` = tripled. |
| `iconRotationAlignment` | `Expression<IconRotationAlignment>` | `Auto` | `Auto`, `Map`, `Viewport`, `Default` |
| `iconPitchAlignment` | `Expression<IconPitchAlignment>` | `Auto` | `Auto`, `Map`, `Viewport` — how icon tilts with 3D pitch |
| `iconKeepUpright` | `Expression<BooleanValue>` | `false` | If true, rotates icon back to stay upright when map is pitched |
| `iconAnchor` | `Expression<SymbolAnchor>` | `Center` | `Center`, `Left`, `Right`, `Top`, `Bottom`, etc. |
| `iconOffset` | `Expression<DpOffsetValue>` | `Zero` | Offset from anchor in dp |

### Usage example

```kotlin
SymbolLayer(
    id = "user-location-arrow",
    source = locationSource,
    iconImage = const("navigation-arrow"),
    iconSize = const(1.5f),
    iconRotate = get("bearing"),        // dynamic per-feature rotation
    iconAnchor = const(SymbolAnchor.Center),
    iconAllowOverlap = const(true),
)
```

---

## CircleLayer

Renders circles at point geometries.

### Signature (key parameters)

```kotlin
@Composable
fun CircleLayer(
    id: String,
    source: Source,
    color: Expression<ColorValue>,
    radius: Expression<DpValue>,
    strokeColor: Expression<ColorValue> = const(Color.Transparent),
    strokeWidth: Expression<DpValue> = const(0.dp),
    blur: Expression<DpValue> = const(0.dp),
    opacity: Expression<FloatValue> = const(1f),
)
```

### Usage example

```kotlin
CircleLayer(
    id = "user-location-dot",
    source = locationSource,
    color = const(Color(0xFF2196F3)),
    radius = const(8.dp),
    strokeColor = const(Color.White),
    strokeWidth = const(2.dp),
)
```

---

## Registering Images in Style (`addImage`)

Images must be registered on the **Style** object before being referenced by `iconImage`.

### API

```kotlin
val style = LocalStyle.current
LaunchedEffect(Unit) {
    style.addImage("image-id", bitmap)
    // For SDF icons (support tinting via iconColor):
    style.addImage("sdf-icon-id", sdfBitmap, isSdf = true)
}
```

### Converting Android drawable → Bitmap

```kotlin
fun Context.drawableToBitmap(@DrawableRes resId: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(this, resId)!!
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
```

### Full pattern: load icon + use in SymbolLayer

```kotlin
@Composable
fun MapLibreLayer(...) {
    MaplibreMap(...) {
        val style = LocalStyle.current
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            val bitmap = context.drawableToBitmap(R.drawable.ic_navigation_arrow)
            style.addImage("navigation-arrow", bitmap, isSdf = true)
        }

        val locationSource = rememberGeoJsonSource(...)

        SymbolLayer(
            id = "user-location-arrow",
            source = locationSource,
            iconImage = const("navigation-arrow"),
            iconSize = const(1.5f),
            iconRotate = get("bearing"),
            iconAnchor = const(SymbolAnchor.Center),
            iconAllowOverlap = const(true),
        )
    }
}
```

---

## Expressions API

Expressions are built via `org.maplibre.compose.expressions.dsl.*`.

### Common expressions

| Function | Signature | Description |
|---|---|---|
| `const(value)` | `const(T): Expression<T>` | Literal constant value |
| `get(property)` | `get(String): Expression<*>` | Read a property from the feature's `properties` object |
| `multiply(a, b)` | `multiply(Expression, Expression): Expression<FloatValue>` | Multiply two expressions |
| `case(condition, result, ...)` | `case(...): Expression<T>` | Conditional: `case(cond1, result1, default)` |
| `match(input, cases, default)` | `match(...): Expression<T>` | Switch-like: `match(get("type"), "a", 1, "b", 2, 0)` |
| `interpolate(stops, input)` | `interpolate(...): Expression<T>` | Interpolation (zoom-dependent values) |
| `nil()` | `nil(): Expression<T>` | Null/unset expression |
| `offset(x, y)` | `offset(Dp, Dp): Expression<DpOffsetValue>` | Create a DpOffset |

### Dynamic rotation via `get()`

```kotlin
// GeoJSON feature must have "bearing" in properties
SymbolLayer(
    id = "arrows",
    source = source,
    iconRotate = get("bearing"),  // reads bearing property per feature
)
```

---

## GeoJsonData.JsonString Bypass

### Why

`spatialk:geojson:0.6.0` crashes when serializing an empty `FeatureCollection()` — `firstNotNullOf` throws on `LocationPuck`'s initial null-location path.

### Solution

Use `GeoJsonData.JsonString` to bypass spatialk polymorphic serialization entirely:

```kotlin
val locationGeoJson = remember(currentLocation, bearing) {
    val loc = currentLocation
    if (loc != null) {
        val lon = loc.position.longitude
        val lat = loc.position.latitude
        val bearingValue = bearing
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"bearing":$bearingValue}}]}"""
    } else {
        """{"type":"FeatureCollection","features":[]}"""
    }
}
val locationSource = rememberGeoJsonSource(GeoJsonData.JsonString(locationGeoJson))
```

### Important

- GeoJSON coordinates are always `[longitude, latitude]` (NOT lat,lon)
- Properties values for `iconRotate` must be numeric (no quotes around the number)
- Bearing is in degrees, clockwise from north (0° = north, 90° = east)

---

## Sensor-Based Device Orientation

### SensorManager + TYPE_ROTATION_VECTOR

The rotation vector sensor provides the device orientation relative to Earth's coordinate system. This is the **correct sensor** for compass-style arrow rotation on a map.

```kotlin
class DeviceOrientationProvider(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _bearing = MutableStateFlow(0f)
    val bearing: StateFlow<Float> = _bearing.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    val orientationAngles = FloatArray(3)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            // azimuth is in radians, range [-π, π]
            var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            // Normalize to [0, 360)
            if (azimuth < 0) azimuth += 360f
            _bearing.value = azimuth
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        sensorManager.registerListener(
            listener,
            rotationVectorSensor,
            SensorManager.SENSOR_DELAY_UI,   // ~60 Hz, good balance of smooth/responsiveness
        )
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }
}
```

### Smoothing (low-pass filter)

Raw sensor data can be jittery. Apply a low-pass filter for smoother arrow rotation:

```kotlin
// In onSensorChanged:
val alpha = 0.85f  // higher = smoother but more lag
val filteredAzimuth = alpha * azimuth + (1f - alpha) * lastFilteredAzimuth
lastFilteredAzimuth = filteredAzimuth
_bearing.value = filteredAzimuth
```

### Why not GPS bearing?

- GPS bearing only updates when the device is **moving** (direction of movement)
- GPS bearing is unavailable or stale when the device is stationary
- `TYPE_ROTATION_VECTOR` gives orientation **regardless of movement** — the arrow always points the direction the top of the screen faces, even when standing still
- This is the expected UX behavior for navigation-style map arrows

---

## MapLibreMap Context

All `rememberXxxSource`, layer composables, and `LocalStyle` access must be inside the `MaplibreMap { }` content lambda — they read `LocalStyleNode` which is only provided within that scope.

```kotlin
@Composable
fun MapLibreLayer(...) {
    MaplibreMap(...) {
        // All sources and layers go here
        val tileSource = rememberRasterSource(...)
        RasterLayer(id = "base-layer", source = tileSource)

        val geoJsonSource = rememberGeoJsonSource(...)
        CircleLayer(id = "dot-layer", source = geoJsonSource, ...)
        SymbolLayer(id = "arrow-layer", source = geoJsonSource, ...)
    }
}
```

---

## Anti-Patterns

| Anti-pattern | Correct |
|---|---|
| `LocationPuck` / `rememberUserLocationState` | `CircleLayer + GeoJsonData.JsonString` (spatialk crashes on empty FeatureCollection) |
| Using `Location.bearing` for arrow rotation | Use sensor-based `TYPE_ROTATION_VECTOR` bearing (GPS bearing only works while moving) |
| Registering image outside `MaplibreMap { }` | `style.addImage()` must be called inside the `MaplibreMap` content lambda (or via `LocalStyle.current`) |
| Bearing as string in GeoJSON properties | Bearing must be a numeric value: `"bearing":123.45` not `"bearing":"123.45"` |
| Hardcoded icon rotation | Use `get("bearing")` expression to read per-feature rotation from GeoJSON properties |
| Updating source inside `MaplibreMap { }` recomposition | Use `remember(data) { buildJson() }` — source is recreated when data changes, MapLibre handles the update |

# Map Orientation Binding

## Режимы

Карта существует в трёх состояниях:

| Состояние | `isNorthLocked` | `isCourseUpActive` | Визуал кнопки |
|---|---|---|---|
| Север зафиксирован (compass tap) | `true` | `false` | `selected = false` (затушена) |
| Свободный поворот (пользователь крутил) | `false` | `false` | `selected = null` (нейтральная) |
| Course-up (long-tap) | `false` | `true` | `selected = true` (активна) |

## Иконка компаса

`buildCompassButton` (ViewModel):
- `isNorthLocked = true` → `ic_compass`, статичная, `selected = false`
- `isNorthLocked = false` → `ic_compass_rotated`, вращается с `mapBearing`, `selected = null / true`
- `iconRotationDegrees = -mapBearing - 45f` — смещение на 45° компенсирует ориентацию drawable

## Управление `isNorthLocked`

Флаг **не** сбрасывается при любом жесте (это вызывало мигание при pan). Два отдельных пути:

- `onMapBearingChanged(bearing)` — только обновляет `mapBearing`. Вызывается при программном изменении bearing (анимация reset, course-up).
- `onMapRotatedByUser(bearing)` — обновляет `mapBearing` + сбрасывает `isNorthLocked`. Вызывается только когда bearing изменился **от жеста пользователя**.

В `MainScreen`, `LaunchedEffect(cameraState.position.bearing)`:
```kotlin
val b = cameraState.position.bearing
if (cameraState.moveReason == CameraMoveReason.GESTURE) onMapRotatedByUser(b)
else onMapBearingChanged(b)
```

Pan не меняет bearing → `LaunchedEffect` не срабатывает → `isNorthLocked` не затрагивается.  
Reset-анимация меняет bearing, но `moveReason = DEVELOPER` → `onMapBearingChanged`, `isNorthLocked` остаётся `true`.

## Сброс на север (compass tap)

`onCompassTap()` → `isNorthLocked = true`, emit `resetBearingEvent`.

`LaunchedEffect(resetBearingEvents)` в MainScreen:
```kotlin
cameraState.animateTo(
    CameraPosition(bearing = 0.0, target = pos.target, zoom = pos.zoom),
    duration = 300.ms,
)
```

Используется `pos.target` (текущий центр карты), **не** `currentLocation.position` — compass tap сбрасывает только bearing, без перемещения камеры к пользователю.

## Азимут в info-слоте

Когда `!isNorthLocked` — рядом с кнопкой компаса отображается текущий азимут взгляда:
```kotlin
info = if (!state.isNorthLocked)
    HudInfoSlot(content = "${state.mapBearing.toInt()}°", color = Color.Red)
else
    emptyInfoSlot()
```

Обновляется каждый фрейм через `mapBearing`. В режиме course-up тоже отображается (отражает курс устройства).

## Course-up

Long-tap на компас активирует course-up (только при наличии GPS-фикса).

### Активация

`onCourseUpToggle(currentZoom: Double)` в ViewModel:
- Сохраняет `zoomAtCourseUpActivation = currentZoom`
- `isCourseUpActive = true`, `isNorthLocked = false`

Zoom снимается в `MainScreen` — там есть доступ к `cameraState.position.zoom`. ViewModel принимает его параметром: `onCourseUpToggle(cameraState.position.zoom)`.

### Позиционирование камеры (target offset)

Пользователь должен быть в точке `(W/2, H − W/2)` экрана — нижняя треть в портрете. Камера смещается вперёд по курсу:

```kotlin
private fun metersPerPixel(latRad: Double, zoom: Double): Double =
    78271.51696 * cos(latRad) / 2.0.pow(zoom)
// MapLibre использует тайлы 512px, поэтому константа = 40_075_016 / 512 = 78_271, а не 156_543
// mpp возвращает метры/dp (не метры/физический пиксель)

val mpp = metersPerPixel(userLatRad, zoom)
val offsetM = (mapHeightDp / 2f - mapWidthDp / 2f) * mpp
val newLat = userLat + offsetM * cos(bearingRad) / 111320.0
val newLon = userLon + offsetM * sin(bearingRad) / (111320.0 * cos(userLatRad))
cameraState.position = CameraPosition(bearing = bearing.toDouble(), target = Position(newLon, newLat), zoom = zoom)
```

Размеры карты (`mapWidthDp`, `mapHeightDp`) берутся из `BoxWithConstraints.maxWidth.value / maxHeight.value` — они отражают фактический размер composable с учётом insets и multi-window, в отличие от `LocalConfiguration.screenWidthDp`.

### Жесты в course-up

- **Scroll (pan)** — отключён: `GestureOptions(isScrollGesturesEnabled = false)` в `MapOptions`
- **Pinch-zoom** — работает нативно через MapLibre
- **Single-finger drag** — перехватывается Compose-оверлеем и конвертируется в zoom:

```kotlin
if (uiState.isCourseUpActive) {
    Box(Modifier.fillMaxSize().pointerInput(mapHeightPx) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            val delta = -dragAmount.y / mapHeightPx * 3.0
            cameraState.position = CameraPosition(
                target = current.target, zoom = (current.zoom + delta).coerceIn(1.0, 20.0), bearing = current.bearing
            )
        }
    })
}
```

Оверлей добавляется **условно** (`if (isCourseUpActive)`), а не через `return@pointerInput` — иначе пустой `Box` с `pointerInput` перехватывает касания даже при раннем выходе из лямбды.

### Follow Me в course-up

В обычном режиме Follow Me — toggle позиции. В course-up Follow Me восстанавливает zoom, сохранённый при активации:

```kotlin
onFollowMeClick = {
    if (uiState.isCourseUpActive) onFollowMeRestoreZoom() else hudUiState.target.button.onClick()
}
```

`onFollowMeRestoreZoom()` эмитит `zoomAtCourseUpActivation` через `restoreZoomEvent: SharedFlow<Double>`.  
`LaunchedEffect(restoreZoomEvents)` в MainScreen собирает и анимирует к сохранённому zoom за 300 мс.

## Инфраструктура HUD

### Выравнивание info-слота

`HudInfoSlotItem` принимает `side: HudSide` и прижимает контент к кнопке:
- `HudSide.Left` → `Alignment.CenterStart`
- `HudSide.Right` → `Alignment.CenterEnd`

### Long-click на компас (Variant B)

`buildCompassButton` устанавливает `onLongClick = null`. `HudPortraitControlsLayer` принимает `onCompassLongClick: (() -> Unit)?` и подставляет в слот при рендере. `MainScreen` закрывает `cameraState.position.zoom` в лямбде:
```kotlin
onCompassLongClick = {
    if (currentLocation?.position != null) onCourseUpToggle(cameraState.position.zoom)
}
```

ViewModel не знает о `cameraState` — zoom передаётся через параметр вызова.

## Ключевые классы

- `MainUiState` — `isCourseUpActive`, `zoomAtCourseUpActivation`, `isNorthLocked`, `mapBearing`
- `MainViewModel` — `onCompassTap()`, `onCourseUpToggle(Double)`, `onMapBearingChanged(Double)`, `onMapRotatedByUser(Double)`, `onFollowMeRestoreZoom()`, `resetBearingEvent: SharedFlow<Unit>`, `restoreZoomEvent: SharedFlow<Double>`
- `MainScreen` — шесть `LaunchedEffect`: followMe, gesture/followMe-deactivation, resetBearing, restoreZoom, bearing/northLock, courseUp-positioning; pan-as-zoom overlay
- `MapLibreLayer` — `isCourseUpActive` → `GestureOptions(isScrollGesturesEnabled = false)`
- `HudPortraitControlsLayer` — `onCompassLongClick`, `onFollowMeClick`
- `HudInfoSlotItem` — `side: HudSide` для выравнивания

## Известные ограничения

- Landscape HUD: course-up не протестирован в ландшафтной ориентации (landscape HUD — legacy slot-based, деferred).
- Чувствительность pan-as-zoom (3.0 zoom/экран) — начальная оценка, может требовать подстройки.
- `mapBearing.toInt()` — усечение без нормализации; `360.0` → "360°" при float-погрешности (крайне редко).

## Источники

- План (архив): `.claude/archive/course-up.md`
- Предыдущая версия документа (heading-up): `.claude/archive/map-orientation.md`

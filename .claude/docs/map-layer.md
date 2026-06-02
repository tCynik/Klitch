# MapLibre Layer Architecture

## What it does

`MapLibreLayer` — единый Composable, рендерящий все визуальные элементы на карте:
- Base raster tiles (OpenTopoMap)
- KMZ/KML overlays
- Node markers (online/offline/stale)
- User location arrow
- Geo marks (points + tracks)
- Draft marks (pending send)

## Key classes

- `MapLibreLayer` — `presentation/feature/main/osd/MapLibreLayer.kt`
- `NodeMarkerModel` — domain model узла с координатами, именем, heading
- `GeoMarkModel` — domain model метки (point/track)
- `animateGeoJsonInterpolation` — интерполяция позиций нод между обновлениями (~60 fps, easeInOut)

## Base style

```kotlin
private val BASE_STYLE_WITH_GLYPHS = BaseStyle.Json(
    """{"version":8,"glyphs":"asset://fonts/{fontstack}/{range}.pbf","sources":{},"layers":[...]}"""
)
```

`BaseStyle.Empty` не имеет `glyphs` URL — без него MapLibre ломает **все** `SymbolLayer`-слои (включая icon-only), даже если текст не запрошен. Поэтому используется кастомный стиль с `glyphs`.

## Bundled fonts

Glyphs хранятся локально в `app/src/main/assets/fonts/` — **без зависимости от сети**.

**Причина:** внешний glyph-сервер (demotiles.maplibre.org) иногда недоступен на конкретных устройствах (timeout, DNS race). MapLibre при ошибке загрузки глифов гробит весь symbol renderer — пропадают не только подписи, но и все icon-SymbolLayer. `CircleLayer`/`LineLayer` при этом работают нормально.

### Расположение файлов

```
app/src/main/assets/
  fonts/
    Noto Sans Regular/
      0-255.pbf       # Basic Latin + Latin-1 (~30 KB)
      1024-1279.pbf   # Cyrillic (~40 KB)
```

### Добавление нового диапазона

Если появились символы вне Latin/Cyrillic (спец. символы, другой язык):

1. Определить Unicode диапазон нужного символа (кратно 256: floor(codepoint / 256) * 256)
2. Скачать файл:
   ```
   https://demotiles.maplibre.org/font/Noto%20Sans%20Regular/<start>-<end>.pbf
   ```
3. Положить в `app/src/main/assets/fonts/Noto Sans Regular/`

### Первая установка (если файлы не добавлены в репозиторий)

```
https://demotiles.maplibre.org/font/Noto%20Sans%20Regular/0-255.pbf
https://demotiles.maplibre.org/font/Noto%20Sans%20Regular/1024-1279.pbf
```

## Text layers — шрифт

Все `SymbolLayer` с `textField` явно задают шрифт:
```kotlin
textFont = const(listOf("Noto Sans Regular"))
```

Без явного `textFont` MapLibre использует дефолтный font stack (`Open Sans Regular, Arial Unicode MS Regular`), который не совпадает с именем директории в assets — глифы не найдутся.

## Source data update — SideEffect

`rememberGeoJsonSource` использует `LaunchedEffect` с key=lambda — ненадёжно при загрузке стиля. Для обхода данные обновляются через `SideEffect` (выполняется синхронно после каждой рекомпозиции):

```kotlin
SideEffect {
    peerOnlineSource.setData(...)
    peerOfflineSource.setData(...)
    peerStaleSource.setData(...)
}
```

## Layer stack (bottom → top)

1. `base-raster-layer` — тайлы
2. `overlay-ground-*` / `overlay-fill-*` / `overlay-line-*` — KMZ/KML
3. `node-stale-dot` + `node-stale-label` — устаревшие ноды
4. `node-online-stationary` / `node-online-moving` — онлайн ноды (иконки)
5. `node-remote-offline-dot` — оффлайн ноды
6. `node-remote-online-label` / `node-remote-offline-label` — подписи нод
7. `user-location-arrow` — позиция пользователя
8. `geo-draft-*` — черновики меток
9. `geo-selected-mark-ring` — выбранная метка (красное кольцо)
10. `geo-received-points` + `geo-received-point-labels` — точки
11. `geo-received-tracks-*` — треки + якоря + концы

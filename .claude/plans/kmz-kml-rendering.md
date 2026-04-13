# Plan: KMZ/KML Overlay Rendering

**Date**: 2026-04-13
**Status**: Planned

## Summary

Отображение импортированных KMZ/KML оверлеев на карте MapLibre. Пользователь включает/выключает
отображение чекбоксом в настройках (Settings → Map tab). Состояние `is_selected` уже персистируется
в SQLDelight. Импорт файлов реализован. Эта фича добавляет:

1. **Парсинг при импорте** — KMZ/KML → GeoJSON (вектор) + PNG (GroundOverlay), кэш в `filesDir`
2. **Обновление схемы** — пути к кэшированным файлам хранятся в SQLDelight
3. **Рендеринг в MapLibreLayer** — `GeoJsonSource` + слои для вектора; `ImageSource` + `RasterLayer` для растра
4. **MainViewModel** — наблюдает за выбранными оверлеями и передаёт данные в MapLibreLayer

---

## Architecture Notes

### Парсинг: import-time (рекомендовано research)
- KMZ/KML тяжело парсить на каждом открытии карты → парсим один раз при импорте
- Результат сохраняется в `context.filesDir/overlays/{id}/`:
  - `features.geojson` — вектор (Placemark'и, линии, полигоны)
  - `ground_overlay.png` — растровый оверлей (если есть)
  - `ground_overlay_bounds.json` — `{north, south, east, west}` для `LatLngQuad`
- SAF URI остаётся как ссылка на оригинал; кэш — рабочая копия для рендеринга

### Рендеринг в maplibre-compose
- **Вектор**: `rememberGeoJsonSource` + `FillLayer` / `LineLayer` / `SymbolLayer` (уже используются в проекте)
- **GroundOverlay**: `ImageSource` — нужно проверить, есть ли `rememberImageSource` в maplibre-compose 0.12.1.
  Fallback: нативный SDK через `MapEffect { map -> map.style?.addSource(...) }`

### Слои рендеринга оверлеев
- Оверлеи рендерятся **поверх базовой растровой карты**, но **под маркерами узлов**
- Порядок: `base-raster-layer` → `overlay-ground-*` → `overlay-fill-*` → `overlay-line-*` → `overlay-symbol-*` → node layers

### Изменения SQLDelight
- Добавить колонки `geo_json_path TEXT` и `ground_overlay_path TEXT` (nullable, NULL = не распарсено)
- Версия схемы SQLDelight (dev-проект, миграции нет) → bump version в `Database.sq` или пересоздать БД

---

## Scope

**In scope:**
- OSMBonusPack как парсер KML/KMZ (уже исследован)
- Расширение `ImportedMapOverlay.sq` — пути к кэшу
- `KmlOverlayParser` в `app/data/local/map/` — unzip KMZ + parse KML + serialize GeoJSON + extract PNG
- Обновление `ImportedMapRepositoryImpl.import()` — вызывает parser, сохраняет пути
- Presentation model `OverlayRenderModel` в `presentation/feature/main/osd/models/`
- `ObserveSelectedOverlaysUseCase` — фильтр `isSelected == true`, читает GeoJSON из кэша, загружает Bitmap
- `MainUiState` — новое поле `selectedOverlays: ImmutableList<OverlayRenderModel>`
- `MainViewModel` — wires `ObserveSelectedOverlaysUseCase`
- `MapLibreLayer` — рендерит оверлеи (GeoJSON layers + GroundOverlay)

**Out of scope:**
- Поддержка `NetworkLink`, `TimeSpan`, 3D KML-элементов
- Полная KML-стилизация (цвета, иконки Placemark'ов) — базовые стили, доработка позже
- Повторный парсинг уже импортированных файлов (ретроактивно не применяется)

---

## Data Models

### SQLDelight (изменение схемы)
```sql
CREATE TABLE ImportedMapOverlay (
    id                   TEXT    NOT NULL PRIMARY KEY,
    name                 TEXT    NOT NULL,
    uri                  TEXT    NOT NULL,
    created_at           INTEGER NOT NULL,
    is_selected          INTEGER NOT NULL DEFAULT 0,
    geo_json_path        TEXT,          -- NULL = не распарсено / нет вектора
    ground_overlay_path  TEXT           -- NULL = нет GroundOverlay
);
```

Добавить query:
```sql
updateParsedPaths:
UPDATE ImportedMapOverlay
SET geo_json_path = :geoJsonPath, ground_overlay_path = :groundOverlayPath
WHERE id = :id;

selectSelected:
SELECT * FROM ImportedMapOverlay WHERE is_selected = 1;
```

### Domain model `ImportedMapOverlay.kt` — расширить
```kotlin
data class ImportedMapOverlay(
    val id: String,
    val name: String,
    val uri: String,
    val createdAt: Long,
    val isSelected: Boolean,
    val geoJsonPath: String?,        // path in filesDir
    val groundOverlayPath: String?,  // path in filesDir
)
```

### Presentation model (новый файл)
```
presentation/feature/main/osd/models/OverlayRenderModel.kt
```
```kotlin
data class OverlayRenderModel(
    val id: String,
    val geoJson: String?,          // содержимое features.geojson
    val groundOverlayBitmap: Bitmap?,
    val groundOverlayBounds: GroundOverlayBounds?,
)

data class GroundOverlayBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)
```

---

## Phase Plan

### Phase 0 — Research ✅ Done
**Результат:**

1. `rememberImageSource(position: PositionQuad, bitmap: ImageBitmap)` **есть** в maplibre-compose 0.12.1
   — файл `commonMain/org/maplibre/compose/sources/ImageSource.kt`
   — Вариант A (нативный `MapEffect`) не нужен
2. `PositionQuad(topLeft, topRight, bottomRight, bottomLeft)` принимает `org.maplibre.spatialk.geojson.Position`
3. `FillLayer`, `LineLayer` **есть** в `org.maplibre.compose.layers`
4. OSMBonusPack совместим с minSdk 24 (Java 8+, подтверждено research)

### Phase 1 — Dependency + Schema
1. Добавить в `app/build.gradle.kts`:
   ```kotlin
   implementation("org.osmdroid:osmbonuspack:6.9.0")
   ```
2. Изменить `ImportedMapOverlay.sq` — добавить колонки + новые queries
3. Расширить domain model `ImportedMapOverlay.kt`
4. Обновить `ImportedMapRepositoryImpl.toDomain()` — маппинг новых полей
5. Bump SQLDelight schema version (либо `fallbackToDestructiveMigration` в DatabaseFactory)

**Token checkpoint**: `/compact` после фазы

### Phase 2 — KML Parser
**Файл**: `app/src/main/java/ru/tcynik/meshtactics/data/local/map/KmlOverlayParser.kt`

```kotlin
class KmlOverlayParser(private val context: Context) {
    // Возвращает пути к кэшированным файлам (null = тип отсутствует в KML)
    suspend fun parse(id: String, uri: Uri): ParseResult

    data class ParseResult(
        val geoJsonPath: String?,
        val groundOverlayPath: String?,    // path к PNG
        val groundOverlayBoundsPath: String?, // path к JSON с bounds
    )
}
```

**Логика**:
- Определить формат по расширению (`uri.path?.endsWith(".kmz")`)
- KMZ → unzip в temp dir → найти `.kml` внутри
- KML → `KmlDocument().parseGeoJSON(context, kmlFile)` → `saveAsGeoJSON()` → записать в `filesDir/overlays/{id}/features.geojson`
- Найти `GroundOverlay` в `KmlDocument` → взять `mIcon: Bitmap` + `mNorth/mSouth/mEast/mWest`
  → записать PNG в `filesDir/overlays/{id}/ground_overlay.png`
  → записать bounds JSON в `filesDir/overlays/{id}/ground_overlay_bounds.json`

### Phase 3 — Import wiring
В `ImportedMapRepositoryImpl.import()`:
- После `queries.insert(...)` вызвать `KmlOverlayParser.parse(id, parsedUri)`
- Вызвать `queries.updateParsedPaths(geoJsonPath, groundOverlayPath, id)`

### Phase 4 — Use case + Presentation model
1. Создать `ObserveSelectedOverlaysUseCase`:
   - `ImportedMapRepository.observeSelected(): Flow<List<ImportedMapOverlay>>`
   - Для каждого оверлея читает `geoJsonPath` → файл → строку GeoJSON
   - Загружает `groundOverlayPath` → `BitmapFactory.decodeFile()` + читает bounds JSON
   - Эмитит `List<OverlayRenderModel>`
2. Создать `OverlayRenderModel.kt` + `GroundOverlayBounds.kt` в `presentation/feature/main/osd/models/`

### Phase 5 — MainViewModel + MainUiState
1. В `MainUiState` добавить:
   ```kotlin
   val selectedOverlays: ImmutableList<OverlayRenderModel> = persistentListOf()
   ```
2. В `MainViewModel.init {}`:
   ```kotlin
   observeSelectedOverlays(NoParams)
       .onEach { overlays ->
           _uiState.update { it.copy(selectedOverlays = overlays.toImmutableList()) }
       }
       .launchIn(viewModelScope)
   ```
3. Пробросить `selectedOverlays` в `MapLibreLayer` через `MainScreen`

### Phase 6 — MapLibreLayer rendering
Добавить параметр:
```kotlin
selectedOverlays: ImmutableList<OverlayRenderModel> = persistentListOf()
```

Внутри `MaplibreMap { ... }` после базовой растровой карты и перед маркерами узлов:

**GroundOverlay** (если `overlay.groundOverlayBitmap != null`):
```kotlin
val imageSource = rememberImageSource(
    position = PositionQuad(topLeft, topRight, bottomRight, bottomLeft),
    bitmap = overlay.groundOverlayBitmap.asImageBitmap(),
)
RasterLayer(id = "overlay-ground-${overlay.id}", source = imageSource)
```

**GeoJSON вектор** (если `overlay.geoJson != null`):
```kotlin
val geoSource = rememberGeoJsonSource(GeoJsonData.JsonString(overlay.geoJson))
FillLayer(id = "overlay-fill-${overlay.id}", source = geoSource, ...)
LineLayer(id = "overlay-line-${overlay.id}", source = geoSource, ...)
SymbolLayer(id = "overlay-sym-${overlay.id}", source = geoSource, ...)
```

Решение по Варианту A/B принимается по итогам Phase 0.

### Phase 7 — DI wiring
- `app/di/MapDataModule.kt`: bind `KmlOverlayParser`, `ObserveSelectedOverlaysUseCase`
- `app/di/MainModule.kt` (или где живёт `MainViewModel`): inject `ObserveSelectedOverlaysUseCase`

### Phase 8 — Testing
- `ObserveSelectedOverlaysUseCase` — Turbine, mock repository
- `KmlOverlayParser` — instrumental test с реальным KMZ/KML файлом из assets

### Phase 9 — Simplify + Review
- `/simplify` на `MapLibreLayer.kt`, `MainViewModel.kt`, `ImportedMapRepositoryImpl.kt`
- `/architect review` на `app/domain/map/`, `app/data/local/map/`, `MapLibreLayer.kt`

### Phase 10 — CLAUDE.md + commit
- CLAUDE.md: обновить статус фичи KMZ/KML rendering → Done
- Сформировать коммит на русском без `Co-Authored-By`

---

## Coordination Map

```
Phase 0: research maplibre-compose ImageSource API
Phase 1: dependency + schema + domain model
Phase 2: KmlOverlayParser
Phase 3: import wiring (extend ImportedMapRepositoryImpl)
Phase 4: ObserveSelectedOverlaysUseCase + OverlayRenderModel
Phase 5: MainUiState + MainViewModel
Phase 6: MapLibreLayer rendering (Вариант A или B по Phase 0)
Phase 7: DI
Phase 8: /tester
Phase 9: /simplify + /architect review
Phase 10: CLAUDE.md + commit
```

---

## Open Questions

1. ~~**maplibre-compose 0.12.1 ImageSource**~~ ✅ `rememberImageSource(PositionQuad, ImageBitmap)` есть
2. **OSMBonusPack GroundOverlay bounds** — уточнить поля при реализации Phase 2 (вероятно `mNorth`/`mSouth`/`mEast`/`mWest` в `KmlGroundOverlay`)
3. **SQLDelight schema bump** — уточнить в Phase 1 (найти `DatabaseFactory` / версию схемы)

---

## Decisions

1. **Парсинг при импорте, не при рендеринге** — CPU-тяжёлая операция; кэш в `filesDir` обеспечивает быстрый старт карты
2. **Отдельный кэш-каталог** `filesDir/overlays/{id}/` — изоляция по оверлею, легко чистить при `hide`/`delete`
3. **Bounds как JSON-файл** — не расширяем SQLDelight schema лишними полями float; читается вместе с bitmap
4. **Слои рендерятся динамически** по списку selected overlays — нет захардкоженных ID

## Change Log
- 2026-04-13: создан

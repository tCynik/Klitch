# Geo Marks List Screen

**Date**: 2026-05-22 (актуализировано: контекстное меню, фильтры доставки, массовые действия)  
**Plan archive**: `.claude/archive/geo-marks-list.md`  
**Related**: `.claude/docs/geo-marks.md` (создание и отправка меток с карты)

---

## Overview

Полноэкранный список всех гео-меток из SQLDelight (свои и принятые). Открывается из HUD Menu Drawer — пункт **«Метки»** (`ic_marks`) → `Route.GeoMarksList`.

Возможности экрана:

- просмотр меток с TTL, автором и типом доставки;
- фильтрация по состоянию доставки (локально / отправлено / принято);
- показ/скрытие на карте через чекбокс (персистентно в БД);
- массовое включение/выключение видимости и удаление отмеченных чекбоксом меток;
- контекстное меню строки: удалить, продлить TTL, повторно отправить в контур.

Скрытые метки (`is_visible = 0`) не рендерятся на карте в `MapLibreLayer`.

---

## Navigation & DI

| Точка входа | Цепочка |
|---|---|
| Drawer | `MainViewModel` → `HudNavCallbacks.onGeoMarksList` |
| NavGraph | `composable<Route.GeoMarksList>` → `GeoMarksListViewModel` (Koin) → `GeoMarksListScreen` |

**Koin** (`PresentationModule`):

```kotlin
GeoMarksListViewModel(
    observeGeoMarks = get(),
    observeContours = get(),
    toggleVisibility = get(),
    deleteGeoMarks = get(),
    extendGeoMark = get(),
    sendGeoMark = get(),
    logger = get(),
)
```

**Use cases** (`GeoMarkDataModule`): `ObserveGeoMarksUseCase`, `ToggleGeoMarkVisibilityUseCase`, `DeleteGeoMarksUseCase`, `ExtendGeoMarkUseCase`, `SendGeoMarkUseCase`.  
**Channels**: `ObserveContoursUseCase` (`UserSettingsModule`).

---

## File Structure

```
app/
├── domain/marker/
│   ├── repository/GeoMarkRepository.kt
│   │   — observeGeoMarks / toggleVisibility / updateExpiresAt / deleteById / sendGeoMark …
│   └── usecase/
│       ├── ObserveGeoMarksUseCase.kt
│       ├── ToggleGeoMarkVisibilityUseCase.kt
│       ├── DeleteGeoMarksUseCase.kt
│       ├── ExtendGeoMarkUseCase.kt      — EXTEND_TTL_SECONDS = 28_800 (8 ч)
│       └── SendGeoMarkUseCase.kt
├── data/marker/repository/
│   └── GeoMarkRepositoryImpl.kt
└── presentation/feature/marks/
    ├── GeoMarksListScreen.kt
    ├── GeoMarksListViewModel.kt
    ├── GeoMarkListItem.kt
    ├── GeoMarkTtlFormatter.kt
    ├── GeoMarkDeliveryIcon.kt         — иконки LOCAL / SENT / RECEIVED
    ├── GeoMarkDeliveryFilterButton.kt — кнопки фильтра в AppBar
    ├── GeoMarkTypeIcon.kt             — GeoMarkShapeIcon, GeoMarkTrackEndIcon
    ├── GeoMarksDeleteConfirmDialog.kt
    ├── GeoMarksSendContourDialog.kt
    └── models/
        ├── GeoMarksListUiState.kt
        ├── GeoMarkListItemUiModel.kt
        ├── GeoMarkDeliveryState.kt    — + resolveGeoMarkDeliveryState()
        ├── GeoMarkDeliveryFilterStatus.kt
        ├── GeoMarkDeliveryFilterButtonUi.kt
        ├── GeoMarksDeleteConfirmUi.kt
        ├── GeoMarksSendContourPickerUi.kt
        └── GeoMarkContourOptionUi.kt

shared/.../GeoMark.sq
    — is_visible, setVisible, updateExpiresAt, selectAll ORDER BY created_at DESC, …

shared/.../9.sqm
    — ALTER TABLE geo_mark ADD COLUMN is_visible INTEGER NOT NULL DEFAULT 1
```

---

## AppBar (TopAppBar)

| Элемент | Действие |
|---|---|
| ← Назад | `popBackStack` |
| Checkbox / SelectAll | `onToggleAllFilteredVisibility` — для **текущего фильтра**: если все видимы → скрыть все, иначе показать все |
| 3× фильтр доставки | `onDeliveryFilterToggle` — toggle типа LOCAL / SENT / RECEIVED |
| Удалить | `onDeleteClick` — удалить все метки **текущего фильтра** с `isVisible == true` (с подтверждением) |

**Пустые состояния:**

| Условие | Текст |
|---|---|
| `!hasMarks` | «Меток нет» |
| `hasMarks && items.isEmpty()` | «Нет меток по выбранным фильтрам» |

`hasMarks` учитывает все метки в БД; `items` — только прошедшие активные фильтры доставки.

---

## Delivery state & filters

Тип доставки вычисляется в `resolveGeoMarkDeliveryState(isSelf, logicalChannelId, authorNodeId)`:

| `GeoMarkDeliveryState` | Условие | Иконка (строка / фильтр) |
|---|---|---|
| `RECEIVED` | `!isSelf` | `MoveToInbox` — «Принято из сети» |
| `LOCAL` | `isSelf` и пустые `logicalChannelId` + `authorNodeId` | `Save` — «Сохранено в базу» |
| `SENT` | `isSelf`, иначе | `Email` — «Отправлено» |

**Фильтры в AppBar** (`GeoMarkDeliveryFilterButton`):

| `GeoMarkDeliveryFilterStatus` | Поведение кнопки |
|---|---|
| `INACTIVE` | Типа меток нет в списке — disabled, приглушённый |
| `SELECTED` | Тип включён в фильтр — primary + `primaryContainer` фон |
| `UNSELECTED` | Тип есть, но скрыт фильтром — кликабельно |

**Синхронизация фильтров** (`syncVisibleDeliveryFiltersOnMarksChange`):

- при первой загрузке меток включаются все присутствующие типы;
- при появлении нового типа он автоматически добавляется в активные фильтры;
- при исчезновении типа из БД он убирается из `visibleDeliveryFilters`;
- ручной выбор пользователя **не сбрасывается** при обновлении TTL-тикера (только при изменении состава меток из БД).

---

## Row UI

| Элемент | Источник |
|---|---|
| Checkbox | `isVisible` → `ToggleGeoMarkVisibilityUseCase` |
| Иконка 32dp | POINT: `GeoMarkShapeIcon`; TRACK: `GeoMarkTrackEndIcon` |
| Имя | `mark.name` или «—» |
| Бейдж типа | «точка» / «трек» |
| Подзаголовок | иконка доставки + `{createdAtLabel} • {ttlLabel} • {authorLabel}` |
| ⋮ | `DropdownMenu`: Удалить / Продлить / Отправить |

**Автор**: «Я» для `isSelf`, иначе первые 6 символов `authorNodeId`.

**Время создания/получения** (`GeoMarkCreatedAtFormatter`, поле `created_at`):

| Условие | Подпись |
|---|---|
| сегодня | `{ч}:{мм}` — минуты двузначные (локальное время) |
| вчера и ранее | `{месяц}мес.{день}` |

**TTL** (`GeoMarkTtlFormatter`; ViewModel обновляет `nowSeconds` каждые 60 с):

| Условие | Подпись |
|---|---|
| `expiresAt == null` | «—» |
| истёк | «истёк» |
| &lt; 60 с | «&lt;1 мин.» |
| &lt; 1 ч | «N мин.» (ceil) |
| ≥ 1 ч | «Nч» или «Nч Mм» |

Сортировка списка: `created_at DESC`.

---

## Visibility on map

- Колонка `is_visible` в `geo_mark` (default 1), миграция `9.sqm`.
- `ToggleGeoMarkVisibilityUseCase` → `GeoMarkRepository.toggleVisibility`.
- Сохраняется между перезапусками.
- `MapLibreLayer`: `geoMarks.filter { it.isVisible }` для точек и треков.

---

## Delete (подтверждение)

Общий диалог `GeoMarksDeleteConfirmDialog` + `GeoMarksDeleteConfirmUi` (`message`, `markIds`).

| Источник | Кого удаляет | Текст (1 метка) | Текст (N меток) |
|---|---|---|---|
| AppBar «Удалить» | Все **видимые на карте** (`isVisible`) в текущем фильтре | `Удалить метку {name}(от {author})?` | `Удалить выбранные метки(N)?` |
| Меню ⋮ «Удалить» | Только эта метка | то же | — |

Подтверждение → `DeleteGeoMarksUseCase` → `repository.deleteById` для каждого id.

---

## Row context menu (⋮)

| Пункт | Поведение |
|---|---|
| **Удалить** | `onItemDeleteClick` → диалог подтверждения для одной метки |
| **Продлить** | `ExtendGeoMarkUseCase` → `updateExpiresAt(id, now + 28800)`; только локальная БД, без mesh |
| **Отправить** | `onItemSendClick` → `GeoMarksSendContourDialog` |

### Отправить в контур

1. Список целей из `ObserveContoursUseCase`: **активные** контуры + «Хранилище» (`contourId = __local__`).
2. Пользователь выбирает контур → `onSendContourSelected`.
3. `SendGeoMarkUseCase(SendGeoMarkParams(mark, contourId, localOnly))`:
   - `__local__` → `localOnly = true`, `contourId = null` (как в `MainViewModel.sendGeoMarkAtPoints`);
   - иначе `ContourId` + отправка waypoint в mesh и `INSERT OR REPLACE` с `is_self = 1`.

Метка берётся из `cachedMarks` по `markId` (полная `GeoMarkModel`, без пересоздания полей в UI).

---

## GeoMarksListUiState

| Поле | Назначение |
|---|---|
| `items` | Отфильтрованные строки списка |
| `hasMarks` | Есть ли метки в БД (для empty state) |
| `deliveryFilters` | Кнопки фильтра с `GeoMarkDeliveryFilterStatus` |
| `allFilteredVisible` | Все строки фильтра с чекбоксом on |
| `bulkVisibilityEnabled` | Есть строки в фильтре |
| `deleteEnabled` | Хотя бы одна видимая метка в фильтре |
| `deleteConfirm` | Активный диалог удаления |
| `sendContourPicker` | Активный диалог выбора контура |

---

## Out of Scope (follow-up)

- Вкладки / поиск / сортировка по полям
- «Показать на карте» (навигация + центрирование камеры)
- Редактирование полей метки из списка

---

## Tests

| File | Coverage |
|---|---|
| `GeoMarkDeliveryStateTest` | `resolveGeoMarkDeliveryState` |
| `GeoMarkTtlFormatterTest` | TTL edge cases |
| `ToggleGeoMarkVisibilityUseCaseTest` | Repository delegation |
| `GeoMarksListViewModelTest` | Mapping, sort, delivery filters, bulk visibility, toolbar delete, menu delete/extend/send |
| `GeoMarkRepositoryImplTest` | `toggleVisibility`, `updateExpiresAt`, delete, … |

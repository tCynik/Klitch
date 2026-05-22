# Geo Marks List Screen

**Date**: 2026-05-22  
**Plan archive**: `.claude/archive/geo-marks-list.md`

---

## Overview

Full-screen list of all geo marks (self-sent and received). Opened from HUD menu drawer item **«Метки»** (`ic_marks`). Each row shows shape icon, name, type badge, TTL countdown, author, visibility checkbox (persisted in SQLDelight), and a three-dot overflow menu.

Hidden marks (`is_visible = 0`) are excluded from map rendering in `MapLibreLayer`.

---

## File Structure

```
app/
├── domain/marker/
│   ├── model/GeoMarkModel.kt          — isVisible: Boolean = true
│   ├── repository/GeoMarkRepository.kt — toggleVisibility(id, visible)
│   ├── usecase/ExtendGeoMarkUseCase.kt
│   └── usecase/ToggleGeoMarkVisibilityUseCase.kt
├── data/marker/repository/
│   └── GeoMarkRepositoryImpl.kt       — setVisible query; toModel maps is_visible
└── presentation/feature/marks/
    ├── GeoMarksListScreen.kt
    ├── GeoMarkListItem.kt
    ├── GeoMarksListViewModel.kt
    ├── GeoMarkTtlFormatter.kt         — internal TTL label formatting
    ├── GeoMarksDeleteConfirmDialog.kt
    ├── GeoMarksSendContourDialog.kt
    └── models/
        ├── GeoMarksListUiState.kt
        ├── GeoMarkListItemUiModel.kt
        ├── GeoMarksDeleteConfirmUi.kt
        ├── GeoMarksSendContourPickerUi.kt
        └── GeoMarkContourOptionUi.kt

shared/.../GeoMark.sq
    — is_visible INTEGER NOT NULL DEFAULT 1
    — setVisible query
    — updateExpiresAt query
    — selectAll ORDER BY created_at DESC

shared/.../9.sqm
    — ALTER TABLE geo_mark ADD COLUMN is_visible ...
```

Navigation: `Route.GeoMarksList` → `NavGraph` composable; drawer wired via `HudNavCallbacks.onGeoMarksList`.

---

## Row UI

| Element | Source |
|---|---|
| Shape icon (32dp) | `GeoMarkShape` + `GeoMarkColor.colorAt(color)` |
| Name | `mark.name` or «—» if blank |
| Type badge | «точка» / «трек» |
| Subtitle | `{ttlLabel} • {authorLabel}` |
| Checkbox | `isVisible`; toggles `ToggleGeoMarkVisibilityUseCase` |
| ⋮ | Dropdown: **Удалить** / **Продлить** / **Отправить** |

**Author label**: «Я» for `isSelf`, else first 6 chars of `authorNodeId`.

**TTL label** (`GeoMarkTtlFormatter`, ticker every 60s in ViewModel):

| Condition | Label |
|---|---|
| `expiresAt == null` | «—» |
| expired | «истёк» |
| &lt; 60s left | «&lt;1 мин.» |
| &lt; 1h | «N мин.» (ceil) |
| ≥ 1h | «Nч» or «Nч Mм» |

---

## Visibility Persistence

- Column `is_visible` in `geo_mark` (default 1).
- Survives app restart.
- `MapLibreLayer`: `geoMarks.filter { it.isVisible }` for points and tracks.

---

## Row context menu (⋮)

| Action | Behaviour |
|---|---|
| **Удалить** | `GeoMarksDeleteConfirmDialog` for this mark only (same copy as toolbar bulk delete) |
| **Продлить** | `ExtendGeoMarkUseCase` → `expires_at = now + 8h` (28 800 s), local DB only |
| **Отправить** | `GeoMarksSendContourDialog` — active contours + «Хранилище»; `SendGeoMarkUseCase` with chosen contour |

Contour list mirrors `GeoMarksSheet` send target list (`ObserveContoursUseCase`, active only + local storage id `__local__`).

---

## Out of Scope (follow-up)

- Filtering / tabs
- Show on map (navigate + center camera)
- Edit mark fields from list

---

## Tests

| File | Coverage |
|---|---|
| `ToggleGeoMarkVisibilityUseCaseTest` | Repository delegation |
| `GeoMarkTtlFormatterTest` | TTL edge cases |
| `GeoMarksListViewModelTest` | Mapping, sort, visibility, menu delete/extend/send |
| `GeoMarkRepositoryImplTest` | `toggleVisibility`, `updateExpiresAt` round-trip |

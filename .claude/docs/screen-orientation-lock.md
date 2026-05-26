# Screen Orientation Lock

**Date**: 2026-05-26  
**Plan archive**: `.claude/archive/screen-orientation-lock.md`

---

## Overview

Пользователь может закрепить ориентацию приложения (портрет или альбом) на экране **Настройки → Экран**. Чекбокс включает блокировку; при включении появляется выпадающий список. После **Сохранить** настройка пишется в `AppSettings` и сразу применяется к `MainActivity` без перезапуска. При снятии чекбокса восстанавливается авто-поворот (`SCREEN_ORIENTATION_UNSPECIFIED`).

---

## Domain (`shared`)

| Файл | Назначение |
|---|---|
| `domain/settings/model/ScreenOrientationMode.kt` | `SYSTEM`, `PORTRAIT`, `LANDSCAPE` |
| `domain/settings/repository/ScreenOrientationRepository.kt` | get/set + `observeOrientationSettings(): Flow<Pair<Boolean, ScreenOrientationMode>>` |
| `data/settings/AppSettings.kt` | Реализация; ключи `screen_orientation_locked`, `screen_orientation_mode` |

`SYSTEM` в UI не показывается — только в persisted state при первом включении lock подставляется `PORTRAIT`.

---

## Use cases (`app`)

| Use case | Роль |
|---|---|
| `GetScreenOrientationLockedUseCase` | Чтение при инициализации ViewModel |
| `GetScreenOrientationModeUseCase` | Чтение режима |
| `SetScreenOrientationLockedUseCase` | Сохранение из `onSave()` |
| `SetScreenOrientationModeUseCase` | Сохранение из `onSave()` |
| `ObserveScreenOrientationSettingsUseCase` | Flow для `MainActivity` |

DI: `CommonModule` → `ScreenOrientationRepository`; use cases в `MapDataModule`.

---

## Presentation

- `SettingsUiState`: `orientationLocked`, `orientationLockedPending`, `orientationModePending`
- `SettingsViewModel`: `onOrientationLockedChange`, `onOrientationModeChange`, сохранение в `onSave()`
- `DisplaySettingsScreen`: `Checkbox` + `AnimatedVisibility` + `ExposedDropdownMenuBox` (паттерн как в `GeoMarksSheet`)

Строки: `settings_orientation_lock_label`, `settings_orientation_portrait`, `settings_orientation_landscape`.

---

## Apply orientation (Android-only)

`MainActivity.onCreate()` подписывается на `ObserveScreenOrientationSettingsUseCase` в `lifecycleScope`:

```kotlin
requestedOrientation = when {
    !locked -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    mode == PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    mode == LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}
```

Платформенный API остаётся в `app`; domain/data — KMP-чистые.

---

## Tests

`SettingsViewModelTest`:
- `onOrientationLockedChange` → pending + default `PORTRAIT` при `SYSTEM`
- `onSave` → вызов set use cases

---

## Manual smoke

1. Настройки → Экран → включить «Закрепить ориентацию» → Вертикальная → Сохранить → повернуть устройство → экран не поворачивается.
2. Снять чекбокс → Сохранить → авто-поворот снова работает.

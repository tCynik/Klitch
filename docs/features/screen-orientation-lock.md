# Screen Orientation Lock

**Date**: 2026-05-26
**Plan archive**: `docs/archive/screen-orientation-lock.md`

---

## Overview

На экране **Настройки → Экран** — чекбокс «Закрепить ориентацию экрана» + выпадающий список (портрет / альбом). После **Сохранить** настройка персистируется и применяется к Activity немедленно. При снятом чекбоксе — авто-поворот.

**Текущее состояние (MVP)**: ландшафтная ориентация не реализована. UI заморожен: чекбокс всегда нажат, дропдаун disabled, `onSave()` всегда сохраняет `locked=true` + `PORTRAIT`. Разморозить по TODO при реализации ландшафта.

---

## Domain (`shared`)

| Файл | Назначение |
|---|---|
| `domain/settings/model/ScreenOrientationMode.kt` | `PORTRAIT`, `LANDSCAPE` |
| `domain/settings/repository/ScreenOrientationRepository.kt` | get/set locked + mode, `observeOrientationSettings(): Flow<Pair<Boolean, ScreenOrientationMode>>` |
| `data/settings/AppSettings.kt` | Реализация; ключи `screen_orientation_locked` (default `true`), `screen_orientation_mode` (default `PORTRAIT`) |

---

## Use cases (`app`)

| Use case | Роль |
|---|---|
| `GetScreenOrientationLockedUseCase` | Чтение при инициализации ViewModel |
| `GetScreenOrientationModeUseCase` | Чтение режима |
| `SetScreenOrientationLockedUseCase` | Сохранение из `onSave()` |
| `SetScreenOrientationModeUseCase` | Сохранение из `onSave()` |
| `ObserveScreenOrientationSettingsUseCase` | Flow для `MainActivity` (сейчас закомментирован) |

DI: `CommonModule` → `ScreenOrientationRepository`; use cases в `MapDataModule`.

---

## Presentation

- `SettingsUiState`: `orientationLockedPending`, `orientationModePending` (committed `orientationLocked` удалён — было мёртвое поле)
- `SettingsViewModel`: `onOrientationLockedChange`, `onOrientationModeChange`; `onSave()` **хардкодит** `locked=true` + `PORTRAIT`
- `DisplaySettingsScreen`: `Checkbox(enabled=false)` + `AnimatedVisibility` + `ExposedDropdownMenuBox(enabled=false)` с хардкодом `true`/`PORTRAIT`

Строки: `settings_orientation_lock_label`, `settings_orientation_portrait`, `settings_orientation_landscape`.

---

## Apply orientation (Android-only)

**Текущее (хардкод)**: `MainActivity.applyScreenOrientation()` вызывает `requestedOrientation = PORTRAIT` напрямую. Flow-коллектор закомментирован — `combine(StateFlow, StateFlow)` не даёт синхронной первой эмиссии, поэтому `UNSPECIFIED` успевал переопределить манифест до установки PORTRAIT.

**Целевое (после реализации ландшафта)**:
```kotlin
observeScreenOrientationSettings(NoParams)
    .onEach { (locked, mode) ->
        requestedOrientation = when {
            !locked -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            mode == PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            mode == LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    .launchIn(lifecycleScope)
```

Также в манифесте стоит `android:screenOrientation="portrait"` как дополнительный safety-net.

---

## Non-obvious decisions

- **`combine` не синхронный** — два `MutableStateFlow` через `combine` дают `ChannelFlow`; первая эмиссия приходит асинхронно. Это причина, по которой Flow-коллектор в `MainActivity` не работал как хардкод: `UNSPECIFIED` успевал установиться раньше `PORTRAIT`.
- **`requestedOrientation` переопределяет манифест** — вызов `requestedOrientation = UNSPECIFIED` в рантайме всегда побеждает над `android:screenOrientation` в манифесте.
- **`ScreenOrientationMode.SYSTEM` удалён** — изначально был в enum как дефолт "не выбрано", но создавал скрытое третье состояние. Заменён дефолтом `PORTRAIT` в `AppSettings`.

---

## TODO-маркеры (к реализации ландшафта)

| Файл | TODO |
|---|---|
| `AndroidManifest.xml` | Удалить `android:screenOrientation="portrait"` |
| `MainActivity.kt` | Раскомментировать Flow-коллектор, удалить прямой вызов |
| `AppSettings.kt` | Вернуть default `getOrientationLocked()` → `false` |
| `DisplaySettingsScreen.kt` | Восстановить `state.orientationLockedPending` / `state.orientationModePending`, убрать `enabled=false` |
| `SettingsViewModel.kt` | Восстановить `setScreenOrientationLocked(state.orientationLockedPending)` в `onSave()` |

---

## Tests

`SettingsViewModelTest`:
- `onOrientationLockedChange(true)` → `orientationLockedPending = true`
- `onSave()` → всегда вызывает `setScreenOrientationLocked(true)` + `setScreenOrientationMode(PORTRAIT)`

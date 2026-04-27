# Plan: User Settings Write Mechanics

**Date**: 2026-04-27
**Status**: Approved

## Summary

GPS broadcast bug fix, GPS toggle UI (immediate write), leave-screen save dialog (deferred+reboot for callsign), docs pattern for new settings.

## Scope

**In scope:**
- Bug fix: `disableNodePositionBroadcast()` — добавить `position_broadcast_smart_enabled = false`
- `GpsBroadcastSettingsRepository` — локальный preferencе (Settings-backed)
- GPS broadcast toggle в UI — немедленная запись, без диалога
- Leave-screen dialog — "Сохранить изменения? Нода будет перезагружена."
- Callsign: при подтверждении выхода (connected) → `writeOwner()` + `rebootNode()`; при выходе disconnected → local-only
- Убрать кнопку «Сохранить» из поля displayName
- Docs: таблица write mechanics для настроек экрана

**Out of scope:**
- Другие вкладки Settings (Map, Screen) — не трогать
- Новые настройки ноды (LoRa, регион и т.п.) — будут добавляться по этой же механике

## Write Mechanics Rule

| Настройка | Применение | Ребут | Диалог при выходе |
|---|---|---|---|
| GPS broadcast toggle | Немедленно на toggle | Нет | Нет |
| Позывной (connected) | При подтверждении выхода | Да | Да |
| Позывной (disconnected) | Локально при выходе | Нет | Нет |

**Правило для любой новой настройки на этом экране**: при добавлении указывать в docs строку «Write mechanics: immediate / deferred+reboot / local-only».

## Architecture Notes

### New domain additions

| File | Role |
|---|---|
| `domain/mesh/repository/GpsBroadcastSettingsRepository.kt` | `val enabled: Flow<Boolean>`, `fun set(value: Boolean)` |
| `domain/mesh/usecase/ObserveGpsBroadcastEnabledUseCase.kt` | wraps repo |
| `domain/mesh/usecase/SetGpsBroadcastEnabledUseCase.kt` | wraps repo |

### New data additions

| File | Role |
|---|---|
| `data/mesh/repository/GpsBroadcastSettingsRepositoryImpl.kt` | Settings-backed, default = `true` |

### Bug fix

`MeshConfigRepositoryImpl.disableNodePositionBroadcast()`:
```kotlin
val updated = current.copy(
    position_broadcast_secs = GEO_BROADCAST_DISABLED_SECS,
    position_broadcast_smart_enabled = false,   // добавить
)
```

### UserSettingsUiState additions

```kotlin
val isGpsBroadcastEnabled: Boolean = true
val showLeaveDialog: Boolean = false
```

`hasUnsavedUserChanges` (уже есть) — переиспользуем как маркер «pending reboot changes» когда `isNodeConnected = true`.

### UserSettingsViewModel changes

**Inject добавить:** `ObserveGpsBroadcastEnabledUseCase`, `SetGpsBroadcastEnabledUseCase`, `ObserveDeviceConfigUseCase`, `WriteOwnerUseCase`

**`onConnected()` — новая логика:**
```kotlin
private fun onConnected(contours: List<Contour>) {
    viewModelScope.launch {
        val emergencyActive = contours.find { it.isEmergency }?.isActive ?: false
        val broadcastEnabled = gpsBroadcastEnabled.first()
        if (emergencyActive || !broadcastEnabled) disableNodePositionBroadcast()
        else enableNodePositionBroadcastReady()
    }
}
```

**Новый метод `onGpsBroadcastToggle(enabled: Boolean)`:**
```kotlin
fun onGpsBroadcastToggle(enabled: Boolean) {
    viewModelScope.launch {
        setGpsBroadcastEnabled(enabled)
        if (connectionStatus is MeshConnectionStatus.Connected) {
            if (enabled) enableNodePositionBroadcastReady()
            else disableNodePositionBroadcast()
        }
    }
}
```

**`onNavigateBackRequested()`:**
```kotlin
fun onNavigateBackRequested() {
    if (uiState.value.hasUnsavedUserChanges && uiState.value.isNodeConnected) {
        _uiState.update { it.copy(showLeaveDialog = true) }
    } else {
        if (uiState.value.hasUnsavedUserChanges) {
            viewModelScope.launch { saveAppUser(AppUser(displayName = uiState.value.displayName)) }
        }
        _navigateBack.tryEmit(Unit)
    }
}
```

**`onSaveAndReboot()` — "Сохранить":**
```kotlin
fun onSaveAndReboot() {
    _uiState.update { it.copy(showLeaveDialog = false) }
    viewModelScope.launch {
        val shortName = withTimeoutOrNull(5_000) {
            observeDeviceConfig(NoParams).first { it != null }
        }?.shortName ?: ""
        writeOwner(uiState.value.displayName, shortName)
        saveAppUser(AppUser(displayName = uiState.value.displayName))
        _uiState.update { it.copy(hasUnsavedUserChanges = false) }
        rebootNode()
        _navigateBack.tryEmit(Unit)
    }
}
```

**`onDiscardAndLeave()` — "Сбросить":**
```kotlin
fun onDiscardAndLeave() {
    _uiState.update { it.copy(showLeaveDialog = false, hasUnsavedUserChanges = false) }
    viewModelScope.launch {
        val saved = observeAppUser(NoParams).first()
        _uiState.update { it.copy(displayName = saved.displayName) }
        _navigateBack.tryEmit(Unit)
    }
}
```

**`onDismissLeaveDialog()` — backdrop / system back = остаёмся на экране:**
```kotlin
fun onDismissLeaveDialog() {
    _uiState.update { it.copy(showLeaveDialog = false) }
}
```

**Navigation event (добавить в VM):**
```kotlin
private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
val navigateBack: SharedFlow<Unit> = _navigateBack.asSharedFlow()
```

**Убрать `onSaveUser()`** — заменено leave-dialog flow.

### SettingsScreen changes

```kotlin
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
    userViewModel: UserSettingsViewModel = koinViewModel(),  // добавить
) {
    val userState by userViewModel.uiState.collectAsStateWithLifecycle()

    // перехват навигационного события
    LaunchedEffect(Unit) {
        userViewModel.navigateBack.collect { onNavigateBack() }
    }

    // leave dialog
    if (userState.showLeaveDialog) {
        LeaveSettingsDialog(
            onConfirm = userViewModel::onSaveAndReboot,
            onDiscard = userViewModel::onDiscardAndLeave,
            onDismiss = userViewModel::onDismissLeaveDialog,
        )
    }

    // BackHandler
    BackHandler(enabled = userState.hasUnsavedUserChanges) {
        userViewModel.onNavigateBackRequested()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    // вместо прямого onNavigateBack
                    IconButton(onClick = { userViewModel.onNavigateBackRequested() }) { ... }
                },
                ...
            )
        },
        ...
    )
}
```

### UserTabContent changes

- Убрать кнопку «Сохранить» (строки 178–186)
- Добавить секцию «Радио» с Switch для GPS broadcast:
  ```kotlin
  item {
      Text("Радио", style = ...) // секция-заголовок
  }
  item {
      Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Транслировать геопозицию", modifier = Modifier.weight(1f))
          Switch(
              checked = state.isGpsBroadcastEnabled,
              onCheckedChange = viewModel::onGpsBroadcastToggle,
          )
      }
  }
  ```

### New composable `LeaveSettingsDialog`

```kotlin
@Composable
fun LeaveSettingsDialog(
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leave_settings_dialog_title)) },
        text = { Text(stringResource(R.string.leave_settings_dialog_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.leave_settings_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.leave_settings_dialog_discard))
            }
        },
    )
}
```

Strings:
- `leave_settings_dialog_title`: "Сохранить изменения?"
- `leave_settings_dialog_message`: "Нода будет перезагружена."
- `leave_settings_dialog_save`: "Сохранить"
- `leave_settings_dialog_discard`: "Сбросить"

## Phase Plan

### Phase 1 — Bug fix + GPS broadcast preference

**Order:** domain → data → DI → bug fix → VM

1. `GpsBroadcastSettingsRepository.kt` (domain interface)
2. `ObserveGpsBroadcastEnabledUseCase.kt`, `SetGpsBroadcastEnabledUseCase.kt`
3. `GpsBroadcastSettingsRepositoryImpl.kt` (Settings-backed, default=true)
4. DI: bind в `UserSettingsModule`
5. Bug fix: `MeshConfigRepositoryImpl.disableNodePositionBroadcast()` — `smart_enabled = false`
6. `UserSettingsViewModel.onConnected()` — учитывать preference

### Phase 2 — GPS broadcast toggle UI

1. `UserSettingsUiState`: добавить `isGpsBroadcastEnabled`
2. `UserSettingsViewModel`: observe pref, добавить `onGpsBroadcastToggle()`
3. `UserTabContent`: секция «Радио» со Switch

### Phase 3 — Leave-screen dialog + callsign deferred write

**Order:** UiState → VM → SettingsScreen → UserTabContent → новый composable → strings

1. `UserSettingsUiState`: добавить `showLeaveDialog`
2. `UserSettingsViewModel`: inject новые use cases, добавить `navigateBack` SharedFlow, новые методы, убрать `onSaveUser()`
3. `SettingsScreen`: inject `UserSettingsViewModel`, `LaunchedEffect`, `BackHandler`, `LeaveSettingsDialog`, TopAppBar back кнопка
4. `UserTabContent`: убрать кнопку «Сохранить»
5. `LeaveSettingsDialog.kt` — новый composable в `ui/components/`
6. `strings.xml` — 4 новые строки

**Skill**: direct coding → `/simplify` на изменённых файлах

### Phase 4 — Docs

1. Создать `.claude/docs/user-settings-write-mechanics.md`
   - Таблица write mechanics
   - Leave-dialog flow
   - Правило «Write mechanics: X» для новых настроек
2. Обновить `CLAUDE.md`: добавить строку в таблицу документации

### Phase 5 — Tests

- `UserSettingsViewModel`: `onNavigateBackRequested()` connected+unsaved → `showLeaveDialog=true`
- `onSaveAndReboot()` → `writeOwner` + `rebootNode` вызваны → `navigateBack` emit
- `onDiscardAndLeave()` → displayName сброшен → `navigateBack` emit
- `onGpsBroadcastToggle(false)` + connected → `disableNodePositionBroadcast()` вызван

### Phase 6 — Skill Update + Commit

- `/ui-designer`: `LeaveSettingsDialog` → Component Library
- Commit в Russian, imperative mood

## Coordination Map

```
Phase 1: bug fix + pref infrastructure (domain→data→DI→VM)
Phase 2: GPS toggle UI
Phase 3: leave-screen dialog (UiState→VM→SettingsScreen→UserTabContent→composable→strings)
Phase 4: docs
Phase 5: tests
Phase 6: skill update → /simplify → commit
```

## Open Questions

1. **`ObserveDeviceConfigUseCase` уже инжектирован в `UserSettingsViewModel`?**
   Нет — добавить в конструктор и DI. Нужен для получения `shortName` при записи позывного.

2. **Секция «Радио» в UserTabContent**: GPS broadcast toggle относится к Radio, но экран называется «Пользователь». Если планируется расширение радионастроек — возможно, вынести в отдельную вкладку Settings позже.

3. **Emergency override**: при активном SOS → `isGpsBroadcastEnabled = true` в UI (toggle показывает true), но нода не транслирует (override в `onConnected`). Нужно ли визуально показывать override? Deferred — определить при реализации Phase 2.

## Change Log

- 2026-04-27: создан

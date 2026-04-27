# User Settings Write Mechanics

## Write Mechanics Table

| Настройка | Применение | Ребут | Диалог при выходе |
|---|---|---|---|
| GPS broadcast toggle | Немедленно на toggle | Нет | Нет |
| Позывной (connected) | При подтверждении выхода | Да | Да |
| Позывной (disconnected) | Локально при выходе | Нет | Нет |

**Правило для любой новой настройки на этом экране**: при добавлении указывать в docs строку «Write mechanics: immediate / deferred+reboot / local-only».

## Leave-Dialog Flow

```
User нажимает назад / стрелку
    ├─ hasUnsavedUserChanges=false → немедленный выход
    ├─ hasUnsavedUserChanges=true && isNodeConnected=false → save local → выход
    └─ hasUnsavedUserChanges=true && isNodeConnected=true → LeaveSettingsDialog
            ├─ "Сохранить" → writeOwner + saveAppUser + rebootNode → выход
            └─ "Сбросить" → restore displayName из AppUserRepository → выход
```

`BackHandler` перехватывает системную кнопку назад при `hasUnsavedUserChanges=true`.
`userViewModel.navigateBack` SharedFlow — единственная точка реального перехода назад.

## GPS Broadcast Override

При подключении (`onConnected`):
- Emergency active **или** GPS broadcast выключен → `disableNodePositionBroadcast()`
- Иначе → `enableNodePositionBroadcastReady()`

Toggle в UI меняет `GpsBroadcastSettingsRepository` (DataStore) и немедленно пишет в ноду если она подключена.

## Bug Fix

`MeshConfigRepositoryImpl.disableNodePositionBroadcast()` теперь дополнительно выставляет `position_broadcast_smart_enabled = false`, чтобы нода не транслировала геопозицию через smart-broadcast.

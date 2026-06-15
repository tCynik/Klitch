# Позывной (Callsign)

## Роль в системе

Позывной — единственный публичный идентификатор пользователя в радиосети.  
Без него пользователь анонимен для других узлов сети.

Отсутствие позывного не блокирует офлайн-функции (карта, локальные метки), но делает невозможным любое взаимодействие через радиоканал: подключение к ноде, отправку сообщений, трансляцию позиции, SOS.

## Модель

```
AppUser.displayName: String           (DataStore — хранилище приложения)
    ↕ синхронизация при подключении
Meshtastic User.longName: String      (хранится на ноде)
```

- `DISPLAY_NAME_MAX_LENGTH = 39` — ограничение протокола Meshtastic
- Пустая строка считается «позывного нет»

## Жизненный цикл

```
1. Ввод пользователем (UserSettings или CallsignGateDialog)
2. Сохранение в DataStore → AppUser.displayName
3. Синхронизация с нодой: writeOwner() → Meshtastic User.longName
4. Нода перезагружается (rebootNode) — longName вступает в силу
```

Шаги 3–4 выполняются:
- при первом подключении (если расхождение обнаружил `CheckNodeSyncUseCase`)
- при подтверждении sync dialog
- при сохранении настроек пользователя (connected path)

## Инварианты

| Инвариант | Обеспечивается |
|---|---|
| Пустой позывной → нельзя подключиться | Gate в `MeshTestViewModel` / `MainViewModel` |
| Пустой позывной → нельзя сохранить настройки | Guard в `UserSettingsViewModel.onSaveAndReboot()` |
| Расхождение app ↔ нода → sync перед работой | `CheckNodeSyncUseCase` → sync dialog |
| Отказ от sync → отключение от ноды | `disconnectFromMesh` при dismiss sync dialog |
| Изменение в эфире → reboot ноды | `writeOwner` + `rebootNode` в любом пути записи |

## Отображение

| Место | Что показывается |
|---|---|
| HUD-бар (статус соединения) | `longName` ноды (после синхронизации совпадает с позывным) |
| Чат (имя отправителя) | `displayName` из `ChatContact` (маппинг из `User.longName`) |
| Маркеры нод на карте | `NodeMarkerModel.displayName` |
| UserSettings | Редактируемое поле с счётчиком символов (max 39) |

## Gate-политика (блокировка без позывного)

Поведение по сценариям и архитектурная реализация — в [callsign-gate-on-connect.md](callsign-gate-on-connect.md).

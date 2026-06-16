# GPS Pipeline Debug — наработки

**Ветка:** `contours_remake_may`  
**Статус:** Не завершён. Ноды всё ещё не видят друг друга на карте.

---

## Симптомы

- Прямые сообщения между нодами проходят → PSK совпадает, BLE работает
- Позиции нод не появляются на карте ни в MeshTactics, ни (видимо) в стандартном Meshtastic
- Тоггл "Provide location to mesh" в оригинальном Meshtastic-приложении **остаётся выключен** после переподключения через MeshTactics

---

## Что было исправлено (этот и предыдущий сеанс)

### Сессия 1 (предыдущая)

| Файл | Проблема | Фикс |
|---|---|---|
| `MeshConfigRepositoryImpl` | `?: Config.PositionConfig()` → писал `gps_mode=0 (DISABLED)` на старте | Заменено на `?: return` |
| `MeshConfigRepositoryImpl` | `writeChannelPositionPrecision(0, 13)` вызывался когда `channelSetFlow` пустой → перезаписывал канал 0 с пустым именем/PSK → коррупция хэша → бесконечный NeedsSync | Убрать вызов из `enableNodePositionBroadcastReady()` |
| `UserSettingsViewModel` | `onConnected()` ждал `observeDeviceConfig != null`, но это не гарантировало загрузку `localConfig.position` | Добавлен `withTimeoutOrNull(5_000)` (позже удалён, логика перенесена в use case) |
| `CheckNodeSyncUseCase` | Проверял slot 0 по Emergency-хэшу → `InSync` при старом порядке LongFast=0, Basic=1 | Переписан: slot 0 = Primary hash, slot 1 = Emergency hash |

### Сессия 2 (текущая)

| Файл | Проблема | Фикс |
|---|---|---|
| `MeshConfigRepositoryImpl.enableNodePositionBroadcastReady()` | `?: return` — `localConfig.position` null в момент вызова (LocalConfig-пакеты приходят позже NodeInfo) → запись не происходила | Функция стала `suspend`, внутри ждёт `localConfig.first { it.position != null }` с таймаутом 15 с |
| `MeshConfigRepositoryImpl.disableNodePositionBroadcast()` | Аналогично | Аналогично |
| `EnableNodePositionBroadcastReadyUseCase` / `DisableNodePositionBroadcastUseCase` | Вызывались без корутины (теперь suspend) | `suspend operator fun invoke()` |
| **Новый** `GpsBroadcastCoordinator` | `enableNodePositionBroadcastReady()` вызывался только из `UserSettingsViewModel` — не активен при авто-коннекте с экрана карты | `createdAtStart=true` синглтон в `UserSettingsModule`, подписывается на `observeConnectionStatus` и вызывает enable/disable при каждом коннекте |
| `NetworkViewModel` | Sync check (`checkContourSync()`) вызывался немедленно на коннекте, до прихода каналов → `channelSetFlow` пустой → `InSync` → sync dialog не показывался | Добавлен `withTimeoutOrNull(10_000) { observeNodeChannels.filter { isNotEmpty() }.firstOrNull() }` перед проверкой |
| `MainViewModel` | Аналогично + `syncStateRepository.clear()` сбрасывал HUD-флаг преждевременно | Аналогично |

---

## Оставшаяся проблема: "Provide location to mesh" остаётся OFF

### Что это значит технически

`shouldProvideNodeLocation` в `UiPrefsImpl` хранится в **нашем** DataStore (`UiDataStore`), дефолт `true`. Это не то же самое, что `gps_mode` на ноде.

В Meshtastic-приложении "Provide location to mesh" = отдельный DataStore этого приложения. Два приложения не делят DataStore → расхождение **ожидаемо**.

**НО**: ключевой вопрос — что Meshtastic-приложение **пишет на ноду** при выключении этого тоггла?

### Гипотеза 1 (наиболее вероятная): `gps_mode = DISABLED` записан на ноду Meshtastic-приложением

Если пользователь ранее открывал Meshtastic-приложение и там тоггл был OFF, Meshtastic мог отправить на ноду `gps_mode = DISABLED` (или `NOT_PRESENT`). Нода запомнила это в flash.

После подключения через MeshTactics:
- `enableNodePositionBroadcastReady()` пишет `position_broadcast_secs = 60` ✓
- Но `gps_mode` остаётся `DISABLED` с предыдущей сессии Meshtastic ✓✗
- Нода не может получить GPS → нечего транслировать → позиций нет

**Как проверить**: Открыть LocationSettings в MeshTactics → посмотреть значение `gpsMode` в `LocationConfigModel`. Если `DISABLED` или `NOT_PRESENT` — гипотеза подтверждена.

### Гипотеза 2: Нода не имеет встроенного GPS

Если устройство не имеет GPS-чипа (напр. RAK4631 без GPS модуля), `gps_mode = NOT_PRESENT`. Нода не может самостоятельно транслировать позицию. Позицию должен отправлять телефон через `setFixedPosition`.

В этом случае:
- `enableNodePositionBroadcastReady()` пишет broadcast interval → но нода не знает своих координат → всё равно ничего не транслирует
- Нужно `shouldProvideNodeLocation = true` (уже true по дефолту у нас) И GpsService активен

**Как проверить**: `gpsMode == NOT_PRESENT` в LocationConfig.

### Гипотеза 3: Каналы всё ещё не синхронизированы

Sync dialog мог не показаться (NetworkViewModel не был активен при коннекте, или пользователь закрыл без подтверждения). Канал 0 имеет `position_precision = 0` → позиции не транслируются.

**Как проверить**: В настройках → вкладка пользователя → посмотреть статус sync нод. Если иконка показывает "не на ноде" → sync не выполнен.

### Гипотеза 4: `position_broadcast_secs` всё ещё не пишется

`GpsBroadcastCoordinator` запускается при инициализации Koin. Если Koin инициализируется раньше чем `UserSettingsModule` поднимается... маловероятно, но возможно.

**Как проверить**: Логи тег `MT/GpsBroadcast` (если добавить логирование в координатор).

---

## Рекомендуемые следующие шаги

### 1. Диагностика (приоритет)

Добавить в `GpsBroadcastCoordinator.applyGpsBroadcastSettings()` логирование текущего состояния:

```kotlin
private suspend fun applyGpsBroadcastSettings() {
    val sosActive = observeEmergencyMode().first()
    val broadcastEnabled = observeGpsBroadcastEnabled().first()
    logger.d("GpsBroadcast", "onConnected: sos=$sosActive broadcast=$broadcastEnabled")
    if (sosActive || !broadcastEnabled) {
        disableNodePositionBroadcast()
        logger.d("GpsBroadcast", "→ disabled")
    } else {
        enableNodePositionBroadcastReady()
        logger.d("GpsBroadcast", "→ enabled")
    }
}
```

Добавить в `enableNodePositionBroadcastReady()`:

```kotlin
logger.d("GpsBroadcast", "position config: gps_mode=${current.gps_mode}, broadcast_secs=${current.position_broadcast_secs}")
// после записи:
logger.d("GpsBroadcast", "wrote broadcast_secs=$GEO_BROADCAST_READY_SECS")
```

### 2. Если `gps_mode = DISABLED` подтвердится

Добавить в `enableNodePositionBroadcastReady()` сброс `gps_mode` до `ENABLED` если он `DISABLED`:

```kotlin
val updated = current.copy(
    position_broadcast_secs = GEO_BROADCAST_READY_SECS,
    position_broadcast_smart_enabled = false,
    gps_mode = if (current.gps_mode == Config.PositionConfig.GpsMode.DISABLED)
        Config.PositionConfig.GpsMode.ENABLED
    else
        current.gps_mode,
)
```

**Осторожно**: нельзя ставить `ENABLED` если `NOT_PRESENT` (нет GPS-чипа). Условие: только если `DISABLED`.

### 3. Если нода без GPS (`NOT_PRESENT`)

Нужно чтобы `GpsService` слал позицию телефона на ноду регулярно. Проверить:
- `GpsService` активен в фоне
- `commandSender.setFixedPosition()` вызывается при каждом GPS-обновлении

Смотреть: `data/gps/` или `domain/gps/` — как и куда GPS телефона отправляется на ноду.

### 4. Если sync не выполнен

Убедиться что sync dialog показывается после открытия экрана "Сеть". После подтверждения → ребут ноды → переподключение.

---

## Ключевые файлы

| Файл | Роль |
|---|---|
| `data/mesh/GpsBroadcastCoordinator.kt` | **NEW** Синглтон, пишет GPS broadcast config при каждом коннекте |
| `data/mesh/repository/MeshConfigRepositoryImpl.kt` | `enableNodePositionBroadcastReady()` / `disableNodePositionBroadcast()` — suspend, ждут `localConfig.position` |
| `domain/mesh/repository/MeshConfigRepository.kt` | Интерфейс — оба метода теперь `suspend fun` |
| `domain/mesh/usecase/EnableNodePositionBroadcastReadyUseCase.kt` | `suspend operator fun invoke()` |
| `domain/mesh/usecase/DisableNodePositionBroadcastUseCase.kt` | `suspend operator fun invoke()` |
| `presentation/feature/settings/UserSettingsViewModel.kt` | `onConnected()` без ручного wait (перенесено в use case) |
| `presentation/feature/network/NetworkViewModel.kt` | Ждёт каналы перед sync check; добавлен `observeNodeChannels` параметр |
| `presentation/feature/main/MainViewModel.kt` | Ждёт каналы перед sync check |
| `di/UserSettingsModule.kt` | Регистрация `GpsBroadcastCoordinator` |

---

## Контекст: что НЕ является проблемой

- **Расхождение тоглов GPS** между MeshTactics и Meshtastic-приложением — ожидаемо, разные DataStore
- **`shouldProvideNodeLocation`** в `UiPrefsImpl` — дефолт `true`, не причина проблемы
- **`position_precision = 13`** в константе `GEO_CHANNEL_PRECISION` — не используется (убрана запись precision из `enableNodePositionBroadcastReady()`)

---

## Версия документа

Создан по итогам отладки на ветке `contours_remake_may`. Коммиты с фиксами: проверить `git log --oneline -10`.

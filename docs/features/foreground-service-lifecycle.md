# Feature: Foreground Service Lifecycle

> **Внутренности сервиса (GPS pipeline, классы, подписки)** — см. [`gps-background-service.md`](gps-background-service.md).  
> Этот документ описывает только **когда** сервис стартует и стопает.

## Назначение

`GpsService` запускается только при реальной потребности и останавливается когда потребность отпала. До этой фичи сервис стартовал безусловно при получении разрешений.

---

## Условия жизненного цикла

**Старт** (любое из):
- Событие `nodeConnected` — BLE-соединение с нодой установлено
- `trackRecordingActive = true` — запущена запись трека

**Стоп** (оба условия вместе):
- `trackRecordingActive = false`
- Тоггл «Сеть» выключен

**Частные случаи**:
- Нода отвалилась сама / disconnect → сервис продолжает жить (`networkServiceActive` остаётся `true`)
- Тоггл «Сеть» ON при старте без подключения → сервис НЕ стартует (нет события `nodeConnected`)
- Тоггл «Сеть» OFF → ON → сервис стартует только при следующем `nodeConnected`

---

## Модель состояния

```
serviceRunning = networkServiceActive || trackRecordingActive

networkServiceActive: Boolean
  default = false
  → true  : событие nodeConnected
  → false : тоггл «Сеть» выключен
```

`networkServiceActive` ≠ `networkEnabledFlow` (тоггл). Тоггл ON без подключения сервис не запускает.

Реактивный pipeline в `GpsServiceControllerImpl`:

```kotlin
combine(
    _networkServiceActive,
    trackRecordingRepository.state.map { it is TrackRecordingState.Recording },
) { net, track -> net || track }
    .distinctUntilChanged()
```

---

## Архитектура

### `GpsServiceController` — интерфейс

```
domain/service/GpsServiceController.kt
```

```kotlin
interface GpsServiceController {
    val shouldRunService: Flow<Boolean>
    fun onNodeConnected()
    fun onNetworkDisabled()
}
```

### `GpsServiceControllerImpl` — реализация

```
data/service/GpsServiceControllerImpl.kt
```

- Koin `single(createdAtStart = true)` в `GpsModule`
- Хранит `_networkServiceActive: MutableStateFlow<Boolean>` (in-memory, не персистируется)
- Инжектирует `TrackRecordingRepository` для получения состояния записи трека

### Триггеры в `ConnectionViewModel`

При первом `Connected` (не re-connect в рамках той же сессии):

```kotlin
if (!wasConnected) {
    gpsServiceController.onNodeConnected()
    ...
}
```

При отключении тоггла «Сеть»:

```kotlin
} else if (!enabled && wasEnabled) {
    gpsServiceController.onNetworkDisabled()
    ...
}
```

### Наблюдатель в `BlePermissionGuard`

```kotlin
val gpsServiceController = koinInject<GpsServiceController>()
LaunchedEffect(granted) {
    if (!granted) return@LaunchedEffect
    gpsServiceController.shouldRunService.collect { shouldRun ->
        if (shouldRun) context.startForegroundService(GpsService.createIntent(context))
        else context.stopService(GpsService.createIntent(context))
    }
}
```

`startForegroundService` вызывается из `LaunchedEffect` → Activity в foreground → Android 14+ ограничение соблюдено.

### Guard в `GpsService`

Защита от повторной инициализации при двойном `startForegroundService`:

```kotlin
private var isRunning = false

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!isRunning) {
        isRunning = true
        gpsLifecycle.start()
        startTrackRecordingObserver()
        startRecordingNotificationObserver()
    }
    return START_NOT_STICKY
}
```

---

## Затронутые файлы

| Файл | Изменение |
|---|---|
| `domain/service/GpsServiceController.kt` | Новый |
| `data/service/GpsServiceControllerImpl.kt` | Новый |
| `di/GpsModule.kt` | Регистрация `GpsServiceControllerImpl` |
| `presentation/feature/main/ConnectionViewModel.kt` | Параметр + вызовы триггеров |
| `di/PresentationModule.kt` | `gpsServiceController = get()` |
| `navigation/BlePermissionGuard.kt` | Реактивный lifecycle вместо безусловного старта |
| `service/GpsService.kt` | `isRunning` guard |

---

## Что не меняется

- Внутренняя логика `GpsService` (track observer, notification observer, `gpsLifecycle`)
- Exit flow: `TrackRecordingViewModel.requestExitIfSafe()` + `NavGraph.exitAppEvent`
- `onTaskRemoved → stopSelf()` в `GpsService`
- `BackgroundPositionSession` и `GpsLifecycleController`

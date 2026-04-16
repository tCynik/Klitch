# Plan: Auto-connect on App Start

**Date**: 2026-04-16
**Status**: Approved

---

## Summary

При запуске приложения (после получения BLE-разрешений) автоматически стартует сканирование BLE-нод.
Если найдена последняя использовавшаяся нода — подключаемся к ней автоматически.
Если найдены другие ноды (но не последняя) — HUD показывает «выбрать ноду» (жёлтый).
Если ноды не найдены — сканирование перезапускается, HUD показывает «Поиск…» (красный).
Если сканирование не активно (будущая фича: остановлено пользователем) — просто красная кнопка Радио, info пустой.

---

## Разрешения

`BlePermissionGuard` в `NavGraph` уже обрабатывает все BLE/Location разрешения **до** рендера любого контента.
`MainScreen` (и, следовательно, `MainViewModel`) создаются только после получения разрешений → дополнительного кода запроса разрешений **не требуется**.

---

## Поведение по состояниям HUD

| Состояние | Цвет кнопки Радио | Info-слот |
|---|---|---|
| Scan активен, нод не найдено | Red | «Поиск…» |
| Scan активен, найдены другие ноды (не последняя) | Yellow | «выбрать ноду» |
| Connecting | Yellow | «Сопряжение с $deviceName» |
| Connected (2 с после подключения) | Green | «Сопряжено с $shortName» |
| Connected (после автоскрытия) | Green | — |
| Disconnected / scan не активен | Red | — |

---

## Архитектура

### Ключевые решения

**1. `MeshConnectionStatus.Scanning` теперь реально эмитируется**
`MeshConnectionRepositoryImpl` добавляет `_isScanning: MutableStateFlow<Boolean>`.
`connectionStatus` flow расширяется до `combine(…, _isScanning)`:
если `_isScanning == true` && `serviceState == Disconnected` → эмитируем `MeshConnectionStatus.Scanning`.
`scanDevices()` устанавливает `_isScanning = true` в начале и `false` в `finally`.

**2. `foundOtherDevicesDuringScan` в `MainUiState`**
Presentation-слой отслеживает факт «во время скана найдены ноды, но не последняя».
Управляется `MainViewModel` в auto-connect корутине.

**3. Последняя нода хранится в `Settings` (multiplatform-settings)**
Тот же механизм, что у `LastMapPositionRepositoryImpl`.
Ключи: `last_ble_address`, `last_ble_name`.

**4. Auto-connect flow в `MainViewModel`**
Запускается из `init{}` через `startAutoConnect()`.
Отменяется из `observeConnectionStatus` при переходе в `Connecting` / `Connected`.
После успешного подключения сохраняет устройство как «последнее».
Если скан завершился естественным образом (30 с timeout) и нод не найдено — перезапускает скан.

---

## Затрагиваемые файлы

### Новые файлы

| Файл | Назначение |
|---|---|
| `domain/mesh/repository/LastConnectedDeviceRepository.kt` | Интерфейс хранилища последней ноды |
| `domain/mesh/usecase/GetLastConnectedDeviceUseCase.kt` | Синхронный get |
| `domain/mesh/usecase/SaveLastConnectedDeviceUseCase.kt` | Suspend save |
| `data/local/mesh/LastConnectedDeviceRepositoryImpl.kt` | Реализация на `Settings` |

### Изменяемые файлы

| Файл | Изменение |
|---|---|
| `data/mesh/repository/MeshConnectionRepositoryImpl.kt` | `_isScanning` + обновить `connectionStatus` combine + обернуть `scanDevices()` в try/finally |
| `presentation/feature/main/MainUiState.kt` | Добавить `foundOtherDevicesDuringScan: Boolean = false` |
| `presentation/feature/main/MainViewModel.kt` | Auto-connect логика + обновить цвет/info кнопки Радио |
| `di/MeshDataModule.kt` | Добавить `LastConnectedDeviceRepository` + use cases |
| `di/PresentationModule.kt` | Добавить новые use cases в `MainViewModel` |

---

## Фазовый план

### Phase 1 — Domain: Last Connected Device

**Файл**: `domain/mesh/repository/LastConnectedDeviceRepository.kt`
```kotlin
interface LastConnectedDeviceRepository {
    fun get(): MeshDeviceModel?
    suspend fun save(device: MeshDeviceModel)
}
```

**Файл**: `domain/mesh/usecase/GetLastConnectedDeviceUseCase.kt`
```kotlin
class GetLastConnectedDeviceUseCase(
    private val repository: LastConnectedDeviceRepository,
) {
    operator fun invoke(): MeshDeviceModel? = repository.get()
}
```

**Файл**: `domain/mesh/usecase/SaveLastConnectedDeviceUseCase.kt`
```kotlin
class SaveLastConnectedDeviceUseCase(
    private val repository: LastConnectedDeviceRepository,
) : UseCase<MeshDeviceModel, Unit>() {
    override suspend fun invoke(params: MeshDeviceModel) = repository.save(params)
}
```

---

### Phase 2 — Data: Last Connected Device Persistence

**Файл**: `data/local/mesh/LastConnectedDeviceRepositoryImpl.kt`

```kotlin
private const val KEY_ADDRESS = "last_ble_address"
private const val KEY_NAME    = "last_ble_name"

class LastConnectedDeviceRepositoryImpl(
    private val settings: Settings,
) : LastConnectedDeviceRepository {

    override fun get(): MeshDeviceModel? {
        val address = settings.getStringOrNull(KEY_ADDRESS) ?: return null
        val name    = settings.getStringOrNull(KEY_NAME) ?: address
        return MeshDeviceModel(address = address, name = name, rssi = 0)
    }

    override suspend fun save(device: MeshDeviceModel) {
        settings.putString(KEY_ADDRESS, device.address)
        settings.putString(KEY_NAME, device.name)
    }
}
```

---

### Phase 3 — Data: `MeshConnectionRepositoryImpl` — emit Scanning

**Изменения**:

1. Добавить поле:
   ```kotlin
   private val _isScanning = MutableStateFlow(false)
   ```

2. Изменить `connectionStatus` с `combine(3 flows)` на `combine(4 flows)`:
   ```kotlin
   override val connectionStatus: Flow<MeshConnectionStatus> =
       combine(
           serviceRepository.connectionState.onEach { … },
           nodeRepository.ourNodeInfo,
           radioInterfaceService.bleRssi,
           _isScanning,
       ) { state, node, bleRssi, isScanning ->
           if (isScanning && state == ConnectionState.Disconnected) {
               MeshConnectionStatus.Scanning
           } else {
               state.toMeshConnectionStatus(node, pendingDeviceName, bleRssi)
           }
       }
   ```

3. Обернуть тело `scanDevices()` в `try/finally`:
   ```kotlin
   override fun scanDevices(): Flow<List<MeshDeviceModel>> = flow {
       _isScanning.value = true
       try {
           val discovered = mutableListOf<MeshDeviceModel>()
           bleScanner.scan(timeout = 30.seconds, serviceUuid = …).collect { device ->
               if (discovered.none { it.address == device.address }) {
                   discovered.add(MeshDeviceModel(…))
                   emit(discovered.toList())
               }
           }
       } finally {
           _isScanning.value = false
       }
   }
   ```

---

### Phase 4 — Presentation: `MainUiState`

Добавить:
```kotlin
val foundOtherDevicesDuringScan: Boolean = false,
```

---

### Phase 5 — Presentation: `MainViewModel`

#### 5a — Новые зависимости в конструкторе

```kotlin
private val scanDevices: ScanMeshDevicesUseCase,
private val connectToDevice: ConnectToMeshDeviceUseCase,
private val getLastConnectedDevice: GetLastConnectedDeviceUseCase,
private val saveLastConnectedDevice: SaveLastConnectedDeviceUseCase,
```

#### 5b — Новое поле

```kotlin
private var scanJob: Job? = null
```

#### 5c — `init{}`: запуск auto-connect

В конце `init {}` добавить:
```kotlin
startAutoConnect()
```

#### 5d — `observeConnectionStatus` collector: отмена скана при подключении

Внутри существующего `.onEach { status -> … }` добавить в начало:
```kotlin
if (status is MeshConnectionStatus.Connecting || status is MeshConnectionStatus.Connected) {
    scanJob?.cancel()
    scanJob = null
    _uiState.update { it.copy(foundOtherDevicesDuringScan = false) }
}
```

Также: при `Connected` — сохранять последнюю ноду. В блоке обработки `Connected`:
```kotlin
if (!wasConnected) {
    val bleAddress = /* недоступен здесь — сохраняется в startAutoConnect перед connect() */
    // сохранение происходит в startAutoConnect до вызова connectToDevice()
    …
}
```

#### 5e — `startAutoConnect()`

```kotlin
private fun startAutoConnect() {
    val lastDevice = getLastConnectedDevice()
    scanJob?.cancel()
    var autoConnectAttempted = false

    scanJob = scanDevices(NoParams)
        .onEach { devices ->
            if (autoConnectAttempted) return@onEach
            val target = lastDevice?.let { last -> devices.find { it.address == last.address } }
            if (target != null) {
                autoConnectAttempted = true
                _uiState.update { it.copy(foundOtherDevicesDuringScan = false) }
                viewModelScope.launch {
                    saveLastConnectedDevice(target)
                    connectToDevice(ConnectToMeshDeviceParams(target.address, target.name))
                }
                scanJob?.cancel()
            } else {
                _uiState.update { it.copy(foundOtherDevicesDuringScan = devices.isNotEmpty()) }
            }
        }
        .onCompletion { cause ->
            // Скан завершился естественно (timeout 30 с), НЕ по отмене
            if (cause == null && !autoConnectAttempted) {
                val currentStatus = _uiState.value.connectionStatus
                val foundOthers   = _uiState.value.foundOtherDevicesDuringScan
                // Если ноды не найдены вообще — перезапуск скана
                if (currentStatus is MeshConnectionStatus.Disconnected && !foundOthers) {
                    startAutoConnect()
                }
                // Если найдены другие — оставляем "выбрать ноду", ждём действия пользователя
            }
        }
        .catch { /* CancellationException — штатное завершение */ }
        .launchIn(viewModelScope)
}
```

#### 5f — `buildNodeStatusColor()`: обновить логику Scanning

```kotlin
MeshConnectionStatus.Scanning ->
    if (state.foundOtherDevicesDuringScan) Color.Yellow else Color.Red
is MeshConnectionStatus.Connecting -> Color.Yellow
```

#### 5g — `buildConnectionInfoSlot()`: обновить Scanning

```kotlin
MeshConnectionStatus.Scanning ->
    if (state.foundOtherDevicesDuringScan)
        HudInfoSlot(content = "выбрать ноду", color = Color.Yellow)
    else
        HudInfoSlot(content = "Поиск...", color = Color.Red)
```

---

### Phase 6 — DI

#### `MeshDataModule.kt`

Добавить в блок `single { … }`:
```kotlin
single<LastConnectedDeviceRepository> { LastConnectedDeviceRepositoryImpl(get<Settings>()) }
single { GetLastConnectedDeviceUseCase(get()) }
single { SaveLastConnectedDeviceUseCase(get()) }
```

#### `PresentationModule.kt`

В `viewModel { MainViewModel(…) }` добавить:
```kotlin
scanDevices = get(),
connectToDevice = get(),
getLastConnectedDevice = get(),
saveLastConnectedDevice = get(),
```

---

### Phase 7 — Simplify

`/simplify` на изменённых файлах:
- `MeshConnectionRepositoryImpl.kt`
- `MainViewModel.kt`
- `MainUiState.kt`

---

### Phase 8 — Integration Review

- Убедиться, что `scanJob` не утекает: отменяется в `onCleared()` (ViewModelScope автоматически) ✅
- `_isScanning` является частью репозитория (singleton), убедиться что нет гонок при параллельных вызовах `scanDevices()` (в текущем коде только один вызов из MainViewModel)
- Убедиться, что `onCompletion` вызывается только при нормальном завершении, не при `CancellationException`

---

### Phase 9 — Docs & Memory Update

- CLAUDE.md: обновить статус фичи «Авто-подключение при старте» → Done (после реализации)
- Установить статус этого плана → Done
- Добавить запись в Change Log

---

### Phase 10 — Commit Preparation

1. `git status` — перечислить изменённые файлы
2. Stage по именам (никогда `git add -A`)
3. Сообщение коммита на русском, повелительное наклонение
4. Показать staged файлы + сообщение → ждать подтверждения → `git commit`

---

## Coordination Map

```
Phase 1:  [direct coding] — domain: repository interface + use cases
Phase 2:  [direct coding] — data: Settings-based persistence
Phase 3:  [direct coding] — data: MeshConnectionRepositoryImpl + _isScanning
Phase 4:  [direct coding] — presentation: MainUiState
Phase 5:  [direct coding] — presentation: MainViewModel auto-connect logic
Phase 6:  [direct coding] — DI wiring
Phase 7:  /simplify
Phase 8:  [direct review]
Phase 9:  [docs & memory]
Phase 10: [stage → commit]
```

---

## Open Questions

- Нет.

---

## Change Log

- 2026-04-16: создан

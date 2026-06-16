# App Logger

**Status**: Done — 2026-05-15

## Overview

Thin injectable `Logger` interface replaces 110 scattered `android.util.Log.*` calls.
Two-level Logcat tag (`MT/<Feature>`) enables filtering by app ownership and by functional domain.
Tests use `NoOpLogger` — no real log output, no brittle tag assertions.

## File Layout

```
shared/src/commonMain/kotlin/ru/tcynik/meshtactics/domain/logger/
    Logger.kt                  ← interface (domain layer)

app/src/main/java/ru/tcynik/meshtactics/logger/
    AndroidLogger.kt           ← Log.d("MT/$feature", message)

app/src/main/java/ru/tcynik/meshtactics/di/
    LoggerModule.kt            ← single<Logger> { AndroidLogger() }

app/src/test/java/ru/tcynik/meshtactics/logger/
    NoOpLogger.kt              ← all methods: Unit (injected directly in tests)
```

## Interface

```kotlin
interface Logger {
    fun d(feature: String, message: String)
    fun i(feature: String, message: String)
    fun w(feature: String, message: String)
    fun e(feature: String, message: String, throwable: Throwable? = null)
}
```

## Logcat Tag Format

```
"MT/$feature"   →   MT/GPS · MT/BLE · MT/Chat · MT/Map · MT/Contour · MT/Node · MT/App
```

Logcat filters:
- All app logs → `tag:MT`
- Single domain → `tag:MT/GPS`

## Feature Tag Registry

| Tag       | Covers                                                    |
|-----------|-----------------------------------------------------------|
| `GPS`     | GpsService                                                |
| `BLE`     | MeshConnectionRepositoryImpl                              |
| `Chat`    | MeshMessagingRepositoryImpl, IngestReceivedChatMessagesUseCase |
| `Map`     | KmlOverlayParser, IngestReceivedGeoMarksUseCase           |
| `Contour` | CheckNodeSyncUseCase, SyncContoursOnConnectUseCase        |
| `Node`    | MeshConfigRepositoryImpl, NodeProvisioningUseCase         |
| `App`     | MyMeshApplication, MeshTestViewModel                      |

## Koin Wiring

`LoggerModule` registered first in `startKoin` inside `MyMeshApplication`:

```kotlin
val loggerModule = module {
    single<Logger> { AndroidLogger() }
}
```

All production classes receive `logger: Logger` via constructor injection and `get()` in Koin modules.
`GpsService` uses field injection `by inject()` (Android Service — no constructor DI).

## Call-site Convention

```kotlin
class GpsRepository(private val logger: Logger) {
    fun track(lat: Double, lon: Double) {
        logger.d("GPS", "GpsRepository.track: $lat,$lon")
    }
}
```

- Feature tag is a constant string literal at the call site.
- No wrapper class, no companion object.

## Log Message Rules

### 1. Searchable context — sufficiency rule

A log message must be searchable: a unique substring in its text must lead back to the call site.

If the message text itself is descriptive and specific enough — use it as-is, no prefix needed.
Prefix `ClassName.methodName` is required when the message is short, generic, or the method name is common (`onCreate`, `onResume`, `init`, `start`, etc.).

```kotlin
// Bad — too short, not searchable
logger.d("GPS", "onCreate")
logger.d("BLE", "connected")

// Good — descriptive message, searchable by its own text
logger.d("BLE", "приступаю к фильтрации отсканированного BLE-девайса")
logger.d("GPS", "запрос разрешений на геолокацию уже был выдан ранее")

// Good — prefix needed: method name is common, message is short
logger.d("GPS", "GpsService.onCreate")
logger.d("BLE", "MeshConnectionRepositoryImpl.connect: nodeId='$nodeId'")
```

### 2. No duplicate message texts within a single file

Each log message in a file must be unique. When two paths are semantically similar, the message must reflect the difference.

```kotlin
// Bad — two lines, same message, ungreppable
logger.d("GPS", "GpsService.onStartCommand: started")
logger.d("GPS", "GpsService.onStartCommand: started") // second path

// Good
logger.d("GPS", "GpsService.onStartCommand: foreground started")
logger.d("GPS", "GpsService.onStartCommand: restarted after disconnect")
```

### 3. No one-word / context-free messages

Messages like `"start"`, `"done"`, `"error"`, `"ok"` are forbidden.
A log must convey enough to understand *what happened and where*, without opening the file.

```kotlin
// Bad
logger.e("BLE", "error")
logger.d("Node", "done")

// Good
logger.e("BLE", "MeshConnectionRepositoryImpl.observeNodes: exception in flow", t)
logger.d("Node", "NodeProvisioningUseCase.execute: provisioning complete for $nodeId")
```

### 4. Data in messages — use key=value form

When logging variable data, prefer `key=value` format for parsability.
String values must be wrapped in single quotes `''` to visually separate them from surrounding log text.

```kotlin
// Bad
logger.d("GPS", "GpsService.onLocationResult: ${location.lat} ${location.lon}")
logger.d("BLE", "подключились к девайсу ${device.name}")

// Good — numbers without quotes, strings in single quotes
logger.d("GPS", "GpsService.onLocationResult: lat=${location.lat} lon=${location.lon}")
logger.d("BLE", "подключились к девайсу name='${device.name}' address='${device.address}'")
```

## Test Convention

Inject `NoOpLogger()` directly — no Koin, no `mockkStatic`:

```kotlin
val useCase = IngestReceivedChatMessagesUseCase(
    repo = mockk(),
    logger = NoOpLogger()
)
```

`NoOpLogger` is NOT registered in any Koin module.

## Migrated Files

| File | Tag |
|------|-----|
| `KmlOverlayParser` | `Map` |
| `MeshConfigRepositoryImpl` | `Node` |
| `MeshConnectionRepositoryImpl` | `BLE` |
| `MeshMessagingRepositoryImpl` | `Chat` |
| `CheckNodeSyncUseCase` | `Contour` |
| `SyncContoursOnConnectUseCase` | `Contour` |
| `IngestReceivedChatMessagesUseCase` | `Chat` |
| `IngestReceivedGeoMarksUseCase` | `Map` |
| `NodeProvisioningUseCase` | `Node` |
| `MeshTestViewModel` | `App` |
| `GpsService` | `GPS` |
| `MyMeshApplication` | `App` |

Updated DI modules: `MapDataModule`, `MeshDataModule`, `ChatDataModule`, `GeoMarkDataModule`, `UserSettingsModule`, `PresentationModule`.

## Out of Scope

Log-to-file, remote crash reporting, structured JSON logs, runtime log level configuration.

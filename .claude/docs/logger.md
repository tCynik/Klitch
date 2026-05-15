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
        logger.d("GPS", "position $lat,$lon")
    }
}
```

- Feature tag is a constant string literal at the call site.
- No wrapper class, no companion object.

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

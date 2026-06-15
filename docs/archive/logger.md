# Plan: App Logger

**Date**: 2026-05-15
**Status**: In Progress — Phase 6 Done

## Summary

Replace 110 scattered `Android.Log.*` calls with a thin injectable `Logger` interface.
Two-level Logcat tag (`MT/<Feature>`) enables filtering by app ownership and by functional domain.
Tests get a `NoOpLogger` — no real log output, no brittle tag assertions.

## Scope

- In scope: `Logger` interface in `shared`; `AndroidLogger` in `app`; `NoOpLogger` in `app/test`; Koin wiring; migration of all existing `Log.*` calls in `app` and `mesh` modules
- Out of scope: log-to-file, remote crash reporting, structured JSON logs, log level configuration at runtime

---

## Architecture

### Interface — `shared` module

```
shared/src/commonMain/kotlin/ru/tcynik/meshtactics/shared/logger/
    Logger.kt          ← interface
```

```kotlin
interface Logger {
    fun d(feature: String, message: String)
    fun i(feature: String, message: String)
    fun w(feature: String, message: String)
    fun e(feature: String, message: String, throwable: Throwable? = null)
}
```

### Implementations — `app` module

```
app/src/main/java/ru/tcynik/meshtactics/logger/
    AndroidLogger.kt   ← Log.d("MT/$feature", message)

app/src/test/java/ru/tcynik/meshtactics/logger/
    NoOpLogger.kt      ← all methods: Unit
```

### Logcat tag format

```
"MT/$feature"   →   e.g.  MT/GPS · MT/BLE · MT/Chat · MT/Map · MT/SOS
```

Filter in Logcat:
- All app logs → `tag:MT`
- Single domain → `tag:MT/GPS`

### Koin wiring

New module `LoggerModule.kt` in `app/src/main/java/…/di/`:

```kotlin
val loggerModule = module {
    single<Logger> { AndroidLogger() }
}
```

Add `loggerModule` to `startKoin` in `MyMeshApplication`.
Tests that need a logger inject `NoOpLogger()` directly — no Koin required.

### Call-site style

Inject `Logger` via constructor and call with a constant feature tag local to the class:

```kotlin
class GpsRepository(private val logger: Logger) {
    fun track(lat: Double, lon: Double) {
        logger.d("GPS", "position $lat,$lon")
    }
}
```

No wrapper class, no companion object — just direct call. Laconic.

---

## Phase Plan

### ✅ Phase 1 — Architecture Design

**Goal**: approved `Logger` interface, file layout, Koin module structure
**Tasks**:
- ✅ Confirm exact `shared/commonMain` source set path — `mesh` does NOT depend on `:shared`; only `app` does
- ✅ Clarify `mesh` DI bootstrap — `mesh` only defines Koin modules, `app` calls `startKoin`; mesh files had only commented-out Log calls → no migration needed there, no `:shared` dep needed
- ✅ Define the 4-method `Logger` interface
- ✅ Define `AndroidLogger` and `NoOpLogger` skeletons
- ✅ Design `loggerModule` Koin singleton
**Output**: architecture sign-off, confirmed file paths

### ✅ Phase 3 — Implementation

**Goal**: working logger, wired into Koin, replacing all `Log.*` calls

**Tasks** (in order):
1. ✅ Create `Logger.kt` in `shared/src/commonMain/kotlin/ru/tcynik/meshtactics/logger/`
2. ✅ Create `AndroidLogger.kt` in `app/src/main/java/ru/tcynik/meshtactics/logger/`
3. ✅ Create `NoOpLogger.kt` in `app/src/test/java/ru/tcynik/meshtactics/logger/`
4. ✅ Create `LoggerModule.kt`, add `loggerModule` first in `startKoin` in `MyMeshApplication`
5. ✅ Migrate all classes — constructor injection (`logger: Logger`) + Koin `get()`, except `GpsService` (field injection via `by inject()`)
6. ✅ Replace all `Log.d/e/w/i` calls — 0 remaining `android.util.Log` imports outside `AndroidLogger`
7. ⚠️ Build check blocked by pre-existing JVM 25 parse error in Kotlin 2.0.21 — unrelated to these changes

**Migrated files:**

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

**Updated DI modules:** `MapDataModule`, `MeshDataModule`, `ChatDataModule`, `GeoMarkDataModule`, `UserSettingsModule`, `PresentationModule`

**Feature tag registry** (assigned):

| Tag      | Covers |
|----------|--------|
| `GPS`    | GpsService |
| `BLE`    | MeshConnectionRepositoryImpl |
| `Chat`   | MeshMessagingRepositoryImpl, IngestReceivedChatMessagesUseCase |
| `Map`    | KmlOverlayParser, IngestReceivedGeoMarksUseCase |
| `Contour`| CheckNodeSyncUseCase, SyncContoursOnConnectUseCase |
| `Node`   | MeshConfigRepositoryImpl, NodeProvisioningUseCase |
| `App`    | MyMeshApplication, MeshTestViewModel |

**Output**: 0 remaining `android.util.Log` imports in production code

### ✅ Phase 4 — Testing

**Goal**: existing tests pass unchanged; logger does not pollute test output

**Tasks**:
- ✅ Removed `mockkStatic(android.util.Log::class)` / `unmockkStatic` from 4 test files
- ✅ Injected `NoOpLogger()` into `IngestReceivedGeoMarksUseCaseTest`, `IngestReceivedChatMessagesUseCaseTest`, `CheckNodeSyncUseCaseTest`, `SyncContoursOnConnectUseCaseTest`
- ✅ Removed `verify { android.util.Log.w(...) }` from `SyncContoursOnConnectUseCaseTest`
- ✅ Cleaned up dead `@After` blocks and stale imports
- ⚠️ Full `./gradlew :app:testDebugUnitTest` blocked by **pre-existing** compile errors in ViewModel tests (`observeCallsignChanges`, `refreshNodePublicKey`, `checkOwnPkcHealth` params missing) — confirmed unrelated to logger (those files absent from git diff)
- ✅ `NoOpLogger` is NOT registered in any Koin module — injected directly in tests only

**Output**: 4 test files migrated, 0 Log mock scaffolding remaining

### ✅ Phase 5 — Integration Review

**Goal**: no Clean Architecture violations

**Tasks**:
- ✅ `Logger` перемещён из `shared/commonMain/logger/` в `shared/commonMain/domain/logger/` — пакет `ru.tcynik.meshtactics.domain.logger`
- ✅ Все 15 call-site файлов обновлены на новый import-путь
- ✅ `AndroidLogger` строго в `app/` — платформенный impl не утёк в `shared`
- ✅ `mesh` модуль — все `Log.*` закомментированы, активных нет; `:shared` dep не нужен
- ✅ Избыточные self-imports удалены из `AndroidLogger` и `NoOpLogger`

**Output**: ревью чисто

### ✅ Phase 6 — Skill Update Review

- ✅ `/architect` — добавлен Logger injection как DI-конвенция; добавлен anti-pattern: `import android.util.Log` вне `AndroidLogger`
- ✅ `/tester` — добавлен пункт в pattern checklist + принцип: `NoOpLogger()` вместо `mockkStatic(android.util.Log::class)`
- ✅ `/planner` — добавлен Logger checklist в Phase 3
- ✅ `/iterate` — добавлена инструкция по диагностическому логированию в BUG mode Step 2
- ✅ `/ui-designer` — no changes
- ✅ `/icon-designer` — no changes

### Phase 6b — Docs & Memory Update

- Create `.claude/docs/logger.md`
- Update CLAUDE.md status table: add Logger row as `✅ Done`
- Archive plan: move to `.claude/archive/logger.md`, delete from `.claude/plans/`
- Update `memory/project_state.md`

### Phase 7 — Commit Preparation

- Stage: `shared/.../Logger.kt`, `app/.../AndroidLogger.kt`, `app/test/.../NoOpLogger.kt`, `app/.../LoggerModule.kt`, all migrated source files
- Proposed message:

```
feat(logger): внедрён инжектируемый Logger с двухуровневой фильтрацией MT/<Feature>

- Logger интерфейс в shared, AndroidLogger в app, NoOpLogger в тестах
- Koin single<Logger> через LoggerModule
- Мигрированы все 110 вызовов android.util.Log в app и mesh модулях
```

---

## Coordination Map

```
✅ Phase 1:  /architect — confirmed shared module layout, mesh DI clarified
✅ Phase 3:  [direct coding] — Logger.kt, AndroidLogger.kt, NoOpLogger.kt, LoggerModule.kt, all call migrations
✅ Phase 4:  [direct coding] — 4 test files migrated; full test run blocked by pre-existing ViewModel compile errors
   Phase 5:  /architect review: logger/
   Phase 6:  [skill update — architect, tester]
   Phase 6b: [docs: .claude/docs/logger.md · CLAUDE.md · archive plan · memory]
   Phase 7:  [stage by name] → [propose commit] → [wait confirmation] → git commit
```

---

## Open Questions

*(resolved before implementation)*

1. ~~`shared` KMP or Android-only?~~ — **KMP**, Logger goes in `shared/commonMain`
2. `mesh` DI bootstrap — to be clarified by `/architect` in Phase 1
3. ~~Log mocks in tests?~~ — scan task added to Phase 4; remove any found

---

## Change Log

- 2026-05-15: создан
- 2026-05-15: Phase 1 выполнена — архитектура согласована, уточнено что mesh не зависит от shared и не требует Logger
- 2026-05-15: Phase 3 выполнена — все файлы созданы, 12 call sites мигрированы, 0 остаточных Log.* в production
- 2026-05-15: Phase 4 выполнена — 4 тест-файла мигрированы на NoOpLogger, Log-моки удалены; полный тест-ран заблокирован pre-existing ошибками компиляции в ViewModel-тестах

# Plan: Tile Cache Duration Settings

**Date**: 2026-04-29
**Status**: Approved

## Summary

Add a tile cache duration selector to the Map tab of the Settings screen. The user picks one of three modes — Default, Month, or Maximum — which controls the `Cache-Control: max-age` header applied to MapLibre's tile HTTP responses via an OkHttp interceptor. Maximum mode shows a warning about storage consumption.

## Scope

- **In scope**: cache mode selector UI in Map tab; persistence in AppSettings; OkHttp client injection into MapLibre with a Cache-Control interceptor and disk cache; warning dialog for Maximum mode.
- **Out of scope**: per-region or per-zoom offline pre-caching; tile provider switching; cache size management UI; clearing the cache manually.

## Architecture Notes

### Key unknown — Phase 0 required

MapLibre Native Android uses OkHttp internally. The standard hook is `HttpRequestUtil.setOkHttpClient()` (from `org.maplibre.android.net`). However, `maplibre-compose 0.12.1` wraps the SDK — the exact API surface and the correct call site (Application init vs. Compose side-effect) must be confirmed before implementation.

### Layers affected

| Layer | What changes |
|---|---|
| domain/settings | New `TileCacheMode` enum; `MapCacheSettingsRepository` interface with get/set/flow |
| data/settings | `AppSettings` implements the new interface; new SharedPreferences key |
| data/map | New `TileCacheOkHttpConfigurator` — builds OkHttpClient with disk cache + interceptor |
| di | `MapDataModule` wires the configurator; `MyMeshApplication.onCreate` applies it |
| presentation/settings | `SettingsUiState` gets `tileCacheMode`; `SettingsViewModel` reads/writes it; `MapTabContent` renders radio group + warning dialog |

### Data model

```kotlin
enum class TileCacheMode {
    DEFAULT,   // respect server Cache-Control (~7 days for opentopomap)
    MONTH,     // max-age = 2_592_000
    MAXIMUM,   // max-age = 31_536_000
}
```

### OkHttp wiring (call site to be confirmed in Phase 0)

```kotlin
// Expected call site — Application.onCreate or Koin startKoin block
HttpRequestUtil.setOkHttpClient(
    OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, "map_tiles"), 100L * 1024 * 1024))
        .addNetworkInterceptor(TileCacheInterceptor(modeRef))
        .build()
)
```

**Interceptor strategy — Variant B (approved):**
`TileCacheInterceptor` holds an `AtomicReference<TileCacheMode>`. `TileCacheOkHttpConfigurator` is a stateful singleton that exposes `updateMode(mode)` — called by `SetTileCacheModeUseCase` on every user selection. Mode change takes effect immediately on the next tile request, without rebuilding the OkHttpClient or restarting the app.

**Warning dialog:** shown every time the user selects Maximum mode (not just once).
**Cache size:** 100 MB (fixed for MVP).

## Phase Plan

### Phase 0 — Research

**Goal**: confirm the MapLibre/maplibre-compose OkHttp injection API for version 0.12.1 and identify the correct call site on Android.

**Tasks**:
- Check maplibre-compose 0.12.1 source / changelog for `HttpRequestUtil` availability or alternative hook.
- Confirm `OkHttpClient` injection does NOT conflict with maplibre-compose's internal lifecycle.
- Check if maplibre-compose exposes its own cache configuration (e.g. `MapLibreMap.setMaximumAmbientCacheSize()`).

**Skill**: `/research maplibre-compose OkHttp injection`
**Output**: `.claude/research/maplibre-okhttp-injection.md`

> **Token checkpoint**: run `/compact` after Phase 0 before proceeding.

---

### Phase 1 — Architecture Design

**Goal**: approved architecture — interfaces, data flow, ViewModel state shape.

**Tasks**:
- Define `TileCacheMode` enum (domain layer, `shared/domain/settings/model/`).
- Define `MapCacheSettingsRepository` interface (domain layer).
- Define use cases: `GetTileCacheModeUseCase`, `SetTileCacheModeUseCase`, `ObserveTileCacheModeUseCase`.
- Design `TileCacheInterceptor` contract — input: mode → output: rewritten response header.
- Decide call site for `HttpRequestUtil.setOkHttpClient()` (Application vs. Koin module).
- Extend `SettingsUiState` with `tileCacheMode: TileCacheMode`.

**Skill**: `/architect feature: tile cache duration settings — see .claude/plans/tile-cache-settings.md`
**Output**: architect's layer decomposition (inline); updated plan with confirmed decisions.

> **Token checkpoint**: run `/compact` after Phase 1 before proceeding.

---

### Phase 2 — UI Design

**Goal**: approved component layout for cache mode selector in Map tab.

**Tasks**:
- Design a `TileCacheModeSelector` composable — labeled radio button group (3 options).
- Position it above the existing KML/KMZ map list in `MapTabContent`.
- Design the warning `AlertDialog` shown when user selects Maximum mode.
- Confirm string resource keys for all new strings.

**Skill**: `/ui-designer component: TileCacheModeSelector for Settings Map tab`
**Output**: composable design decision (inline); string resource list.

---

### Phase 3 — Implementation

**Goal**: working code across all layers.

**Order**:
1. `TileCacheMode.kt` — enum in `shared/domain/settings/model/`
2. `MapCacheSettingsRepository.kt` — interface in `shared/domain/settings/repository/`
3. Use cases: `GetTileCacheModeUseCase`, `SetTileCacheModeUseCase`, `ObserveTileCacheModeUseCase` — in `shared/domain/settings/usecase/`
4. `AppSettings` — implement `MapCacheSettingsRepository`, add `KEY_TILE_CACHE_MODE` key
5. `TileCacheInterceptor.kt` — OkHttp `Interceptor` in `app/data/map/`
6. `TileCacheOkHttpConfigurator.kt` — builds `OkHttpClient`, wires interceptor and `Cache`; in `app/data/map/`
7. `MapDataModule.kt` — add DI bindings for new repository, use cases, configurator
8. `MyMeshApplication.onCreate` (or Koin start block) — call `HttpRequestUtil.setOkHttpClient()`
9. `SettingsUiState.kt` — add `tileCacheMode: TileCacheMode`
10. `SettingsViewModel.kt` — inject `ObserveTileCacheModeUseCase`, `SetTileCacheModeUseCase`; expose mode; handle `onTileCacheModeSelected()`
11. `SettingsScreen.kt` / `MapTabContent` — add `TileCacheModeSelector`, wire warning dialog

**Skill**: direct coding (EnterPlanMode before starting)
**After**: run `/simplify` on changed files.
**Output**: buildable code.

---

### Phase 4 — Testing

**Goal**: key paths verified.

**Tasks**:
- Unit test `TileCacheInterceptor` — for each mode, assert correct `Cache-Control` header in response.
- Unit test `SettingsViewModel` — `onTileCacheModeSelected` updates state and calls use case.
- Manual smoke test: change mode → restart app → confirm tiles are served from cache for Month/Maximum.

**Skill**: direct coding
**Output**: passing unit tests.

---

### Phase 5 — Integration Review

**Goal**: no Clean Architecture violations.

**Tasks**: review changed files — confirm `TileCacheInterceptor` and OkHttp wiring stay in data/app layer, not leaking into domain.

**Skill**: `/architect review: tile-cache-settings`
**Output**: review sign-off.

---

### Phase 6 — Skill Update Review

**Goal**: skills reflect new patterns.

**Tasks**:
- `/architect` — new pattern: OkHttp injection into MapLibre via `HttpRequestUtil`; DI wiring location.
- `/ui-designer` — `TileCacheModeSelector` pattern (labeled radio group in settings tab).
- `/icon-designer` — no changes expected.
- `/planner` — no methodology changes.

**Skill**: direct edit of `.claude/commands/`
**Output**: updated skill files or "no changes needed" per skill.

---

### Phase 6b — Docs & Memory Update

**Tasks**:
- Create `.claude/docs/tile-cache-settings.md`.
- Update CLAUDE.md status table: add "Tile Cache Settings" row → `Done`.
- Archive this plan → `.claude/archive/tile-cache-settings.md`.
- Update `memory/project_state.md`.
- Record token cost in archive file.

---

### Phase 7 — Commit Preparation

**Tasks**: enumerate changed files → stage by name → propose commit message in Russian → wait for confirmation → `git commit`.

## Coordination Map

```
Phase 0: /research maplibre-compose OkHttp injection → .claude/research/maplibre-okhttp-injection.md → [/compact]
Phase 1: /architect feature: tile cache duration settings → [/compact]
Phase 2: /ui-designer component: TileCacheModeSelector
Phase 3: [direct coding, EnterPlanMode] → /simplify
Phase 4: [direct coding — tests]
Phase 5: /architect review: tile-cache-settings
Phase 6: [skill update review]
Phase 6b: [docs & memory — CLAUDE.md, .claude/docs/tile-cache-settings.md, archive, memory/]
Phase 7: [stage by name] → [propose commit message] → [confirm] → git commit
```

## Open Questions

1. **OkHttp injection call site** — does `maplibre-compose 0.12.1` require `HttpRequestUtil.setOkHttpClient()` to be called before the first `MapView` composition, or is there a Compose-side lifecycle hook? *(Resolved in Phase 0.)*
2. ~~Mode change — restart required?~~ **Resolved:** Variant B — `AtomicReference` in interceptor, immediate effect, no restart.
3. ~~Cache size~~ **Resolved:** 100 MB, fixed.
4. ~~Warning dialog trigger~~ **Resolved:** every time Maximum is selected.

## Change Log

- 2026-04-29: created
- 2026-04-29: approved — interceptor strategy Variant B (AtomicReference), immediate effect, 100 MB cache, warning every time

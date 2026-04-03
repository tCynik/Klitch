# Plan: Application Structure

**Date**: 2026-04-03
**Status**: Done

## Summary

Establish the full package and feature-module structure for MeshTactics, aligned with the product spec.
Current state: one prototype screen (`meshtest`). Target state: Clean Architecture with feature slices
covering map, markers, chat, groups, telemetry, node management, and settings — ready for incremental
feature implementation across MVP and Beta 1.0.

## Scope

**In scope:**
- Approved target package tree for `app/` and (future) `shared/`
- Feature module list with layer responsibilities
- Data repository interface definitions for all 5 transports
- Navigation graph skeleton (routes, placeholder screens)
- DI module layout per feature
- OSD layer composition pattern for the main screen

**Out of scope:**
- Actual implementation of any feature beyond scaffolding
- Map library integration (blocked on Phase 0 decision)
- KMP migration of existing `data/mesh` code (separate task)
- Backend (MQTT server, mapping API)

---

## Phase 0 — Research: Map Library & KMP Readiness ✅ DONE (2026-04-03)

**Goal**: Resolve two blockers before any architecture is designed.

### Map Library Decision: **MapLibre Native Android** (primary)

**Evaluated:** OsmDroid, MapLibre, Mapsforge/VTM, Mapbox Maps SDK v11, Google Maps SDK

| Requirement | MapLibre result |
|---|---|
| Custom XYZ/TMS tile URLs | ✅ Native — `RasterSource` in Style JSON, supports both XYZ and TMS scheme |
| Multi-layer tiles (topo + soviet overlay) | ✅ Full layer ordering in Style JSON |
| Tile disk caching + offline | ✅ Ambient auto-cache + explicit `OfflineManager` region downloads |
| KMZ / KML loading | ❌ No native support — see mitigation below |
| MBTiles offline packages | ✅ First-class via `mbtiles://` URI; PMTiles also supported (v11.7.0+) |
| Jetpack Compose | ✅ Official `maplibre-compose` (org.maplibre.compose v0.12.1, Nov 2025) |
| License | ✅ BSD 2-Clause — no API key, no usage cost, no ToS restrictions |
| Maintenance | ✅ Active (monthly releases, Vulkan backend Dec 2024) |

**Why not others:**
- OsmDroid — **archived November 2024**, no future security or SDK updates. Dead end.
- Mapsforge/VTM — offline `.map` format niche, no Compose, no KML, small community.
- Mapbox — proprietary closed-source core, paid API key required even for custom tiles.
- Google Maps — cannot replace base tile layer; custom tiles only as overlays; no MBTiles.

**KMZ/KML gap mitigation (Req 3 — core product feature):**

Option A (preferred): Use `android-maps-utils` `KmlLayer` as a standalone parser to extract
geometry, then inject into MapLibre as `GeoJsonSource`. Does not require Google Maps at runtime.

Option B: Use `osmbonuspack` `KmlDocument` as parser, extract geometry → MapLibre `GeoJsonSource`.

Option C: Implement a focused in-app KMZ parser (KMZ = ZIP + KML XML; well-specified).
Required for ground overlays (georeferenced raster images) — requires MapLibre `ImageSource`.

**Recommendation**: Prototype Option A first; fall back to Option C if ground overlays are needed
(nakarte.me Soviet topo overlays may use georeferenced raster — needs testing).

### Open Questions Remaining from Phase 0

1. KMZ ground overlays: MapLibre `ImageSource` + `LatLonBox` pipeline needs a prototype.
2. `maplibre-compose` feature gaps: verify `RasterSource`, `OfflineManager`, image overlays are
   exposed in Compose API (or confirmed accessible via `MapEffect` escape hatch).
3. Russian tile servers (nakarte.me, marshruty.ru): test HTTPS/SSL + User-Agent requirements
   on Android's HTTP stack — may need custom interceptor in MapLibre's tile fetcher.
4. Device compatibility: use `android-sdk-opengl` artifact (OpenGL ES fallback) instead of
   default Vulkan backend for broader field device support at minSdk 24.
5. PMTiles vs MBTiles: evaluate which format paid map pack vendors supply.

### KMP Readiness Decision

Deferred to post-Beta-1.0. MapLibre Native Android is not yet a KMP library (only the Compose
wrapper is Compose Multiplatform). Forcing KMP split now would add friction without benefit.
Current `domain/mesh` + `data/mesh` stays in `app/` until after Beta 1.0.

**Output**: ✅ Map library decision made. Phase 1 unblocked.

---

## Phase 1 — Architecture Design: Package & Feature Structure

**Goal**: Approved target package tree and feature boundary definitions.

**Tasks:**

### 1.1 — Target Package Tree
Propose and approve the following structure:

```
app/
  src/main/java/ru/tcynik/meshtactics/
    │
    ├── application/          # Application class, AppBuildConfigProvider
    │
    ├── di/                   # DI root + per-feature modules
    │   ├── AppModule.kt
    │   ├── MeshDataModule.kt (existing)
    │   ├── MapDataModule.kt
    │   ├── UserDataModule.kt
    │   └── PresentationModule.kt (existing, expand)
    │
    ├── navigation/           # NavGraph, Routes, Guards (existing, expand)
    │
    ├── ui/
    │   └── theme/            # existing
    │
    ├── domain/               # App-level domain (user profile, groups, markers)
    │   ├── user/
    │   │   ├── model/        # UserProfileModel, UserGroupModel
    │   │   └── repository/   # UserRepository (interface)
    │   ├── marker/
    │   │   ├── model/        # MarkerModel, TrackModel, PolygonModel
    │   │   └── repository/   # MarkerRepository (interface)
    │   └── group/
    │       ├── model/        # GroupModel, GroupRole
    │       └── repository/   # GroupRepository (interface)
    │
    ├── data/
    │   ├── mesh/             # existing (Meshtastic transport)
    │   ├── mqtt/             # MQTT transport (stub, post-MVP)
    │   │   └── repository/
    │   ├── wifi/             # Wi-Fi transport (stub, post-MVP)
    │   │   └── repository/
    │   ├── map/              # Map data: MVP = single XYZ source; cache/import/KMZ = post-MVP
    │   │   └── repository/   # MapTileRepository interface only in MVP
    │   └── local/            # SQLDelight DB (markers, groups, tracks)
    │       └── db/           # tile cache schema added post-MVP
    │
    └── presentation/
        └── feature/
            ├── main/         # Main landscape screen (map + HUD only)
            │   ├── MainScreen.kt
            │   ├── MainViewModel.kt
            │   └── osd/      # OSD Compose layers (2 total — see OSD decision)
            │       ├── MapLibreLayer.kt      # layers 1-5: map + all spatial content
            │       └── HudControlsLayer.kt   # layer 6: left+right button columns
            │
            ├── map/          # Map controls OSD (left column)
            │   ├── MapControlsOsd.kt
            │   └── MapControlsViewModel.kt
            │
            ├── menu/         # Right column menu buttons OSD
            │   └── MenuOsd.kt
            │
            ├── chat/         # Chat feed OSD + Chat modal
            │   ├── ChatFeedOsd.kt
            │   ├── ChatModal.kt
            │   └── ChatViewModel.kt
            │
            ├── markers/      # Markers OSD + Marker management modal
            │   ├── MarkersOsd.kt
            │   ├── MarkerManagementModal.kt
            │   └── MarkersViewModel.kt
            │
            ├── telemetry/    # Telemetry OSD (participants)
            │   ├── TelemetryOsd.kt
            │   └── TelemetryViewModel.kt
            │
            ├── friends/      # Friends/contacts OSD
            │   ├── FriendsOsd.kt
            │   └── FriendsViewModel.kt
            │
            ├── groups/       # Groups OSD + Group management modal [Beta 1.0]
            │   ├── GroupsOsd.kt
            │   ├── GroupManagementModal.kt
            │   └── GroupsViewModel.kt
            │
            ├── settings/     # Settings modal
            │   ├── SettingsModal.kt
            │   └── SettingsViewModel.kt
            │
            ├── node/         # Node status modal + Node settings modal
            │   ├── NodeStatusModal.kt
            │   ├── NodeSettingsModal.kt
            │   └── NodeViewModel.kt
            │
            └── meshtest/     # [TEMPORARY] debug prototype — delete after all features migrated
                              # migration tracker: chat ☐  node_settings ☐  telemetry ☐
```

### 1.2 — OSD Layer Composition Pattern

**Decision**: spatial content lives inside MapLibre; "modals" are separate NavGraph destinations.

The main screen is a `Box` with **2 Compose layers**:

```
Box(Modifier.fillMaxSize()) {
    MapLibreLayer(Modifier.fillMaxSize())     // z=1..5 — all geo content
    HudControlsLayer(Modifier.fillMaxSize())  // z=6    — button columns (HUD)
}
```

**MapLibreLayer** internally manages all 5 spatial product layers:

| Product layer | MapLibre implementation |
|---|---|
| 1. Map tiles | `RasterLayer` (XYZ/TMS sources) |
| 2. Grids | `LineLayer` (vector, styled in JSON) |
| 3. Markers & tracks | `PointAnnotation` + `PolylineAnnotation` + `PolygonAnnotation` |
| 4. Channel telemetry (live positions) | `PointAnnotation` (updated via Flow) |
| 5. Channel markers | `PointAnnotation` |

**Why this works:**
- MapLibre owns the coordinate system — pan/zoom is automatic for all spatial content
- `maplibre-compose` provides `PointAnnotation` / `PolylineAnnotation` / `PolygonAnnotation`
  as Compose composables that work in map geo-coordinates
- No manual geo→screen projection code
- GPU-accelerated rendering; live telemetry updates are cheap (GeoJSON source update)

**Trade-off for rich markers:**
Complex callouts (callsign + battery + signal badge) can't be arbitrary Compose widgets inside
MapLibre. Mitigation: render `@Composable` to `Bitmap` via `rememberComposeBitmapPainter()` or
`ComposeView.drawToBitmap()`, pass as `PointAnnotation` icon. Canonical MapLibre pattern.

### 1.3 — Modal Screens: NavGraph Destinations (not overlays)

**Decision**: modals are separate NavGraph destinations, not a Compose overlay layer.

Rationale: modals don't need map interaction while open; full screen coverage is acceptable.
Using Navigation destinations gives free back-stack management and clean feature isolation.
No `ModalType` sealed class needed; no custom back button handling.

```kotlin
NavHost(startDestination = "main") {
    composable("main")              { MainScreen() }           // map + HUD
    composable("chat")              { ChatScreen() }           // full-screen
    composable("settings")          { SettingsScreen() }       // full-screen
    composable("node_settings")     { NodeSettingsScreen() }   // full-screen
    composable("marker_management") { MarkerManagementScreen() }
    composable("group_management")  { GroupManagementScreen() } // Beta 1.0
    dialog("node_status")           { NodeStatusDialog() }     // small dialog
}
```

Screen type per destination:
- `composable()` — full-screen takeover (Chat, Settings, NodeSettings, MarkerManagement, GroupManagement)
- `dialog()` — floating dialog, map not visible behind (NodeStatus)

HUD buttons call `navController.navigate("destination")`. Back stack managed by Navigation.

Define this 2-layer + NavGraph-destinations pattern as the canonical template in `/architect`.

### 1.3 — Navigation Structure

All screens live in one NavGraph. No separate Activities. See 1.2 for modal destination rationale.

| Route | Type | Description |
|---|---|---|
| `main` | `composable` | Default destination — map + HUD |
| `chat` | `composable` | Full-screen chat feed + input |
| `settings` | `composable` | App settings |
| `node_settings` | `composable` | Meshtastic node config |
| `marker_management` | `composable` | Marker list, edit, delete |
| `group_management` | `composable` | Groups + roles (Beta 1.0) |
| `node_status` | `dialog` | Compact node status popup |
| `meshtest` | `composable` | Debug screen (debug builds only) |

### 1.4 — Map Feature Staging

Cartography is implemented in stages. `MapTileRepository` interface is designed for the full
feature set from day one, but implementations are added incrementally.

| Feature | Stage | Notes |
|---|---|---|
| Single hardcoded XYZ tile source | MVP | One tile source (e.g. OpenTopoMap), no UI switcher |
| Markers (`PointAnnotation`) | MVP | Local storage via SQLDelight |
| Tile source switcher (multi-source) | Beta 1.0 | UI in HUD right column |
| Tile caching / offline buffer | Beta 1.0 | `OfflineManager` + cache size settings |
| Pre-packaged map imports (MBTiles/PMTiles) | Beta 1.0 | File picker + MapLibre `mbtiles://` |
| KMZ file import | Beta 1.0 | Parser + `GeoJsonSource` / `ImageSource` |
| Soviet topo tile sources | Beta 1.0 | Additional XYZ source configs |

This staging means `data/map/` in MVP contains only the `MapTileRepository` interface +
a single `HardcodedXyzTileSource` implementation. No caching, no importer.

### 1.5 — MeshTest Screen Policy

`meshtest` is a debug tool, not a product feature. It stays in the codebase as long as it
provides debugging value not yet covered by proper feature screens.

**Removal condition**: delete `meshtest` only after ALL of the following are implemented
and verified in their respective feature screens:

| Capability in meshtest | Target feature screen | Status |
|---|---|---|
| BLE connection + device scan | `node_status` / `node_settings` | ☐ |
| Channel config (WriteChannel) | `node_settings` | ☐ |
| Messaging / chat | `chat` | ☐ |
| Telemetry display (nodes) | `MapLibreLayer` (PointAnnotation) | ☐ |
| Packet log | `node_status` (debug section) | ☐ |

Until all rows are checked, `meshtest` remains. Gate the route behind `BuildConfig.DEBUG`.

### 1.6 — Data Repository Abstraction Contract

All transports (Mesh, MQTT, WiFi) must implement the same domain-facing interfaces:
- `MessageRepository` — send/observe messages
- `NodeRepository` — observe nodes and their locations
- `ChannelRepository` — channel config read/write

Define these interfaces in `domain/` so data layer is interchangeable.
In MVP only the Meshtastic implementation is non-stub.

**Skill**: `/architect feature: full package and feature structure for MeshTactics — see plan app-structure.md Phase 1`
**Output**: Code scaffold (empty files + stub classes) committed to `project_features_structure` branch

---

## Phase 2 — UI Design: Main Screen OSD Layout

**Goal**: Approved visual design for the main screen OSD layer composition and button columns.

**Tasks:**
1. Define the HUD layout: full-screen `Box`, button column sizing and padding, z-ordering of 3 Compose layers
2. Design left column (map tool buttons): follow, zoom+, zoom-, grab, record track, plan route
3. Design right column (menu buttons): main menu, settings, map source, filter, chat, add marker, tools
4. Establish modal overlay visual treatment (dimming? blur? none? — map always visible behind?)
5. Define OSD element theming: auto/manual color adaptation to map tile background, icon shadows
6. Define rich marker callout visual: how callsign + telemetry badge renders as `PointAnnotation` icon

**Skill**: `/ui-designer component: MainScreen OSD layout — see plan app-structure.md Phase 2`
**Output**: Updated Design System tokens, layout spec, button column component designs

---

## Phase 3 — Implementation: Scaffold ✅ DONE (2026-04-03)

**Goal**: All packages and stub files created; app builds; navigation routes wired.

**Tasks (in order):**
1. Create domain model files: `UserProfileModel`, `MarkerModel`, `TrackModel`, `GroupModel`, `GroupRole`
2. Create domain repository interfaces: `UserRepository`, `MarkerRepository`, `GroupRepository`
3. Create stub data repositories (no logic, just `TODO()` implementations): `MqttMessageRepository`, `WifiMessageRepository`, `LocalMarkerRepository`, `MapTileRepository`
4. Create `local/db/` SQLDelight schema stubs
5. Create DI modules: `MapDataModule`, `UserDataModule` — wire stub implementations
6. Create `MainScreen.kt` with 2-layer `Box`: `MapLibreLayer` + `HudControlsLayer`
7. Create stub `MapLibreLayer.kt` — empty `MapLibreMap` composable placeholder
8. Create stub `HudControlsLayer.kt` — empty left+right column placeholders
9. Create stub screen composables as NavGraph destinations: `ChatScreen`, `SettingsScreen`,
   `NodeSettingsScreen`, `MarkerManagementScreen`, `NodeStatusDialog`
10. Create stub ViewModels for each feature screen (empty `UiState`, no logic)
11. Update `NavGraph` to route to `MainScreen` as default destination
12. Update `PresentationModule` / `AppModule` with new ViewModel registrations
13. Verify: app builds and launches to an empty landscape map screen

**Skill**: Direct coding (use EnterPlanMode before starting)
**Output**: Buildable scaffold committed

---

## Phase 4 — Testing: Build + Navigation Smoke Test ✅ DONE (2026-04-03)

**Goal**: Verify scaffold is coherent.

**Tasks:**
1. `./gradlew assembleDebug` — must succeed with no errors
2. Launch on device/emulator — confirm MainScreen is default, landscape-locked
3. Confirm `meshtest` screen still reachable (debug route)
4. Check DI graph loads without errors at startup (Koin validation)

**Skill**: Direct (Bash + device)
**Output**: Green build, no crash on launch

---

## Phase 5 — Integration Review

**Goal**: Architecture boundaries are clean before feature work begins.

**Tasks:**
1. Confirm no presentation layer references data layer directly
2. Confirm all repository interfaces are in `domain/`, implementations in `data/`
3. Confirm `meshtest` is isolated and does not leak into new feature modules
4. Confirm ViewModel dependencies come only through use cases or repository interfaces

**Skill**: `/architect review: presentation, domain, data layers after scaffold — see app-structure.md`
**Output**: Review report; any violations fixed before Phase 5 closes

---

## Phase 6 — Skill Update Review ✅ DONE (2026-04-03)

**Tasks per skill:**

- `/architect`: ✅ Added — 2-layer OSD composition pattern, NavGraph-as-modals convention, map feature staging table, meshtest removal policy + capability checklist, transport repository abstraction contract (`MessageRepository`, `NodeRepository`, `ChannelRepository`). Added 4 new anti-pattern rows.
- `/ui-designer`: ✅ Updated — Navigation & UX Patterns section: navigation style decided (NavGraph destinations only, no nav bar); main screen OSD layout flagged as PENDING (Phase 2 not yet executed), with explicit run instructions.
- `/icon-designer`: No changes needed — OSD button icons designed in a later feature iteration.
- `/planner`: No changes needed — no methodology gaps identified, no new skills created.

**Output**: `.claude/commands/architect.md`, `.claude/commands/ui-designer.md` updated.

---

## Testing Strategy

### Principle: TDD where there is logic, tests-after where there is glue

A class that contains `if`, `when`, data transformation, or calculation gets a test written
before its implementation. A class that is wiring (DI binding, UI composable, delegation to
an external API) gets a manual or integration test, or no test at all.

---

### Coverage map by layer

| Layer / component | Approach | Tools |
|---|---|---|
| **Domain use cases** (logic-bearing) | TDD | JUnit5 + Turbine (Flow) + MockK |
| **Domain use cases** (pass-through) | None — tested via integration | — |
| **Data mappers** (`*Mapper.kt`) | TDD — pure functions | JUnit5 |
| **Domain models / validators** (`PskValidator` etc.) | TDD | JUnit5 |
| **ViewModel** (non-trivial state logic) | TDD | JUnit5 + Turbine + MockK |
| **ViewModel** (simple state passthrough) | None | — |
| **Repository implementations** | Integration test (real SQLDelight in-memory DB) | JUnit5 + SQLDelight test driver |
| **BLE / Meshtastic transport** | Manual on device — mocking cost > benefit | — |
| **MapLibre rendering** | Manual on device | — |
| **Compose UI** | Manual smoke test on device | — |
| **NavGraph / navigation** | Manual smoke test | — |
| **DI graph** | Koin `checkModules()` at startup (existing) | Koin test module |

---

### When to write tests during development

**Rule**: tests are written in the same PR as the implementation, not in a later "testing sprint".

| Stage | Testing action |
|---|---|
| Phase 3 — scaffold | No tests: all stubs are `TODO()`, nothing to test |
| Phase 3 → feature impl: mapper written | Write unit test first (TDD) |
| Phase 3 → feature impl: use case with logic | Write unit test first (TDD) |
| Phase 3 → feature impl: ViewModel with non-trivial state | Write ViewModel test first (TDD) |
| Phase 3 → feature impl: repository impl | Write integration test after (real in-memory DB) |
| Phase 4 — smoke test | `./gradlew assembleDebug` + manual launch + Koin `checkModules()` |
| meshtest removal | All capabilities re-verified in new screens before deletion |

---

### Test file location convention

```
app/src/test/java/ru/tcynik/meshtactics/
    domain/mesh/util/PskValidatorTest.kt        ← unit
    domain/mesh/usecase/ObserveNodesUseCaseTest.kt
    data/mesh/mapper/NodeMapperTest.kt
    data/mesh/mapper/MessageMapperTest.kt
    presentation/feature/chat/ChatViewModelTest.kt

app/src/androidTest/java/ru/tcynik/meshtactics/
    data/local/MarkerRepositoryIntegrationTest.kt ← SQLDelight in-memory
```

---

### What "TDD for a use case" looks like in this project

1. Define the use case signature in `domain/`
2. Write test: given mock repository → expected Flow emissions
3. Run test → red
4. Implement use case → green
5. Refactor if needed → still green

MockK mocks repository interfaces from `domain/`. No real data layer involved.

---

## Coordination Map

```
Phase 0: [Research agent — map library + KMP readiness]
Phase 1: /architect feature: full package structure (see Phase 1 tasks)
Phase 2: /ui-designer component: MainScreen OSD layout
Phase 3: [direct coding — EnterPlanMode]
Phase 4: [direct — Bash build + device smoke test]
Phase 5: /architect review: all layers post-scaffold
Phase 6: [direct edit — .claude/commands/architect.md, ui-designer.md, planner.md]
```

---

## Open Questions

1. ~~**Map library**~~ — ✅ Resolved: MapLibre Native Android + `maplibre-compose`
2. ~~**KMP split timing**~~ — ✅ Resolved: deferred to post-Beta-1.0
3. ~~**`meshtest` fate**~~ — ✅ Resolved: kept behind `BuildConfig.DEBUG`, removed after all capabilities migrated (see Phase 1.5 checklist)
4. ~~**Modal dimming**~~ — ✅ Resolved: modals are NavGraph destinations, not overlays; map is fully replaced by modal screen
5. **Landscape lock**: Lock at `AndroidManifest` level (`screenOrientation="landscape"`) or via Compose `LocalConfiguration`?
6. **KMZ ground overlays**: Can MapLibre `ImageSource` render georeferenced raster images from KMZ? Needs prototype.
7. **Russian tile server compatibility**: nakarte.me / marshruty.ru require testing on Android HTTP stack.

---

## Change Log

- 2026-04-03: created
- 2026-04-03: Phase 0 completed — map library decision (MapLibre), KMP split deferred
- 2026-04-03: Phase 1.2 updated — OSD composition changed from 7 Compose layers to 3 (Option B)
- 2026-04-03: Phase 1.2/1.3 updated — modals replaced with NavGraph destinations; MainScreen reduced to 2 layers
- 2026-04-03: Phase 1.4–1.6 added — map feature staging, meshtest removal policy, transport abstraction
- 2026-04-03: Testing Strategy section added — TDD for logic, integration for repos, manual for BLE/UI
- 2026-04-03: Phase 3 completed — full scaffold committed, app builds clean
- 2026-04-03: Phase 4 completed — `assembleDebug` green; deprecated Koin ViewModel DSL imports fixed in PresentationModule.kt
- 2026-04-03: Phase 6 completed — architect.md updated (OSD pattern, NavGraph-as-modals, staging, meshtest policy, transport contract); ui-designer.md updated (nav style decided, OSD layout flagged pending)

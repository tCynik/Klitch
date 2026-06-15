# Plan: Network Screen (MeshTest → "Сеть")

**Date**: 2026-05-27
**Status**: Done

## Summary

Rename and restructure the MeshTest debug screen into a production "Сеть" screen.
Tabs are removed; Connection and Telemetry content is laid out vertically on one screen.
Config content moves to a separate Settings screen reachable via a gear button.
A "Использовать сеть" toggle at the top disables all node interaction (including BLE scanning)
and marks the HUD radio button as off (navigation to the screen preserved).
The old `meshtest` feature package is deleted.

## Scope

**In scope:**
- Rename feature package `meshtest` → `network`
- Remove tab row (TabRow component gone)
- Remove MessagesTab and GeoNodesTab entirely
- Lay out ConnectionTab + TelemetryTab content vertically (single scroll)
- Add `NetworkSettingsScreen` with ConfigTab content; navigate via gear icon in TopAppBar
- Add "Использовать сеть" toggle at top of NetworkScreen
- Domain + Data for persisting `networkEnabled` flag (follow AppSettings / `russhwolf.settings` pattern)
- HUD radio button: `selected = false` when network disabled (onClick keeps navigation)
- Remove `BuildConfig.DEBUG` gate from NavGraph for network routes
- Delete old `presentation/feature/meshtest/` package after migration

**Out of scope:**
- "Node" feature (ignored per requirements)
- MessagesTab content (deleted, not migrated)
- GeoNodesTab content (deleted, not migrated)
- Any visual redesign beyond layout restructuring
- BLE node markers on map (separate planned feature)

## Architecture Notes

### Settings persistence
Pattern already established in `AppSettings.kt` (multiplatform `russhwolf.settings`):
- New domain interface: `NetworkSettingsRepository` in `shared/domain/.../settings/repository/`
- New use cases: `ObserveNetworkEnabledUseCase`, `SetNetworkEnabledUseCase`
- Implementation: extend `AppSettings` — add `KEY_NETWORK_ENABLED` key + `_networkEnabled: MutableStateFlow<Boolean>`
- `AppSettings` implements `NetworkSettingsRepository` (same pattern as `MarkerSettingsRepository` etc.)

### HUD disabled state
`HudButtonSlot` already supports `selected: Boolean?`:
- `selected = null` → regular button (current state)
- `selected = false` → toggle-off visual (network disabled, but `enabled = true` keeps click active)
- MainViewModel observes `networkEnabled` from `ObserveNetworkEnabledUseCase`, feeds into HudConfig

### New routes
```kotlin
@Serializable data object Network : Route               // replaces MeshTest
@Serializable data object NetworkSettings : Route       // new — config screen
```
`Route.MeshTest` deleted. `Route.Nodes` and `Route.NodeDetail` kept (separate feature).

### New package structure
```
presentation/feature/network/
├── NetworkScreen.kt              # main screen (no tabs)
├── NetworkViewModel.kt           # pruned: no messages/geoNodes logic
├── NetworkUiState.kt
├── NetworkSettingsScreen.kt      # config content
├── NetworkSettingsViewModel.kt   # OR share state from NetworkViewModel (TBD in Phase 1)
├── components/
│   ├── MeshStatusBar.kt          # moved as-is
│   ├── CallsignGateDialog.kt     # moved as-is
│   ├── ConnectionContent.kt      # extracted from ConnectionTab.kt (no TabLayout wrapper)
│   ├── TelemetryContent.kt       # extracted from TelemetryTab.kt
│   └── NetworkSettingsContent.kt # extracted from ConfigTab.kt + LocationConfigCard.kt
└── state/
    ├── NetworkConnectionState.kt
    ├── NetworkTelemetryState.kt
    ├── NetworkSettingsState.kt   # was ConfigTabState
    ├── MeshConnectionStatusUi.kt # moved as-is
    └── models/
        ├── CallsignGateDialogState.kt  # moved as-is
        └── LocationConfigUi.kt        # moved as-is
```

Files deleted after migration:
- `meshtest/MeshTestScreen.kt`, `MeshTestViewModel.kt`, `MeshTestUiState.kt`
- `meshtest/state/MeshTestTab.kt`, `MessagesTabState.kt`, `GeoNodesTabState.kt`
- `meshtest/state/models/GeoNodeUi.kt`
- `meshtest/components/tabs/MessagesTab.kt`, `GeoNodesTab.kt`, `ConnectionTab.kt`, `TelemetryTab.kt`, `ConfigTab.kt`, `LocationConfigCard.kt`

### ViewModel split — DECIDED: separate ViewModels
`NetworkViewModel` — connection + telemetry + networkEnabled toggle.
`NetworkSettingsViewModel` — config (device config, channels, location config).
Rationale: NetworkScreen scope is expected to grow; keeping VMs independent avoids coupling future extensions.

### NetworkScreen layout

**When networkEnabled = true:**
```
TopAppBar: "Сеть"  [←]  [⚙]
─────────────────────────────
[ Использовать сеть ]  ◉ Вкл
─────────────────────────────
MeshStatusBar
─────────────────────────────
── Connection content ──
  (scan button, device list)
─────────────────────────────
── Telemetry content ──
  (device metrics card, node list)
```

**When networkEnabled = false:**
```
TopAppBar: "Сеть"  [←]  [⚙]
─────────────────────────────
[ Использовать сеть ]  ○ Выкл
─────────────────────────────

    Для подключения к сети
    Meshtastic включите сеть       ← centered, fills remaining space
```
No connection attempt, no scanning. VM guards all BLE actions on `networkEnabled`.

### networkEnabled defaults — DECIDED
- Default: `true` (network on at first launch)
- Persisted in `AppSettings` via `KEY_NETWORK_ENABLED`; restored across sessions

## Phase Plan

### Phase 1 — Architecture Design
**Goal**: Approved architecture — new domain interfaces, ViewModel split decision, route contract, state shapes.
**Tasks**:
1. Design `NetworkSettingsRepository` interface (domain)
2. Decide VM split (Option A vs B above)
3. Define `NetworkUiState` shape (connection + telemetry + settings substate + networkEnabled flag)
4. Confirm `NetworkSettingsScreen` shares `NetworkViewModel` (if Option A chosen)
5. Define `Route.Network` and `Route.NetworkSettings`
**Skill**: `/architect feature: Network screen refactor from MeshTest`
**Output**: Architecture decision document (can be appended here)

### Phase 2 — UI Design
**Goal**: Approved layout for NetworkScreen and NetworkSettingsScreen. No new icons needed (gear icon already exists in system).
**Tasks**:
1. Confirm toggle placement and disabled-overlay pattern with `/ui-designer`
2. Confirm `HudButtonSlot.selected = false` visual appearance is sufficient for "network off" state
3. Check if gear button style in TopAppBar follows existing conventions (e.g. chat screen)
**Skill**: `/ui-designer component:` if new patterns needed; otherwise direct decision
**Output**: Layout decisions documented here or in feature doc

### Phase 3 — Implementation
**Goal**: Working code, old meshtest package deleted, build passes.

**Order**:
1. **Domain** — `NetworkSettingsRepository` interface + `ObserveNetworkEnabledUseCase` + `SetNetworkEnabledUseCase`
2. **Data** — extend `AppSettings` with `KEY_NETWORK_ENABLED` (default `true`) + `_networkEnabled: MutableStateFlow<Boolean>`; add `NetworkSettingsRepository` to `AppSettings` implements list
3. **DI** — bind new use cases in Koin module
4. **Presentation — new package**:
   a. Create `presentation/feature/network/` structure
   b. Move `MeshStatusBar`, `CallsignGateDialog`, state models as-is (package rename only)
   c. Extract `ConnectionContent`, `TelemetryContent` from old tabs (no TabLayout wrapper)
   d. Build `NetworkViewModel` — remove messages/geoNodes logic from MeshTestViewModel; inject `ObserveNetworkEnabledUseCase` + `SetNetworkEnabledUseCase`; gate scan/connect on `networkEnabled`
   e. Build `NetworkUiState` with `networkEnabled: Boolean`
   f. Build `NetworkScreen` — toggle at top; when `networkEnabled = true`: StatusBar + ConnectionContent + TelemetryContent; when `false`: centered placeholder text
   g. Extract `NetworkSettingsContent` from old ConfigTab + LocationConfigCard
   h. Build `NetworkSettingsViewModel` — config/channels/locationConfig logic only
   i. Build `NetworkSettingsScreen`
5. **Navigation**:
   a. Add `Route.Network`, `Route.NetworkSettings` to `Route.kt`; remove `Route.MeshTest`
   b. In `NavGraph.kt`: remove DEBUG gate, add `network { }` and `networkSettings { }` destinations
   c. Update `HudNavCallbacks.kt`: `onRadioClick` → `Route.Network`
   d. Fix `Nodes` screen: replace `Route.MeshTest(nodeId)` → `Route.Network` (or remove if unused)
6. **HUD**:
   a. Inject `ObserveNetworkEnabledUseCase` in `MainViewModel` (or `HudConfigFactory`)
   b. Map `networkEnabled = false` → radio `HudButtonSlot` with `selected = false`
7. **Cleanup**: Delete entire `presentation/feature/meshtest/` directory

**Skill**: Direct coding (EnterPlanMode before starting)
**After**: run `/simplify` on changed files
**Output**: Buildable, working code

### Phase 4 — Testing
**Goal**: Feature verified.
**Tasks**:
1. Unit: `ObserveNetworkEnabledUseCase` / `SetNetworkEnabledUseCase` — Flow emission on toggle
2. Unit: `NetworkViewModel` — scan/connect blocked when `networkEnabled = false`
3. Manual smoke test on device: toggle off → scan disabled; toggle on → scan works; HUD button reflects state
**Skill**: Direct coding
**Output**: Passing unit tests, manual sign-off

### Phase 5 — Integration Review
**Goal**: No Clean Architecture violations.
**Tasks**:
1. Confirm `NetworkViewModel` doesn't import data-layer classes
2. Confirm `AppSettings` only implements domain repository interfaces (no presentation imports)
3. Check `NavGraph.kt` for any remaining meshtest references
**Skill**: `/architect review:` changed files
**Output**: Review clean

### Phase 6 — Skill Update Review
**Goal**: Skills reflect new patterns.
- `/architect`: document pattern of single ViewModel shared across two destinations (Network + NetworkSettings), if Option A chosen
- `/ui-designer`: document toggle + disabled-overlay pattern if new; document gear button in TopAppBar
- `/icon-designer`: no changes expected
- `/planner`: no methodology gap identified
**Skill**: Direct edit of `.claude/commands/`
**Output**: Updated skills or "no changes needed" per skill

### Phase 6b — Docs & Memory Update
**Goal**: Project metadata current.
**Tasks**:
1. Update `CLAUDE.md` status table: MeshTest → "Сеть" / Network feature; add `NetworkSettings` screen entry
2. Create `.claude/docs/network-screen.md`
3. Move this plan → `.claude/archive/network-screen.md`; verify `ls .claude/plans/`
4. Update memory: `project_spec.md` (feature list)
**Skill**: Direct edit
**Output**: CLAUDE.md accurate, doc created, plan archived, memory updated

### Phase 7 — Commit Preparation
**Goal**: Committed changeset after user approval.
**Tasks**:
1. `git status` — enumerate all changed files
2. Stage by name (never `git add -A`)
3. Draft commit message in Russian
4. Present staged files + message to user → wait for confirmation
5. After confirmation: `git commit`
**Skill**: Direct git
**Output**: Clean `git status`

## Coordination Map

```
Phase 1: /architect feature: Network screen refactor
Phase 2: /ui-designer (if toggle/overlay pattern not established) → direct decision otherwise
Phase 3: [direct coding — EnterPlanMode] → /simplify
Phase 4: [direct coding — tests] → manual device smoke test
Phase 5: /architect review: network package + NavGraph + AppSettings
Phase 6: [skill update review — architect, ui-designer, icon-designer, planner]
Phase 6b: [docs & memory — CLAUDE.md, .claude/docs/network-screen.md, archive plan, memory/]
Phase 7: [stage by name] → [propose commit] → [wait confirmation] → git commit
```

## Resolved Decisions

| Question | Decision |
|---|---|
| VM split | Separate: `NetworkViewModel` + `NetworkSettingsViewModel` |
| networkEnabled default | `true` (network on at first launch) |
| networkEnabled persistence | `AppSettings` / `russhwolf.settings`, key `network_enabled` |
| Disabled state UI | Empty screen body + centered text: "Для подключения к сети Meshtastic включите сеть" |

## Open Questions

1. **Telemetry scroll**: Combined Connection + Telemetry content can be long. `LazyColumn` for whole screen or `Column` inside `VerticalScroll`? Resolve in Phase 3 (LazyColumn preferred, but device list inside is also lazy — no nested lazy columns allowed).
2. **`Route.MeshTest` callers**: Nodes screen calls `Route.MeshTest(nodeId)` — decide: remove the nodeId navigation (Network screen has no node-detail view) or keep as dead stub. Likely remove.

## Change Log
- 2026-05-27: created
- 2026-05-27: decisions recorded — VM split, networkEnabled default/persistence, disabled-state UI
- 2026-05-27: implemented — meshtest removed, Network + NetworkSettings screens, HUD integration

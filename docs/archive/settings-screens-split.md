# Plan: Settings Screens Split

**Date**: 2026-05-16
**Status**: Approved

## Summary

The current `SettingsScreen` with four tabs (Map, Main, Screen, User) is replaced by four independent
screens, each accessible directly from the HUD menu drawer. This removes the tab paradigm entirely —
each settings domain becomes a first-class navigation destination. The existing `SettingsScreen.kt` is
deleted once all four screens are live and wired into the drawer.

## Scope

**In scope:**
- Create 4 new screen packages under `presentation/feature/settings/`
- Migrate tab content into standalone screens (ViewModels stay, content moves)
- Add Exit button to `MainSettingsScreen`
- Extend `HudNavCallbacks` with 4 new callbacks; update `buildMenuDrawerUiState()` accordingly
- Add 4 NavGraph routes; remove old `Route.Settings`
- Delete `SettingsScreen.kt`, `SettingsTab.kt`; clean up `SettingsUiState.selectedTab` + `SettingsViewModel.onTabSelected`
- Document changes in `.claude/docs/hud-menu-drawer.md`

**Out of scope:**
- Any new settings content / functionality
- Changes to `SettingsViewModel` or `UserSettingsViewModel` logic
- Landscape HUD (drawer is portrait-only, unchanged)

## Architecture Notes

- `SettingsViewModel` owns Map + Display logic and is injected via `koinViewModel()` in both new screens — no splitting needed.
- `UserSettingsViewModel` moves as-is into `UserSettingsScreen`.
- `HudNavCallbacks` gains 4 nav callbacks + 1 system callback (`onExitApp`); `onSettingsClick` removed in Phase 3.
- `DrawerMenuItem` list order: Radio → Main → Map → Screen → User (Settings item removed in Phase 3).
- Icon mapping: Main → `ic_settings`, Map → `ic_maps`, Screen → `ic_night`, User → `ic_man`.

### Dual-Source Drawer Assembly (canonical pattern, established here)

`buildMenuDrawerUiState()` assembles lambdas from two sources:

| Source | What goes here | Examples |
|---|---|---|
| `HudNavCallbacks` (from NavGraph) | Actions ViewModel cannot execute — need `NavController` or Android `Context` | navigate, exitApp |
| Direct ViewModel methods | Actions ViewModel executes itself | disconnect, SOS toggle |

`HudNavCallbacks` = "external callbacks" only. NavGraph constructs them with access to `navController` and `LocalContext`. ViewModel stays context-free.

`exitApp` → `HudNavCallbacks.onExitApp: () -> Unit`, assembled in NavGraph:
```kotlin
onExitApp = {
    context.stopService(GpsService.createIntent(context))
    (context as? Activity)?.finishAndRemoveTask()
}
```

`MainSettingsScreen` receives `onExitApp: () -> Unit` as a plain parameter — no `LocalContext` inside the screen composable.

## Phase Plan

### Phase 1 — Create 4 new screens (SettingsScreen stays, compilable)

**Goal**: four new screen files exist; all content migrated; SettingsScreen still compiles and routes still work.

**Tasks:**

1. Create `presentation/feature/settings/main/MainSettingsScreen.kt`
   - `Scaffold` with `TopAppBar` (back arrow + Exit button with `PowerSettingsNew` icon)
   - Body: placeholder `Box { Text("Главная — TODO") }`
   - Params: `onNavigateBack: () -> Unit`, `onExitApp: () -> Unit`
   - `onExitApp` is a plain callback — no `LocalContext` inside the screen; lambda is constructed in NavGraph

2. Create `presentation/feature/settings/map/MapSettingsScreen.kt`
   - Move `MapTabContent`, `TileCacheModeSelector`, `MapItemRow` from `SettingsScreen.kt` into this file (private composables)
   - Move `TileCacheMode.labelRes()` / `descRes()` extensions here too
   - Screen accepts `onNavigateBack`, injects `SettingsViewModel` via `koinViewModel()`
   - Delete confirm `AlertDialog` and file picker launcher move here
   - `Scaffold` with `TopAppBar` (back arrow only, no Exit)

3. Create `presentation/feature/settings/display/DisplaySettingsScreen.kt`
   - Move `ScreenTabContent` from `SettingsScreen.kt` into this file (private composable)
   - Injects `SettingsViewModel` via `koinViewModel()`
   - `Scaffold` with `TopAppBar` (back arrow only, no Exit)
   - Snackbar for save confirmation stays here

4. Create `presentation/feature/settings/user/UserSettingsScreen.kt`
   - Move `BackHandler(enabled = userState.hasUnsavedUserChanges)` here
   - Move `LeaveSettingsDialog` conditional here
   - Move `LaunchedEffect(Unit) { userViewModel.navigateBack.collect { onNavigateBack() } }` here
   - Move `UserTabContent()` call here
   - Injects `UserSettingsViewModel` via `koinViewModel()`
   - `Scaffold` with `TopAppBar` (back arrow triggering `userViewModel.onNavigateBackRequested()`)
   - Move `UserTabContent.kt` file to `presentation/feature/settings/user/` sub-package

5. After all 4 screens compile: strip the migrated private composables from `SettingsScreen.kt`
   (keep stubs just enough for it to still compile — or leave the dead code for Phase 3 deletion)

**Output**: 4 new screen files, project compiles, SettingsScreen route still works.

---

### Phase 2 — NavGraph routes + HUD drawer items

**Goal**: all 4 new screens are reachable from the HUD drawer; old Settings route also still works.

**Tasks:**

1. Add 4 routes to `navigation/Route.kt`:
   ```
   @Serializable data object MainSettings : Route()
   @Serializable data object MapSettings : Route()
   @Serializable data object DisplaySettings : Route()
   @Serializable data object UserSettings : Route()
   ```

2. Add 4 `composable<Route.*>` entries to `NavGraph.kt`:
   - Each wires `onNavigateBack = { navController.popBackStack() }`
   - `MainSettingsScreen` also gets `onExitApp = callbacks.onExitApp` (passed from `HudNavCallbacks`, constructed in `LaunchedEffect`)

3. Add 5 callbacks to `HudNavCallbacks` (keep `onSettingsClick` for now):
   ```kotlin
   // navigation
   onMainSettingsClick: () -> Unit = {},
   onMapSettingsClick: () -> Unit = {},
   onDisplaySettingsClick: () -> Unit = {},
   onUserSettingsClick: () -> Unit = {},
   // system (Context/Activity needed — ViewModel is context-free)
   onExitApp: () -> Unit = {},
   ```

4. Wire all callbacks in `NavGraph.kt` `LaunchedEffect`:
   ```kotlin
   onMainSettingsClick    = { navController.navigate(Route.MainSettings) },
   onMapSettingsClick     = { navController.navigate(Route.MapSettings) },
   onDisplaySettingsClick = { navController.navigate(Route.DisplaySettings) },
   onUserSettingsClick    = { navController.navigate(Route.UserSettings) },
   onExitApp              = {
       context.stopService(GpsService.createIntent(context))
       (context as? Activity)?.finishAndRemoveTask()
   },
   ```

5. Update `MainViewModel.buildMenuDrawerUiState()` — insert 4 new drawer items after Radio:
   ```
   DrawerMenuItem(R.drawable.ic_settings, "Главная",   callbacks.onMainSettingsClick)
   DrawerMenuItem(R.drawable.ic_maps,     "Карта",     callbacks.onMapSettingsClick)
   DrawerMenuItem(R.drawable.ic_night,    "Экран",     callbacks.onDisplaySettingsClick)
   DrawerMenuItem(R.drawable.ic_man,      "Пользователь", callbacks.onUserSettingsClick)
   ```
   Keep existing Settings item for now.

**Output**: all 4 screens reachable from HUD drawer; old Settings entry still works; project compiles.

---

### Phase 3 — Remove SettingsScreen and old Settings route

**Goal**: `SettingsScreen.kt` deleted; old `Route.Settings` gone; drawer has exactly Radio + 4 new items.

**Tasks:**

1. Delete `presentation/feature/settings/SettingsScreen.kt`
2. Delete `presentation/feature/settings/SettingsTab.kt`
3. Remove `Route.Settings` from `Route.kt`
4. Remove `composable<Route.Settings>` block from `NavGraph.kt`
5. Remove `onSettingsClick` from `HudNavCallbacks`
6. Remove `onSettingsClick` wiring from `NavGraph.kt` `LaunchedEffect`
7. Remove Settings `DrawerMenuItem` from `MainViewModel.buildMenuDrawerUiState()`
8. Clean up `SettingsUiState`: remove `selectedTab: SettingsTab` field
9. Clean up `SettingsViewModel`: remove `onTabSelected()` method

**Output**: clean compile, no dead code referencing old SettingsScreen or Settings tab model.

---

### Phase 4 — Documentation update

**Goal**: `.claude/docs/hud-menu-drawer.md` reflects the new 5-item drawer.

**Tasks:**

1. Update **Key Files** table — add entries for 4 new screen files
2. Update **Visual Specs** — update drawer items list (Radio + 4 settings screens, no old Settings)
3. Add **Scope Decisions** entry: "Settings screen replaced by 4 individual screens accessible from drawer"
4. Update CLAUDE.md status table if needed

**Output**: documentation accurate.

---

### Phase 5 — Commit

**Goal**: clean single commit (or one per phase if preferred) after user confirmation.

- Run `git status`, enumerate changed files by name
- Propose commit message in Russian
- Wait for user confirmation before committing

## Coordination Map

```
Phase 1: [direct coding] — 4 new screens, content migration
Phase 2: [direct coding] — NavGraph routes, HudNavCallbacks (+onExitApp), drawer items
Phase 3: [direct coding] — delete SettingsScreen, cleanup dead code
Phase 4: [direct edit] — .claude/docs/hud-menu-drawer.md (+ Dual-Source pattern), CLAUDE.md
Phase 5: [git stage by name] → [propose commit] → [wait for confirmation] → git commit
```

`/architect` consulted for action routing pattern — decision captured in Architecture Notes above.
No `/ui-designer` or `/tester` involvement needed.

## Open Questions

1. **MainSettingsScreen content** — "Главная" tab is currently TODO. After migration it will still be a
   placeholder (Exit button + empty body). Future content for this screen is deferred — resolved: acceptable.
2. **`UserTabContent.kt` move** — only caller is `SettingsScreen.kt:195` (same-package, no explicit import).
   Move to `settings/user/` is safe — package declaration update only. Resolved: safe to proceed.
3. **Exit button routing** — resolved: `onExitApp` goes into `HudNavCallbacks`, assembled in NavGraph
   via `LocalContext`. `MainSettingsScreen` receives it as a plain `() -> Unit` parameter.

## Change Log

- 2026-05-16: created
- 2026-05-16: added Dual-Source Drawer Assembly pattern (architect consultation); resolved all open questions; updated Phase 2 with `onExitApp` in `HudNavCallbacks`

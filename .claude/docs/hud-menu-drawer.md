# HUD Menu Drawer

**Status**: Done (2026-05-16)  
**Branch**: hud_refactor

## Overview

A slide-out navigation drawer that appears from the left edge of the screen, overlaying the HUD. Triggered by a hamburger button at the top of the HUD left column (portrait orientation only). Items: Radio + 4 settings screens (Главная, Карта, Экран, Пользователь). Designed to grow: new items can be appended to `DrawerMenuItem` list without restructuring.

## Key Files

| File | Role |
|---|---|
| `presentation/feature/main/osd/models/DrawerMenuItem.kt` | Drawer-specific item model (iconRes, label, onClick, enabled) |
| `presentation/feature/main/osd/models/MenuDrawerUiState.kt` | State data class (lambda-containing → separate StateFlow) |
| `presentation/feature/main/osd/layouts/MenuDrawerItem.kt` | Single drawer row: Icon(50dp) + label (bodyMedium) |
| `presentation/feature/main/osd/MenuDrawer.kt` | Animated overlay composable (scrim fade + panel slide) |
| `presentation/feature/main/MainUiState.kt` | Added `menuDrawerOpen: Boolean = false` |
| `presentation/feature/main/MainViewModel.kt` | `menuDrawerUiState: StateFlow<MenuDrawerUiState>`, `toggleMenuDrawer()`, `buildMenuDrawerUiState()` |
| `presentation/feature/main/osd/models/HudUiState.kt` | Added `menuDrawer: HudRowConfig` |
| `presentation/feature/main/osd/HudPortraitControlsLayer.kt` | Left column restructured to `SpaceBetween` with hamburger at top |
| `presentation/feature/main/MainScreen.kt` | Accepts `menuDrawerUiState`, renders `MenuDrawer` as conditional overlay (portrait only) |
| `navigation/NavGraph.kt` | Collects `menuDrawerUiState`, passes to `MainScreen`; wires all nav callbacks incl. `onExitApp` |
| `res/drawable/ic_menu.xml` | Hamburger icon (3-line, MeshIconButton style) |
| `presentation/feature/settings/main/MainSettingsScreen.kt` | Экран "Главная" — placeholder + Exit button |
| `presentation/feature/settings/map/MapSettingsScreen.kt` | Экран "Карта" — KMZ/KML оверлеи + tile cache |
| `presentation/feature/settings/display/DisplaySettingsScreen.kt` | Экран "Экран" — размер маркера |
| `presentation/feature/settings/user/UserSettingsScreen.kt` | Экран "Пользователь" — профиль, контуры, SOS |
| `presentation/feature/settings/user/UserTabContent.kt` | Содержимое экрана пользователя (перенесено из `settings/`) |

## Architecture Decisions

### State shape
`MenuDrawerUiState` contains lambda fields (`onDismiss`, `DrawerMenuItem.onClick`) → must live in its own `StateFlow`, never inside `MainUiState`. Built via `combine(_mainUiState, _navCallbacksFlow)` in `MainViewModel`. Pattern documented in `/architect` — see "Lambda-Containing UiState Pattern".

### Drawer model decoupling
Drawer items use their own `DrawerMenuItem` model — decoupled from `HudButtonSlot` by design. Drawer and HUD are independent UI zones; the earlier coupling to `HudButtonSlot` was an implementation artefact. `MenuDrawerUiState` holds `items: List<DrawerMenuItem>` instead of named `radio`/`settings` fields, allowing extensibility without ViewModel changes.

### Drawer as conditional overlay
`MenuDrawer` is a **conditional overlay composable** inside the `MainScreen` Box, not a new architectural layer. Pattern documented in `/architect` — see "Main Screen: 2-Layer OSD Composition + Conditional Overlays".

### Drawer close on navigation
On item tap: nav callback fires, then `toggleMenuDrawer()` closes the drawer immediately before the user leaves `MainScreen`. The user returns to a closed drawer. No lifecycle observer needed — action-driven close is synchronous.

### Dismiss mechanisms
- Outside tap → scrim `clickable(indication = null)` → `state.onDismiss`
- Back press → `BackHandler(enabled = state.isOpen)` → `state.onDismiss`

## Visual Specs

| Property | Value |
|---|---|
| Drawer width | 200dp |
| Scrim | `Color.Black.copy(alpha = 0.4f)` |
| Panel background | `MaterialTheme.colorScheme.surface` |
| Scrim animation | `fadeIn`/`fadeOut`, `tween(200)` |
| Panel animation | `slideInHorizontally`/`slideOutHorizontally` from left, `tween(250)` |
| Item layout | `Row { Icon(50dp) + Spacer(16dp) + Text(bodyMedium) }`, padding `horizontal=16dp, vertical=12dp` |
| Item icon tint | `onSurface`; disabled → `onSurface.copy(alpha=0.38f)` |
| Item spacing | `Spacer(height = 10.dp)` between items |
| Orientation | Portrait only; landscape is a TODO in `MainScreen` |

## Dual-Source Drawer Assembly

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

## Drawer Items (current)

| Order | Icon | Label | Destination |
|---|---|---|---|
| 1 | `ic_radio` | радио | `Route.MeshTest` |
| 2 | `ic_settings` | Главная | `Route.MainSettings` |
| 3 | `ic_maps` | Карта | `Route.MapSettings` |
| 4 | `ic_night` | Экран | `Route.DisplaySettings` |
| 5 | `ic_man` | Пользователь | `Route.UserSettings` |

## Scope Decisions

- Landscape HUD: out of scope (drawer is portrait-only in this phase)
- Gesture-based open (swipe from left edge): out of scope
- Drawer header / title label: out of scope
- Persistence of drawer state across sessions: out of scope (always closed on restart)
- Status-driven icon tinting: out of scope for drawer (icons always `onSurface`)
- `settings` button removed from HUD right column — navigation to settings is now exclusively via the drawer
- `SettingsScreen` with tabs replaced by 4 independent screens accessible directly from the drawer; `SettingsTab` enum deleted; `selectedTab` field removed from `SettingsUiState`

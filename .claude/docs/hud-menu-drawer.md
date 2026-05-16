# HUD Menu Drawer

**Status**: Done (2026-05-16)  
**Branch**: hud_refactor

## Overview

A slide-out navigation drawer that appears from the left edge of the screen, overlaying the HUD. Triggered by a hamburger button at the top of the HUD left column (portrait orientation only). Initial items: Radio and Settings — reusing existing `HudNavCallbacks`. Designed to grow: new items can be appended without restructuring.

## Key Files

| File | Role |
|---|---|
| `presentation/feature/main/osd/models/MenuDrawerUiState.kt` | State data class (lambda-containing → separate StateFlow) |
| `presentation/feature/main/osd/MenuDrawer.kt` | Animated overlay composable (scrim + panel) |
| `presentation/feature/main/MainUiState.kt` | Added `menuDrawerOpen: Boolean = false` |
| `presentation/feature/main/MainViewModel.kt` | `menuDrawerUiState: StateFlow<MenuDrawerUiState>`, `toggleMenuDrawer()`, `buildMenuDrawerUiState()` |
| `presentation/feature/main/osd/models/HudUiState.kt` | Added `menuDrawer: HudRowConfig` |
| `presentation/feature/main/osd/HudPortraitControlsLayer.kt` | Left column restructured to `SpaceBetween` with hamburger at top |
| `presentation/feature/main/MainScreen.kt` | Accepts `menuDrawerUiState`, renders `MenuDrawer` as conditional overlay (portrait only) |
| `navigation/NavGraph.kt` | Collects `menuDrawerUiState`, passes to `MainScreen` |
| `res/drawable/ic_menu.xml` | Hamburger icon (3-line, MeshIconButton style) |

## Architecture Decisions

### State shape
`MenuDrawerUiState` contains lambda fields (`onDismiss`, `HudButtonSlot.onClick`) → must live in its own `StateFlow`, never inside `MainUiState`. Built via `combine(_mainUiState, _navCallbacksFlow)` in `MainViewModel`. Pattern documented in `/architect` — see "Lambda-Containing UiState Pattern".

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
| Animation | `slideInHorizontally`/`slideOutHorizontally` from left, `tween(250)` |
| Inner padding | 8dp + `statusBarsPadding` + `navigationBarsPadding` |
| Item spacing | `Spacer(height = 10.dp)` between items |
| Orientation | Portrait only; landscape is a TODO in `MainScreen` |

## Scope Decisions

- Landscape HUD: out of scope (drawer is portrait-only in this phase)
- Gesture-based open (swipe from left edge): out of scope
- Drawer header / title label: out of scope
- Persistence of drawer state across sessions: out of scope (always closed on restart)

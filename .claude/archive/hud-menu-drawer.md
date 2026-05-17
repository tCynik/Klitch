# Plan: HUD Left-Edge Menu Drawer

**Date**: 2026-05-16
**Status**: Approved

## Summary

A slide-out menu drawer that appears from the left edge of the screen, overlaying the HUD. Triggered by a hamburger button added at the top of the HUD left column. Initial items: Radio and Settings — reusing the same navigation callbacks already wired into the HUD. Designed to grow: new items can be appended without restructuring. Follows the existing MeshIconButton visual language.

## Scope

- In scope: hamburger trigger button in HUD left column (portrait), `MenuDrawerUiState` StateFlow, animated `MenuDrawer` composable as a third layer in `MainScreen` Box, Radio + Settings items navigating via existing `HudNavCallbacks`, scrim to dismiss on outside tap, `ic_menu` hamburger icon
- Out of scope: landscape HUD (drawer is portrait-only in this phase), gesture-based open (swipe from left edge), drawer header/title label, persistence of drawer state across sessions

## Architecture Notes

**Affected layers**: presentation only (no domain / data changes)

**Key classes changed:**
- `MainUiState` — add `menuDrawerOpen: Boolean = false`
- `HudUiState` — add `menuDrawer: HudRowConfig` (the trigger button in the HUD left column)
- `HudPortraitControlsLayer` — left column changes from `Arrangement.Bottom` to `Arrangement.SpaceBetween` with `menuDrawer` at top
- `MainViewModel` — add `menuDrawerUiState: StateFlow<MenuDrawerUiState>`, add `toggleMenuDrawer()`, update `buildHudUiState()` to include hamburger config, add `buildMenuDrawerUiState()`
- `MainScreen` — accept `menuDrawerUiState`, add `MenuDrawer` as z3 layer in the `Box`
- `NavGraph` — collect `menuDrawerUiState` and pass it to `MainScreen`

**New classes:**
- `MenuDrawerUiState` — `presentation/feature/main/osd/models/MenuDrawerUiState.kt`; contains `isOpen: Boolean` + named `HudButtonSlot` fields per item; contains lambdas → separate StateFlow, never inside `MainUiState`
- `MenuDrawer` — `presentation/feature/main/osd/MenuDrawer.kt`; animated composable; accepts `MenuDrawerUiState`; renders items using existing `HudButtonSlotItem` or a thin wrapper

**State flow:**
```
MainUiState.menuDrawerOpen
     +
HudNavCallbacks
     ↓ combine()
MenuDrawerUiState (isOpen, radio.onClick = { nav.onRadioClick(); toggleMenuDrawer() }, ...)
     ↓
MenuDrawer composable (AnimatedVisibility + slideInHorizontally from left)
```

**HUD left column after change:**
```
Column(Arrangement.SpaceBetween) {
    HudRow(menuDrawer)   // top: hamburger trigger
    Column(Arrangement.Bottom) {
        HudRow(compass), HudRow(target), HudRow(markTool), HudRow(mapTools), HudRow(gps)
    }
}
```

**Drawer composable structure:**
```
Box(fillMaxSize) {
    // Scrim — semi-transparent, click to close
    Box(fillMaxSize, alpha=0.4, onClick = toggleMenuDrawer)
    // Drawer panel — left-anchored column
    Column(Alignment.Start) {
        HudButtonSlotItem(radio)
        HudButtonSlotItem(settings)
    }
}
```
Wrapped in `AnimatedVisibility(visible = state.isOpen, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it })`

## Phase Plan

### Phase 1 — Architecture Design
- **Goal**: confirm layer decomposition and state shape before coding
- **Tasks**: review `MenuDrawerUiState` structure, confirm `isOpen` placement in `MainUiState`, confirm `combine()` wiring pattern matches `hudUiState` convention, check if `HudButtonSlotItem` can be reused directly in `MenuDrawer` or needs a thin wrapper
- **Skill**: `/architect feature: hud-menu-drawer` — pass this plan as context
- **Output**: approved state shape and composable interface

### Phase 2 — Icon Design
- **Goal**: `ic_menu.xml` hamburger icon in MeshIconButton style
- **Tasks**: create hamburger (3-line) icon
- **Skill**: `/icon-designer create: ic_menu — hamburger / 3-line menu icon for HUD trigger button`
- **Output**: `app/src/main/res/drawable/ic_menu.xml`

### Phase 3 — Implementation
Order: models → ViewModel → composables → wiring

1. `MenuDrawerUiState.kt` — new data class in `osd/models/`
2. `MainUiState.kt` — add `menuDrawerOpen: Boolean = false`
3. `MainViewModel.kt`:
   - add `toggleMenuDrawer()`
   - add `menuDrawerUiState: StateFlow<MenuDrawerUiState>` (combine pattern)
   - add `buildMenuDrawerUiState(state, nav)` helper
   - update `buildHudUiState()` to add `menuDrawer` row with hamburger icon + `toggleMenuDrawer` onClick
4. `HudUiState.kt` — add `menuDrawer: HudRowConfig`
5. `HudPortraitControlsLayer.kt` — restructure left column to `SpaceBetween` with `menuDrawer` at top
6. `MenuDrawer.kt` — new composable with scrim + animated panel
7. `MainScreen.kt` — accept + render `MenuDrawer` as z3 layer in Box
8. `NavGraph.kt` — collect `menuDrawerUiState`, pass to `MainScreen`

After implementation: run `/simplify` on changed files.

**Logger checklist**: `MenuDrawer` composable is purely presentational — no logger needed. ViewModel changes are within existing `MainViewModel` which already has a logger.

### Phase 4 — Testing
- **Goal**: ViewModel state transitions verified
- **Tasks**:
  - Unit test: `toggleMenuDrawer()` flips `menuDrawerOpen` in `MainUiState`
  - Unit test: `menuDrawerUiState` emits updated `isOpen` after toggle
  - Unit test: drawer item `onClick` invokes nav callback AND closes drawer
- **Skill**: direct coding
- **Output**: passing tests in `MainViewModelTest`

### Phase 5 — Integration Review
- **Goal**: no Clean Architecture violations
- **Tasks**: confirm `MenuDrawerUiState` stays in presentation layer, confirm no domain/data imports in new composables
- **Skill**: `/architect review: presentation/feature/main/osd/models/MenuDrawerUiState.kt, presentation/feature/main/osd/MenuDrawer.kt`
- **Output**: review clean or violations fixed

### Phase 6 — Skill Update Review
- `/architect` — new pattern: `MenuDrawerUiState` as another example of lambda-containing state in separate StateFlow; update if not already documented
- `/ui-designer` — drawer overlay pattern (scrim + animated panel) as new UX component; document spacing and scrim opacity
- `/icon-designer` — no new icon style decisions (hamburger is standard)
- `/planner` — no methodology gaps found

### Phase 6b — Project Docs & Memory Update
- CLAUDE.md: add "HUD Menu Drawer" row to feature status table
- Create `.claude/docs/hud-menu-drawer.md`
- Archive this plan to `.claude/archive/hud-menu-drawer.md`, delete from `plans/`
- Update `memory/project_state.md` if feature completion changes project state

### Phase 7 — Commit Preparation
- Stage by name all changed files
- Proposed message: `feat(hud): выдвижная шторка меню по левому краю экрана`
- Wait for explicit user confirmation before committing

## Coordination Map

```
Phase 1: /architect feature: hud-menu-drawer → approved state shape
Phase 2: /icon-designer create: ic_menu
Phase 3: [direct coding — models → VM → composables → wiring] → /simplify
Phase 4: [direct coding — tests in MainViewModelTest]
Phase 5: /architect review: MenuDrawerUiState.kt, MenuDrawer.kt
Phase 6: [skill update review — /architect, /ui-designer]
Phase 6b: [CLAUDE.md, .claude/docs/hud-menu-drawer.md, archive plan, memory/]
Phase 7: [stage by name] → [propose commit] → [wait confirmation] → git commit
```

## Decisions

1. **Drawer close on item tap**: navigate with drawer open; on return to MainScreen the drawer must already be closed. Implementation: call `closeMenuDrawer()` on `Lifecycle.Event.ON_STOP` in MainScreen (same lifecycle observer that saves camera position). Drawer state resets before the user sees MainScreen again.
2. **Back press + outside tap**: both close the drawer. Back press handled via `BackHandler(enabled = isOpen)` in `MenuDrawer`. Outside tap handled by scrim click.
3. **Landscape**: drawer is portrait-only. TODO comment in `MainScreen` for landscape implementation.
4. **Drawer width**: fixed — exact value to be defined in Phase 2 (UI design), candidate: 200dp.
5. **Scrim opacity**: semi-transparent (~40% black `Color.Black.copy(alpha = 0.4f)`).
6. **Animation duration**: 250ms (`tween(250)`).

## Open Questions

None — all resolved above.

## Change Log

- 2026-05-16: created

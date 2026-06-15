# Plan: HUD Menu Drawer Refinement

**Date**: 2026-05-16
**Status**: Approved

## Summary

Four improvements to the existing MenuDrawer:
1. Introduce a dedicated `DrawerMenuItem` data model — decouple drawer items from `HudButtonSlot` entirely. The current coupling is an artefact of the initial implementation, not intentional design. Drawer and HUD are independent UI zones.
2. Strip status-driven coloring: drawer icons are always `onSurface`, never tinted by radio/GPS/node state.
3. Replace large-icon-only layout with a standard horizontal icon(50dp)+label row.
4. Decouple scrim animation from panel slide: scrim fades, panel slides.

## Scope

**In scope:**
- New `DrawerMenuItem.kt` data class — own model for drawer items (`iconRes`, `label`, `onClick`, `enabled`)
- `MenuDrawerUiState.kt` — replace `HudButtonSlot` fields with `DrawerMenuItem`
- `MainViewModel.kt` — update `buildMenuDrawerUiState()` to construct `DrawerMenuItem`
- `MainViewModelMenuDrawerTest.kt` — update tests to use `DrawerMenuItem`
- New `MenuDrawerItem.kt` composable — horizontal `Row { Icon(50dp) + Text(label) }`
- `MenuDrawer.kt` — split `AnimatedVisibility` into fade (scrim) + slide (panel)
- `MenuDrawer.kt` — replace `HudButtonSlotItem` with `MenuDrawerItem`
- `.claude/docs/hud-menu-drawer.md` — update to reflect new model and layout decisions

**Out of scope:**
- Changes to `HudButtonSlot` data class (untouched)
- Changes to `HudButtonSlotItem` composable (untouched)
- Expanding the set of drawer items beyond radio + settings (future work)

## Scope Decisions

| Item | Decision | Risk |
|---|---|---|
| Drawer model decoupling | New `DrawerMenuItem(iconRes, label, onClick, enabled)` in `models/`. `MenuDrawerUiState` uses `List<DrawerMenuItem>` instead of named `HudButtonSlot` fields. | Low-Medium — ViewModel and tests need updating; straightforward mechanical change |
| Icons without status | `MenuDrawerItem` composable takes only `DrawerMenuItem` — no `tintOverride`, `selected`, `infoBadge`. Color always `onSurface`; disabled → `onSurface.copy(alpha=0.38f)`. | Low — isolated composable |
| New item layout | `Row { Icon(50dp) + Spacer(16dp) + Text(bodyMedium) }` with `Modifier.clickable(enabled)` on the whole row. Padding: `horizontal=16dp, vertical=12dp`. | Low — new file |
| Scrim animation | Two `AnimatedVisibility` at the same level: (1) scrim — `fadeIn(tween(200))/fadeOut(tween(200))`; (2) panel — `slideInHorizontally(tween(250))/slideOutHorizontally(tween(250))`. `BackHandler` and scrim `clickable` outside both animation blocks. | Medium — structural composable change; verify dismiss still works |

## Phase Plan

### Phase 3 — Implementation

**Step 1: Create `DrawerMenuItem.kt` data class**
- File: `presentation/feature/main/osd/models/DrawerMenuItem.kt`
- `data class DrawerMenuItem(@DrawableRes val iconRes: Int, val label: String, val onClick: () -> Unit, val enabled: Boolean = true)`

**Step 2: Update `MenuDrawerUiState.kt`**
- Replace `val radio: HudButtonSlot` and `val settings: HudButtonSlot` with `val items: List<DrawerMenuItem>`
- Remove `HudButtonSlot` import

**Step 3: Update `MainViewModel.buildMenuDrawerUiState()`**
- Construct `DrawerMenuItem` for radio and settings using the same nav callbacks as before
- Pass as `items = listOf(radioItem, settingsItem)`

**Step 4: Update `MainViewModelMenuDrawerTest.kt`**
- Replace `HudButtonSlot` assertions with `DrawerMenuItem` equivalents

**Step 5: Create `MenuDrawerItem.kt` composable**
- File: `presentation/feature/main/osd/layouts/MenuDrawerItem.kt`
- `@Composable fun MenuDrawerItem(item: DrawerMenuItem)`
- `Row` with `fillMaxWidth`, `clickable(enabled=item.enabled, onClick=item.onClick)`, `padding(horizontal=16.dp, vertical=12.dp)`
- `Icon(ImageVector.vectorResource(item.iconRes), size=50.dp, tint = if (item.enabled) onSurface else onSurface.copy(alpha=0.38f))`
- `Spacer(width=16.dp)`
- `Text(item.label, style=MaterialTheme.typography.bodyMedium)`

**Step 6: Refactor `MenuDrawer.kt`**
- Scrim: wrap in `AnimatedVisibility(state.isOpen, fadeIn(tween(200)), fadeOut(tween(200)))`
- Panel: wrap in `AnimatedVisibility(state.isOpen, slideInHorizontally(tween(250)), slideOutHorizontally(tween(250)))`
- `BackHandler(enabled=state.isOpen)` stays outside both blocks
- Scrim `clickable { state.onDismiss() }` inside the fade block
- Replace item rendering: `state.items.forEach { MenuDrawerItem(it) }` + `Spacer(10.dp)` between items

### Phase 6b — Docs Update

Update `.claude/docs/hud-menu-drawer.md`:
- Replace `HudButtonSlot` references with `DrawerMenuItem` in Key Files table
- Add Architecture Decision: "Drawer items use own `DrawerMenuItem` model — decoupled from `HudButtonSlot` by design"
- Add Visual Spec row for item layout (icon 50dp + label bodyMedium)
- Update scrim animation spec

### Phase 7 — Commit

- Stage: `DrawerMenuItem.kt`, `MenuDrawerUiState.kt`, `MainViewModel.kt`, `MainViewModelMenuDrawerTest.kt`, `MenuDrawerItem.kt`, `MenuDrawer.kt`, `hud-menu-drawer.md`
- Message: `feat(hud): отвязка модели меню от HUD, новый вид айтемов, анимация скрима`

## Coordination Map

```
Phase 3: direct coding — Steps 1→2→3→4 (model + ViewModel + tests), then Steps 5→6 (UI)
Phase 6b: update .claude/docs/hud-menu-drawer.md
Phase 7: stage by name → propose commit → wait for confirmation → git commit
```

## Open Questions

- `List<DrawerMenuItem>` vs named fields: `items: List<DrawerMenuItem>` chosen over `radio/settings` to make the model extensible without ViewModel changes when new items are added.
- Scrim fade duration: 200ms — adjust on device if feels too slow.

## Change Log

- 2026-05-16: created
- 2026-05-16: extended — added model decoupling (DrawerMenuItem), ViewModel and test updates; docs phase made explicit

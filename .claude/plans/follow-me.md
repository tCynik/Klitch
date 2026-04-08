# Plan: Follow Me

**Date**: 2026-04-09
**Status**: Approved

## Summary

"Follow Me" keeps the user's GPS marker centred on the map while the mode is active.
The user activates it via a temporary `ic_target` button in the left HUD column;
the mode auto-deactivates when the user manually scrolls or drags the map.
This is a pure-presentation feature — no domain or data layer changes are needed.

## Scope

- **In scope**: follow-me toggle button (left column), camera animation on GPS update, auto-deactivation on user scroll, button active/inactive visual states
- **Out of scope**: persistent follow-me preference, HUD design system (deferred), zoom-to-user logic, compass / heading lock

## Architecture Notes

All logic lives in the presentation layer:

| Layer | Change |
|---|---|
| `MainUiState` | + `isFollowMeActive: Boolean = false` |
| `MainViewModel` | + `onFollowMeToggle()`, + `onFollowMeDeactivated()` |
| `MainScreen` | GPS `LaunchedEffect` → animate camera; scroll detection; pass callbacks |
| `HudControlsLayer` | `FollowMeButton` composable in left column (ic_target, two visual states) |

**Scroll detection**: use `cameraState.moveReason: CameraMoveReason`.
`CameraMoveReason.GESTURE` = user interaction; `CameraMoveReason.PROGRAMMATIC` = our `animateTo`.
Deactivate follow-me only when `moveReason == GESTURE`. No extra flags needed.

## Phase Plan

### Phase 1 — Architecture (lightweight, no `/architect` call needed)

**Goal**: finalize the state shape and animation API before touching code  
**Tasks**:
- Confirm `CameraState.animate()` / `flyTo()` API available in the MapLibre Compose version in use
- Confirm `cameraState.isCameraMoving` is observable from `MainScreen` (already used for `SaveLastMapPosition`)
- Decide: scroll-detection flag lives in `MainScreen` as `remember { mutableStateOf(false) }` (not in ViewModel — it is transient animation state)

**Output**: this plan section (no separate file needed)

### Phase 2 — UI / Icon

**Goal**: `FollowMeButton` composable with two visual states (active / inactive)  
**Tasks**:
- `ic_target.xml` already exists — no icon creation needed
- Add `FollowMeButton` composable inside `HudControlsLayer.kt` (left column)
- Active state: tinted with accent color (or `LocalContentColor` inverted); inactive: default icon tint
- Keep styling temporary / minimal — Visual Language is deferred

**Skill**: direct coding  
**Output**: `HudControlsLayer.kt` updated

### Phase 3 — Implementation

**Goal**: working follow-me across all affected files  
**Tasks** (in order):

1. **`MainUiState.kt`** — add `isFollowMeActive: Boolean = false`
2. **`MainViewModel.kt`** — add `onFollowMeToggle()` and `onFollowMeDeactivated()`
3. **`HudControlsLayer.kt`** — add `FollowMeButton` to left column; wire `isFollowMeActive`, `onFollowMeClick` params
4. **`MainScreen.kt`**:
   - Add `isAnimatingToUser` ref (`remember { mutableStateOf(false) }`)
   - `LaunchedEffect(currentLocation, uiState.isFollowMeActive)` — if active and location non-null: `cameraState.animateTo(CameraPosition(target, zoom), 300.ms)`
   - Scroll detection: `LaunchedEffect(cameraState.moveReason)` — if `moveReason == GESTURE && isFollowMeActive` → `onFollowMeDeactivated()`
   - Pass `isFollowMeActive`, `onFollowMeToggle`, `onFollowMeDeactivated` down through `HudControlsLayer`

**Skill**: direct coding (EnterPlanMode before starting)  
**Output**: buildable code

### Phase 4 — Testing

**Goal**: unit coverage for toggle and deactivation  
**Tasks**:
- `MainViewModelTest`: toggle activates/deactivates, `onFollowMeDeactivated()` sets `isFollowMeActive = false`
- Manual smoke test on device: activate follow, walk (or simulate GPS), confirm camera follows; scroll map, confirm mode deactivates and button dims

**Skill**: direct coding  
**Output**: passing tests

### Phase 5 — Integration Review

**Goal**: no Clean Architecture violations  
**Tasks**: review that no domain/data classes were touched for presentation-only state; confirm scroll-detection flag is not in ViewModel  
**Skill**: self-review (no `/architect` call needed — scope is presentation only)  
**Output**: review sign-off in this file's change log

### Phase 6 — Skill Update Review

- `/architect` — no new patterns (presentation-only; existing ViewModel conventions apply)
- `/ui-designer` — no new Design System tokens (button is explicitly temporary)
- `/icon-designer` — `ic_target.xml` already exists; no style decisions made
- `/planner` — no methodology gap found
- **Conclusion**: no skill files need updating for this feature

### Phase 6b — Project Docs & Memory Update

- Update `CLAUDE.md` status table: add "Follow Me" row → `Done`
- Set this plan status to `Done`
- Update `memory/project_state.md`
- Token log: append to Change Log below

### Phase 7 — Commit Preparation

- Stage by name: `MainUiState.kt`, `MainViewModel.kt`, `MainScreen.kt`, `HudControlsLayer.kt`, `.claude/plans/follow-me.md`, `CLAUDE.md`, `memory/project_state.md`
- Proposed commit message: `feat(map): добавить режим слежения за позицией пользователя`
- Wait for user confirmation before committing

## Coordination Map

```
Phase 1: [review MapLibre CameraState API in existing code]
Phase 2: [direct coding — HudControlsLayer FollowMeButton]
Phase 3: [direct coding — MainUiState → MainViewModel → HudControlsLayer → MainScreen] → /simplify
Phase 4: [direct coding — MainViewModelTest] + manual smoke
Phase 5: [self-review]
Phase 6: [no skill updates needed]
Phase 6b: [CLAUDE.md + plan status + memory/project_state.md]
Phase 7: [stage by name] → [propose commit] → [wait for confirmation] → git commit
```

## Open Questions

~~1. `CameraState.animate()` API~~ **Resolved** — see `.claude/research/maplibre-compose-camera-animation.md`.
   API: `suspend fun CameraState.animateTo(finalPosition: CameraPosition, duration: Duration = 300.ms)`
   Scroll detection: `CameraState.moveReason: CameraMoveReason` (`GESTURE` | `PROGRAMMATIC`) — no `isAnimatingToUser` flag needed.

~~2. Animation smoothness~~ **Resolved** — use `animateTo` (animated). `LaunchedEffect` auto-cancels on re-trigger (new GPS fix cancels in-flight animation), which is acceptable at GPS update frequency.

3. **Initial activation behaviour** — Decision: button active immediately, camera moves on first valid GPS fix.

## Change Log

- 2026-04-09: created; open questions 1 & 2 resolved via maplibre-compose API research

# Plan: Universal Screen Orientation

**Date**: 2026-04-10
**Branch**: `universal_orientation`
**Status**: Planned

## Summary

Remove the landscape orientation lock. Add portrait orientation support with a separate temporary HUD.
The landscape HUD stays unchanged. The portrait HUD is a temporary implementation to be refined later.

---

## Design Principles

- **Single `HudConfig`** — the ViewModel builds one config used by both orientations (same buttons and actions).
- **Two separate composables** — `HudControlsLayer` (landscape, existing, untouched) and `HudPortraitControlsLayer` (portrait, new, temporary).
- **Selection in `MainScreen`** — via `LocalConfiguration.current.orientation`.
- **Activity is not recreated** — `configChanges` stays in the manifest; the ViewModel survives rotation.

---

## Portrait HUD — temporary layout

```
Portrait screen:
┌──────────────────────────┐
│ [□]              [□]     │
│ [□]   ··· map ···[□]     │
│ [□]              [□]     │
│ [□]              [□]     │
│ [□]              [□]     │
└──────────────────────────┘
  ↑                  ↑
config.left       config.right
(map tools)         (menu)
```

Buttons are placed on both sides — same structure as landscape. `HudControlsLayer` is taken as
the structural reference; the only difference is that info columns are not rendered.

- Info slots: hidden in portrait (temporary — to be designed later)
- Each button: `MeshIconButton` + label below (same as landscape)
- Insets: `padding(8.dp)` + `statusBarsPadding()` top, `navigationBarsPadding()` bottom

---

## Phase Plan

### Phase 0 — Manifest

**File**: `app/src/main/AndroidManifest.xml`

**Changes**:
1. Remove `android:screenOrientation="sensorLandscape"` from `<activity>`
2. Keep `android:configChanges="orientation|screenSize|screenLayout"` (Activity is not recreated)

**Result**: app rotates freely; ViewModel stays alive on rotation.

---

### Phase 1 — `HudPortraitControlsLayer`

**File**: `presentation/feature/main/osd/HudPortraitControlsLayer.kt` (new)

**Tasks**:
1. Composable `HudPortraitControlsLayer(config: HudConfig, modifier: Modifier = Modifier)`:
   - Root: `Row(SpaceBetween, fillMaxSize)` with `statusBarsPadding()` + `navigationBarsPadding()` + `padding(8.dp)`
   - Left: `Column` — iterate `config.left.rows`, render `HudButtonSlotItem` for each `row.button`
   - Right: `Column` — iterate `config.right.rows`, render `HudButtonSlotItem` for each `row.button`
   - Info slots are not rendered (reserved for future)

**Depends on**: `HudButtonSlotItem` (already exists)

---

### Phase 2 — HUD selection in `MainScreen`

**File**: `presentation/feature/main/MainScreen.kt`

**Tasks**:
1. Add imports for `LocalConfiguration` and `Configuration`
2. Derive `val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE`
3. Replace the `HudControlsLayer(...)` call with:
   ```kotlin
   if (isLandscape) {
       HudControlsLayer(config = hudConfig, modifier = Modifier.fillMaxSize())
   } else {
       HudPortraitControlsLayer(config = hudConfig, modifier = Modifier.fillMaxSize())
   }
   ```

**ViewModel / HudConfig changes**: none — one `hudConfig` is used for both orientations.

---

### Phase 3 — Commit

```
feat(orientation): portrait support — temporary HUD, landscape lock removed
```

---

## Open Questions

1. **Portrait HUD layout** — buttons left/right is temporary. A different arrangement is possible when the portrait HUD is properly designed.
2. **Insets in portrait** — `navigationBarsPadding()` is required at the bottom since the Android nav bar is typically at the bottom in portrait.
3. **`configChanges` and insets** — with `configChanges="orientation"` enabled, insets update without Activity recreation; Compose `WindowInsets` handles this correctly.

---

## Coordination Map

```
Phase 0: [Manifest]                  — independent
Phase 1: [HudPortraitControlsLayer]  — independent (uses existing HudButtonSlotItem)
Phase 2: [MainScreen routing]        — after Phase 1
Phase 3: [Commit]                    — after Phases 0–2
```

Phase 0 and Phase 1 can be done in parallel.

---

## Future (out of scope for this plan)

- Portrait HUD design — separate task after MVP
- Possible refactor: separate `hudConfig` instances for landscape/portrait if button sets diverge

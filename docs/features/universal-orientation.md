# Universal Screen Orientation

## What it does
Removes the landscape orientation lock. Portrait rotation is supported with a temporary HUD that shows buttons on both sides (no info columns). Landscape HUD is unchanged.

## Key classes
- `HudPortraitControlsLayer` — portrait HUD composable (buttons only, no info); `presentation/feature/main/osd/`
- `MainScreen` — selects `HudControlsLayer` vs `HudPortraitControlsLayer` via `LocalConfiguration.current.orientation`

## Non-obvious decisions
- **Single `HudConfig` for both orientations**: ViewModel builds one config; portrait composable just ignores info slots. No separate ViewModel state for orientation.
- **Activity is not recreated**: `android:configChanges="orientation|screenSize|screenLayout"` stays in the manifest. ViewModel survives rotation. Compose `WindowInsets` handles insets correctly without recreation.
- Portrait insets: `statusBarsPadding()` top + `navigationBarsPadding()` bottom (nav bar is at bottom in portrait).

## Known limitations / planned extensions
- Portrait HUD layout is temporary — button positioning to be redesigned as a separate feature after MVP
- Info slots are hidden in portrait (reserved for future design)
- May need separate `HudConfig` instances for landscape/portrait if button sets diverge

## Source
Plan: `.claude/archive/universal-orientation.md`

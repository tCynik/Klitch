# App Structure

## What it does
Defines the Clean Architecture package tree, OSD layer composition (MapLibre + HUD), and NavGraph-as-modals navigation pattern used throughout the app.

## Key classes
- `MainScreen` — root composable; `Box` with two layers: `MapLibreLayer` (z1) + `HudControlsLayer` (z2); presentation
- `MapLibreLayer` — owns ALL spatial content (tiles, markers, overlays, GPS arrow); presentation
- `HudControlsLayer` — button columns overlay; presentation
- `NavGraph` — single-graph navigation; modals are `composable()` / `dialog()` destinations, not Compose overlays; navigation
- Repository interfaces (`MessageRepository`, `NodeRepository`, `ChannelRepository`) — transport-agnostic domain contracts; domain

## Non-obvious decisions
- Modals are NavGraph destinations (full-screen takeover), NOT Compose overlay layers — NavGraph gives free back-stack, no custom overlay state machine needed
- MapLibre owns the coordinate system for all geo content; callouts that need rich Compose UI are rendered to `Bitmap` and used as `PointAnnotation` icons
- `meshtest` screen stays behind `BuildConfig.DEBUG` until all 5 capabilities (BLE, channel config, chat, telemetry, log) are migrated to proper feature screens

## Known limitations / planned extensions
- KMP split deferred to post-Beta-1.0 (MapLibre Native Android is Android-only)
- `meshtest` migration tracker: chat ☑, node_settings ☐, telemetry ☐, BLE ☐, packet log ☐

## Source
Plan: `.claude/archive/app-structure.md`

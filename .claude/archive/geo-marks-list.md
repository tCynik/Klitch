# Plan: Geo Marks List Screen

**Date**: 2026-05-21
**Status**: Done
**Doc**: `.claude/docs/geo-marks-list.md`

## Summary

A full-screen list of all geo marks (self-sent + received), accessible via a new drawer item. Each row shows: ShapeIcon (colored by mark color), name + type badge, TTL countdown, author ("Я" / short nodeId). Each row has a visibility checkbox (show/hide mark on the map — persisted in DB) and a three-dot overflow button (placeholder only; menu actions in a follow-up prompt). No filtering in this phase (deferred to follow-up prompt). Marks sorted by `createdAt` DESC.

## Scope

**In scope:**
- New NavGraph destination `Route.GeoMarksList`
- New drawer item #6 "Метки" (`ic_marks` icon)
- `GeoMarksListScreen` composable (portrait + landscape scaffold)
- `GeoMarksListViewModel` — observes marks, exposes list items, handles visibility toggle
- `GeoMarkListItemUiModel` — presentation data class for list rows
- `GeoMarkListItem` composable — ShapeIcon + name/type + TTL остаток + author + checkbox + `⋮` stub (empty lambda + TODO)
- `is_visible` column in `geo_mark` SQLDelight table (INTEGER NOT NULL DEFAULT 1)
- `ToggleGeoMarkVisibilityUseCase` in domain
- `GeoMarkRepository.toggleVisibility(id: String)` interface method + impl
- `MapLibreLayer` filters rendered marks to `isVisible = true`
- TTL countdown: formatted string ("12 мин.", "<1 мин.", "—") derived from `expiresAt - nowSeconds`. Rounded up (ceil). `nowSeconds` updated every 60s via ViewModel ticker

**Out of scope:**
- Three-dot menu actions (follow-up prompt) — `⋮` button rendered as placeholder
- Filtering / tabs (follow-up prompt) — TODO recorded
- "Show on map" action (navigate + center camera)
- Delete from list screen
- Edit from list screen

## Change Log

- 2026-05-21: created
- 2026-05-22: implemented; tests and docs added; archived

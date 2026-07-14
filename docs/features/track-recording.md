# Track Recording (GPS Breadcrumb)

## What it does
Records the user's GPS position as a breadcrumb trail (`RecordedTrack` + ordered `TrackPoint` rows) while the user is moving. Recording can be started, paused/resumed, renamed, recolored, stopped (with optional trim of static leading/trailing points), or discarded. Finished tracks appear in `GeoMarksListScreen` alongside geo marks and can be shown/hidden on the map or deleted.

Distinct from:
- `geo_mark` type `TRACK` — a mesh-shared drawn path (different table/domain)
- KMZ/KML overlay import (`KmlOverlayParser`) — static reference layers, unrelated to recorded tracks
- Track KML import/export (see [`track-import-export.md`](track-import-export.md)) — a separate feature layered on top of this one

## Key classes
- `RecordedTrack` — domain model (id, name, startedAt/finishedAt seconds, totalDistanceMeters, color, isVisible, hasTimestamps); `domain/track/model/`
- `TrackPoint` — single breadcrumb point (trackId, timestampMs, lat, lon, accuracy); `domain/track/model/`
- `TrackRecordingState` — sealed state (`Idle` / `Recording`); `domain/track/model/`
- `TrackRecordingRepository` / `RecordedTrackRepository` — two domain interfaces, one impl (`TrackRepositoryImpl`); `domain/track/repository/`, `data/track/repository/`
- Use cases: `StartTrackRecordingUseCase`, `PauseTrackRecordingUseCase`, `ResumeTrackRecordingUseCase`, `StopTrackRecordingUseCase`, `DiscardTrackRecordingUseCase`, `UpdateTrackRecordingNameUseCase`, `UpdateTrackRecordingColorUseCase`, `ObserveTrackRecordingStateUseCase`, `ObserveRecordedTracksUseCase`, `ObserveRecordedTrackPointsUseCase`, `ToggleRecordedTrackVisibilityUseCase`, `DeleteRecordedTracksUseCase`; `domain/track/usecase/`
- `TrackRecordingViewModel` — recording form, stop dialog, exit-on-stop flow; `presentation/feature/main/`
- `GeoMarksListViewModel` — lists finished tracks, visibility/delete/export/import; `presentation/feature/marks/`
- SQLDelight tables `recorded_track` (id, name, started_at, finished_at nullable, total_distance, color, is_visible, has_timestamps) and `recorded_track_point` (PK `track_id, timestamp`); `shared/.../data/local/RecordedTrack.sq`

## Non-obvious decisions
- **Two domain interfaces, one impl**: `TrackRecordingRepository` (recording control) and `RecordedTrackRepository` (list/query/import) are separate interfaces both implemented by `TrackRepositoryImpl` — recording ViewModel and list ViewModel each depend only on the interface they need.
- **`finishedAt == null` means still recording** — used throughout (export menu disabled, duration label shows "recording", map still tracks it live).
- **Movement trim on stop**: `stop(trimToMovement)` optionally deletes leading/trailing points below a 10 m movement threshold (`TRIM_MOVEMENT_THRESHOLD_METERS`). Tracks with zero net movement are always discarded regardless of the checkbox.
- **In-memory `lastRecordedPoint` for live distance**: distance accumulates incrementally in `addPoint()` via haversine from the previous point, not recomputed from scratch on every point — `computeTotalDistance()` (full recompute) is only used at `stop()`.
- **Unfinished-track restore on process restart**: `TrackRepositoryImpl.init` checks `selectUnfinished()` and rehydrates `TrackRecordingState.Recording` from DB if the app was killed mid-recording.
- **`has_timestamps` flag**: added for track import (see [`track-import-export.md`](track-import-export.md)) — all recorded (non-imported) tracks have this `= 1`; imported tracks lacking real per-point timestamps have it `= 0`, which suppresses duration/speed display but not distance.

## Known limitations / planned extensions
- No pause/resume across app-process death — only a single in-progress track is restored, and it resumes as "recording" (not "paused") on restart.

## Source
Pre-existing feature (retroactively documented, see `docs/plans/track-import-export.md`).

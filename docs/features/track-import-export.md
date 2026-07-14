# Track Import/Export (KML)

## What it does
Exports a finished `RecordedTrack` to a `.kml` file via SAF (`CreateDocument`), and imports a `.kml` file from another source as a new `RecordedTrack`, from `GeoMarksListScreen`. Export is triggered from the three-dot overflow menu on `RecordedTrackListItem` (disabled while the track is unfinished); import is triggered from a toolbar icon on `GeoMarksListScreen`.

Distinct from:
- `geo_mark` type `TRACK` — a mesh-shared drawn path (different table/domain)
- KMZ/KML **overlay** import (`KmlOverlayParser`, Map Settings) — static reference layers, unrelated to recorded tracks
- Track recording itself — see [`track-recording.md`](track-recording.md)

## Key classes
- `TrackKmlWriter` — serializes `RecordedTrack` + ordered `TrackPoint` list to a KML `<Placemark><LineString>` string; `data/track/kml/`
- `TrackKmlParser` — parses the first `<Placemark>` name + `<LineString><coordinates>` in a KML document into `ParsedTrack`; ignores any other Placemarks; `data/track/kml/`
- `ParsedTrack` — plain domain model (name, points as `List<Pair<lat, lon>>`); `domain/track/model/`
- `TrackFileRepository` / `TrackFileRepositoryImpl` — SAF read/write, domain sees only `String` URIs; `domain/track/repository/`, `data/track/repository/`
- `ExportTrackUseCase`, `ImportTrackUseCase` — plain `operator fun invoke`, one-shot suspend; `domain/track/usecase/`
- `RecordedTrackRepository.insertImported(name, points)` — inserts track + points with synthetic sequential timestamps; `has_timestamps = 0`
- UI: `DropdownMenu` on `RecordedTrackListItem` (Export, disabled when unfinished); import icon (`ic_track_import`) on `GeoMarksListScreen` toolbar; SAF launchers (`CreateDocument`/`OpenDocument`) via `rememberLauncherForActivityResult` in the Screen composable

## Non-obvious decisions
- **`has_timestamps` flag, not deleted timestamp column**: `recorded_track_point.timestamp` stays `NOT NULL` (it's part of the PK `(track_id, timestamp)`) — imported points get synthetic sequential timestamps (`nowSeconds*1000 + index`) purely to satisfy PK/ordering. The track-level `has_timestamps = false` is the actual signal that duration/speed cannot be derived; distance stays computable (spatial only, from haversine over lat/lon).
- **Parser only reads the first `<Placemark>/<LineString>`** — multi-Placemark KML files import just the first track; no `<gx:Track>/<when>` support, so imported tracks are always `has_timestamps = false`.
- **No color round-trip on import** — imported tracks get the app's default color assignment (`color = 0`); KML color styling is not read or written.
- **SAF URI crosses the domain boundary as `String`, never `Uri`** — `TrackFileRepository` (domain) takes `String`; only `TrackFileRepositoryImpl` (data) calls `Uri.parse()` / `ContentResolver`. This is the second precedent for this pattern after KMZ/KML overlay import — now documented as a canonical pattern in `/architect` (`SAF Import/Export Use Case Pattern`).
- **Export disabled (not hidden) for unfinished tracks** — `DropdownMenuItem(enabled = item.isFinished)`, resolving the plan's open question in favor of a visible-but-disabled affordance.
- **XML escaping is hand-rolled** (`escapeXml`/`unescapeXml` for `&`, `<`, `>`) rather than a full XML parser — sufficient for the narrow `<name>`/`<coordinates>` extraction this feature needs; regex-based parsing (`DOT_MATCHES_ALL`) trades general KML robustness for simplicity, matching the narrow scope (KML only, no GPX, no styling).

## Known limitations / planned extensions
- GPX format rejected — KML only, per product decision.
- Naming collisions on import are allowed (tracks are keyed by UUID; name is just a label) — no dedup.

## Source
Plan: `docs/archive/track-import-export.md`

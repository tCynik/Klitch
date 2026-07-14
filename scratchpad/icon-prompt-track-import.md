# Icon: Track Import (KML)

**Context**: `docs/plans/track-import-export.md` Phase 2 — import action on `GeoMarksListScreen`
toolbar. Export reuses existing overflow `⋮` menu item (text label "Export"), no new icon needed
there. This icon covers the **import** trigger only.

## Concept

Classic "import" pictogram: downward arrow feeding into an open tray. 2 strokes, unambiguous,
no clash with existing icons (`ic_track_record` = polyline+dots, `ic_file` = plain sheet, no
existing arrow/tray icon in `res/drawable`).

## Style check

- [x] All paths within `[12,68] × [12,68]`
- [x] No fills — stroke only
- [x] `strokeLineCap="round"`, `strokeLineJoin="round"`
- [x] `strokeWidth="3"`
- [x] No frame path (frame = `MeshIconButton`)
- [x] 2 paths only

## File: `ic_track_import.xml` → `app/src/main/res/drawable/`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="80dp"
    android:height="80dp"
    android:viewportWidth="80"
    android:viewportHeight="80">

    <!-- Arrow: stem (40,16)->(40,44) + arrowhead chevron, pointing down into tray -->
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FF000000"
        android:strokeWidth="3"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M 40,16 L 40,44 M 28,32 L 40,44 L 52,32" />

    <!-- Tray: open-top U, receives the arrow -->
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FF000000"
        android:strokeWidth="3"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M 16,48 L 16,62 L 64,62 L 64,48" />

</vector>
```

## Usage (Compose)

```kotlin
MeshIconButton(
    icon = ImageVector.vectorResource(R.drawable.ic_track_import),
    selected = null, // not a toggle
    onClick = { /* launch OpenDocument SAF picker */ },
)
```

## Status

Prompt/spec ready. Not yet written to `res/drawable/` — do that in Phase 2 of the plan when
wiring the actual UI.

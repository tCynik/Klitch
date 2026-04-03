package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// TODO: Replace placeholder with MapLibreMap { } when map feature is implemented.
// Planned content (all rendered as MapLibre layers):
//   Layer 1 — RasterLayer: XYZ tile source (getTileUrlTemplate from MapTileRepository)
//   Layer 2 — LineLayer: coordinate grid
//   Layer 3 — PolylineAnnotation / PolygonAnnotation: markers and tracks
//   Layer 4 — PointAnnotation: channel participant positions (telemetry)
//   Layer 5 — PointAnnotation: channel markers
// Rich callouts (callsign + telemetry badge) rendered as @Composable → Bitmap → PointAnnotation icon.
@Composable
fun MapLibreLayer(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFF2D4A2D)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Map placeholder",
            color = Color.White.copy(alpha = 0.4f),
        )
    }
}

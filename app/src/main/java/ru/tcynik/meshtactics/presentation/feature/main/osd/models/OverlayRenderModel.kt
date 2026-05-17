package ru.tcynik.meshtactics.presentation.feature.main.osd.models

import android.graphics.Bitmap

data class OverlayRenderModel(
    val id: String,
    val geoJson: String?,
    val groundOverlayBitmap: Bitmap?,
    val groundOverlayBounds: GroundOverlayBounds?,
)

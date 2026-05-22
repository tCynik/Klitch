package ru.tcynik.meshtactics.presentation.feature.marks.models

import ru.tcynik.meshtactics.domain.marker.model.GeoMarkShape
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.TrackEndType

data class GeoMarkListItemUiModel(
    val id: String,
    val colorArgb: Int,
    val shape: GeoMarkShape,
    val trackEndType: TrackEndType,
    val type: GeoMarkType,
    val name: String,
    val ttlLabel: String,
    val authorLabel: String,
    val deliveryState: GeoMarkDeliveryState,
    val isVisible: Boolean,
)

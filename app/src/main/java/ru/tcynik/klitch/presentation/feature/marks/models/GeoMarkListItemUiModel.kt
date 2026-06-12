package ru.tcynik.klitch.presentation.feature.marks.models

import ru.tcynik.klitch.domain.marker.model.GeoMarkShape
import ru.tcynik.klitch.domain.marker.model.GeoMarkType
import ru.tcynik.klitch.domain.marker.model.TrackEndType

data class GeoMarkListItemUiModel(
    val id: String,
    val colorArgb: Int,
    val shape: GeoMarkShape,
    val trackEndType: TrackEndType,
    val type: GeoMarkType,
    val name: String,
    val createdAtLabel: String,
    val ttlLabel: String,
    val authorLabel: String,
    val deliveryState: GeoMarkDeliveryState,
    val isVisible: Boolean,
)

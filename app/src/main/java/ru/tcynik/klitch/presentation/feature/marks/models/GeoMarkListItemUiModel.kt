package ru.tcynik.klitch.presentation.feature.marks.models

import ru.tcynik.klitch.domain.marker.model.GeoMarkShape
import ru.tcynik.klitch.domain.marker.model.GeoMarkType
import ru.tcynik.klitch.domain.marker.model.TrackEndType
import ru.tcynik.klitch.presentation.ui.UiText

data class GeoMarkListItemUiModel(
    val id: String,
    val colorArgb: Int,
    val shape: GeoMarkShape,
    val trackEndType: TrackEndType,
    val type: GeoMarkType,
    val name: String,
    val createdAtLabel: String,
    val ttlLabel: UiText,
    val authorLabel: UiText,
    val isSelf: Boolean,
    val deliveryState: GeoMarkDeliveryState,
    val isVisible: Boolean,
)

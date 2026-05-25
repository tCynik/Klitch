package ru.tcynik.meshtactics.presentation.feature.main

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkPreset
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkShape
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.TrackEndType
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarkAddressee

data class GeoMarksFormState(
    val isSheetVisible: Boolean = false,
    val isCollapsed: Boolean = false,
    val selectedType: GeoMarkType = GeoMarkType.POINT,
    val selectedColor: Int = 4,
    val selectedShape: GeoMarkShape = GeoMarkShape.CIRCLE,
    val selectedTrackEndType: TrackEndType = TrackEndType.NONE,
    val selectedTtlSeconds: Long = 900L,
    val pointMarkName: String = "точка",
    val trackMarkName: String = "Путь",
    val pointNameCounter: Int? = 1,
    val trackNameCounter: Int? = 1,
    val selectedContourId: String = "",
    val wasAddresseeExplicitlySelected: Boolean = false,
    val availableContours: ImmutableList<GeoMarkAddressee> = persistentListOf(),
    val savedPresets: ImmutableList<GeoMarkPreset> = persistentListOf(),
)

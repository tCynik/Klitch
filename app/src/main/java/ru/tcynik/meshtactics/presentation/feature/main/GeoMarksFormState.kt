package ru.tcynik.meshtactics.presentation.feature.main

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkPreset
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.TrackEndType
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarkAddressee

data class GeoMarksFormState(
    val isSheetVisible: Boolean = false,
    val selectedType: GeoMarkType = GeoMarkType.POINT,
    val selectedColor: Int = 0,
    val selectedTrackEndType: TrackEndType = TrackEndType.NONE,
    val selectedTtlSeconds: Long = 28800L,
    val markName: String = "",
    val nameCounter: Int = 1,
    val selectedContourId: String = "",
    val availableContours: ImmutableList<GeoMarkAddressee> = persistentListOf(),
    val savedPresets: ImmutableList<GeoMarkPreset> = persistentListOf(),
)

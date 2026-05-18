package ru.tcynik.meshtactics.presentation.feature.main.osd.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkPreset
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.model.TrackEndType

data class GeoMarksSheetUiState(
    val isVisible: Boolean = false,
    val markToolActive: Boolean = false,
    val selectedType: GeoMarkType = GeoMarkType.POINT,
    val selectedColor: Int = 0,
    val selectedTrackEndType: TrackEndType = TrackEndType.NONE,
    val selectedTtlSeconds: Long = 28800L,
    val markName: String = "",
    val nameCounter: Int = 1,
    val pendingPoints: ImmutableList<GeoPoint> = persistentListOf(),
    val availableContours: ImmutableList<GeoMarkAddressee> = persistentListOf(),
    val selectedContourId: String = "",
    val savedPresets: ImmutableList<GeoMarkPreset> = persistentListOf(),
    // Callbacks
    val onClose: () -> Unit = {},
    val onToggleMarkTool: () -> Unit = {},
    val onMarkTypeSelected: (GeoMarkType) -> Unit = {},
    val onColorSelected: (Int) -> Unit = {},
    val onTrackEndTypeSelected: (TrackEndType) -> Unit = {},
    val onTtlSelected: (Long) -> Unit = {},
    val onMarkNameChanged: (String) -> Unit = {},
    val onNameCounterChanged: (Int) -> Unit = {},
    val onAddresseeSelected: (String) -> Unit = {},
    val onApplyPreset: (GeoMarkPreset) -> Unit = {},
    val onSendPendingMark: () -> Unit = {},
    val onDeletePendingPoint: (Int) -> Unit = {},
)

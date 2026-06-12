package ru.tcynik.klitch.presentation.feature.marks.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class GeoMarksListUiState(
    val items: ImmutableList<GeoMarkListItemUiModel> = persistentListOf(),
    val hasMarks: Boolean = false,
    val deliveryFilters: ImmutableList<GeoMarkDeliveryFilterButtonUi> = persistentListOf(),
    /** Все метки текущего фильтра отмечены чекбоксом (видны на карте). */
    val allFilteredVisible: Boolean = false,
    /** Есть метки в текущем фильтре — кнопка «все/снять» активна. */
    val bulkVisibilityEnabled: Boolean = false,
    /** Есть отмеченные чекбоксом метки в текущем фильтре. */
    val deleteEnabled: Boolean = false,
    val deleteConfirm: GeoMarksDeleteConfirmUi? = null,
    val sendContourPicker: GeoMarksSendContourPickerUi? = null,
    /** Треки, видимые в списке (пусто, если фильтр выключен или треков нет). */
    val recordedTracks: ImmutableList<RecordedTrackListItemUiModel> = persistentListOf(),
    /** Состояние кнопки фильтра треков. */
    val tracksFilterStatus: GeoMarkDeliveryFilterStatus = GeoMarkDeliveryFilterStatus.INACTIVE,
)

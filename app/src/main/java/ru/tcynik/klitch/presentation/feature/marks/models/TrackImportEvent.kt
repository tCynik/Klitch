package ru.tcynik.klitch.presentation.feature.marks.models

sealed interface TrackImportEvent {
    data class Success(val trackName: String) : TrackImportEvent
    data object Failed : TrackImportEvent
}

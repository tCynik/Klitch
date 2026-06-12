package ru.tcynik.klitch.presentation.feature.main

import ru.tcynik.klitch.domain.track.model.TrackRecordingSettings

data class TrackRecordingFormState(
    val isSheetVisible: Boolean = false,
    val isCollapsed: Boolean = false,
    val settings: TrackRecordingSettings = TrackRecordingSettings(),
)

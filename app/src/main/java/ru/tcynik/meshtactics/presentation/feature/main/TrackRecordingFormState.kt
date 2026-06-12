package ru.tcynik.meshtactics.presentation.feature.main

import ru.tcynik.meshtactics.domain.track.model.TrackRecordingSettings

data class TrackRecordingFormState(
    val isSheetVisible: Boolean = false,
    val isCollapsed: Boolean = false,
    val settings: TrackRecordingSettings = TrackRecordingSettings(),
)

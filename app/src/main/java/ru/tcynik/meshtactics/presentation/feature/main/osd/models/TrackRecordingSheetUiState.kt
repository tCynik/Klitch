package ru.tcynik.meshtactics.presentation.feature.main.osd.models

import ru.tcynik.meshtactics.domain.track.model.TrackRecordingPreset
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingSettings
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingState

data class TrackRecordingSheetUiState(
    val isVisible: Boolean = false,
    val isCollapsed: Boolean = false,
    val settings: TrackRecordingSettings = TrackRecordingSettings(),
    val recordingState: TrackRecordingState = TrackRecordingState.Idle,
    val durationSeconds: Long = 0L,
    val speedMps: Float? = null,
    val showStopDialog: Boolean = false,
    val trimToMovement: Boolean = false,
    // Callbacks
    val onClose: () -> Unit = {},
    val onToggleCollapsed: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onStopDialogSave: (String) -> Unit = {},
    val onStopDialogDiscard: () -> Unit = {},
    val onStopDialogCancel: () -> Unit = {},
    val onTrimToMovementChanged: (Boolean) -> Unit = {},
    val onPresetSelected: (TrackRecordingPreset) -> Unit = {},
    val onIntervalSelected: (Int?) -> Unit = {},
    val onMinDistanceSelected: (Int) -> Unit = {},
    val onNameChanged: (String) -> Unit = {},
    val onNameCounterChanged: (Int?) -> Unit = {},
    val onColorSelected: (Int) -> Unit = {},
    val onTrackNameChanged: (String) -> Unit = {},
)

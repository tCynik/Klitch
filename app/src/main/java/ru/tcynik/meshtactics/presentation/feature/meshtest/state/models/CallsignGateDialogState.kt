package ru.tcynik.meshtactics.presentation.feature.meshtest.state.models

sealed interface PendingAction {
    data object None : PendingAction
    data object Scan : PendingAction
    data class Connect(val address: String, val deviceName: String) : PendingAction
}

data class CallsignGateDialogState(
    val pendingAction: PendingAction,
    val callsignInput: String,
)

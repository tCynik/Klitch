package ru.tcynik.meshtactics.presentation.feature.meshtest.state.models

data class CallsignGateDialogState(
    val pendingAddress: String,
    val pendingDeviceName: String,
    val callsignInput: String,
)

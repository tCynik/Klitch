package ru.tcynik.mymesh1.presentation.feature.meshtest

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.LogFilter
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MeshTestTab

class MeshTestViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MeshTestUiState())
    val uiState: StateFlow<MeshTestUiState> = _uiState.asStateFlow()

    // --- Navigation ---
    fun onTabSelected(tab: MeshTestTab) = Unit

    // --- Connection ---
    fun onScanClick() = Unit
    fun onStopScanClick() = Unit
    fun onConnectClick(address: String) = Unit
    fun onDisconnectClick() = Unit

    // --- Messages ---
    fun onInputChange(text: String) = Unit
    fun onSendClick() = Unit

    // --- Config ---
    fun onReadConfigClick() = Unit
    fun onEditConfigClick() = Unit
    fun onWriteConfigClick() = Unit

    // --- Telemetry ---
    fun onRefreshTelemetryClick() = Unit

    // --- Log ---
    fun onLogFilterChange(filter: LogFilter) = Unit
    fun onLogPauseToggle() = Unit
}

package ru.tcynik.klitch.presentation.feature.main.osd

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.klitch.R
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.presentation.feature.main.ConnectionUiState
import ru.tcynik.klitch.presentation.ui.UiText

class HudStateMapperTest {

    private fun connectedState(syncRequired: Boolean) = ConnectionUiState(
        connectionStatus = MeshConnectionStatus.Connected(
            nodeId = "!aaaa",
            shortName = "AAAA",
            deviceName = "Node",
            rssi = -50,
            batteryLevel = 0,
        ),
        showConnectionLabel = false,
        syncRequired = syncRequired,
    )

    @Test
    fun `syncRequired true — info slot is yellow, not red`() {
        val slot = HudStateMapper.buildConnectionInfoSlot(connectedState(syncRequired = true))

        assertEquals(UiText.Static(R.string.hud_info_sync_required), slot.content)
        assertEquals(Color.Yellow, slot.color)
    }

    @Test
    fun `syncRequired false — info slot does not show sync-required label`() {
        val slot = HudStateMapper.buildConnectionInfoSlot(connectedState(syncRequired = false))

        assertEquals(emptyInfoSlot(), slot)
    }

    @Test
    fun `syncRequired true — node status color is yellow, not red`() {
        val color = HudStateMapper.buildNodeStatusColor(connectedState(syncRequired = true))

        assertEquals(Color.Yellow, color)
    }

    @Test
    fun `syncRequired false, good rssi — node status color is green`() {
        val color = HudStateMapper.buildNodeStatusColor(connectedState(syncRequired = false))

        assertEquals(Color.Green, color)
    }

    @Test
    fun `disconnected — node status color is red`() {
        val state = ConnectionUiState(connectionStatus = MeshConnectionStatus.Disconnected)

        assertEquals(Color.Red, HudStateMapper.buildNodeStatusColor(state))
    }
}

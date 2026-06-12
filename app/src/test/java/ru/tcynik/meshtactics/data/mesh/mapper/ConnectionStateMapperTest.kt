package ru.tcynik.meshtactics.data.mesh.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.mesh.model.ConnectionState
import ru.tcynik.meshtactics.mesh.model.Node

class ConnectionStateMapperTest {

    @Test
    fun `Connected strips Meshtastic_ prefix from short_name for HUD label`() {
        val node = node(shortName = "Meshtastic_76a0")
        val status = ConnectionState.Connected.toMeshConnectionStatus(
            ourNode = node,
            connectingDeviceName = "Meshtastic_76a0",
        )
        val connected = status as MeshConnectionStatus.Connected
        assertEquals("76a0", connected.shortName)
    }

    @Test
    fun `Connected falls back to BLE device name when short_name is blank`() {
        val node = node(shortName = "")
        val status = ConnectionState.Connected.toMeshConnectionStatus(
            ourNode = node,
            connectingDeviceName = "Meshtastic_ab12",
        )
        val connected = status as MeshConnectionStatus.Connected
        assertEquals("ab12", connected.shortName)
        assertEquals("ab12", connected.deviceName)
    }

    @Test
    fun `Connecting normalizes BLE device name`() {
        val status = ConnectionState.Connecting.toMeshConnectionStatus(
            ourNode = null,
            connectingDeviceName = "Meshtastic_76a0",
        )
        val connecting = status as MeshConnectionStatus.Connecting
        assertEquals("76a0", connecting.deviceName)
    }

    private fun node(shortName: String) = Node(
        num = 1,
        user = User(id = "!00000001", short_name = shortName, long_name = "Test", hw_model = HardwareModel.TBEAM),
    )
}

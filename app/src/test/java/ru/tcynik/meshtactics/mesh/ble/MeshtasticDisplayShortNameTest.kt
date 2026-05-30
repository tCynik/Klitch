package ru.tcynik.meshtactics.mesh.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class MeshtasticDisplayShortNameTest {

    @Test
    fun `extracts suffix from default BLE name`() {
        assertEquals("76a0", "Meshtastic_76a0".toMeshtasticDisplayShortName())
    }

    @Test
    fun `leaves custom short name unchanged`() {
        assertEquals("A1", "A1".toMeshtasticDisplayShortName())
    }

    @Test
    fun `leaves already extracted suffix unchanged`() {
        assertEquals("76a0", "76a0".toMeshtasticDisplayShortName())
    }

    @Test
    fun `normalizes extracted suffix to lowercase`() {
        assertEquals("76a0", "Meshtastic_76A0".toMeshtasticDisplayShortName())
    }
}

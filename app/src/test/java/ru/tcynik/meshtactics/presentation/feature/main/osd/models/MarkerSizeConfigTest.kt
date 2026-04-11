package ru.tcynik.meshtactics.presentation.feature.main.osd.models

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerSizeConfigTest {

    @Test
    fun `level 1 returns 24dp`() {
        assertEquals(24f, MarkerSizeConfig.fromLevel(1).value)
    }

    @Test
    fun `level 5 returns 48dp (default)`() {
        assertEquals(48f, MarkerSizeConfig.fromLevel(5).value)
    }

    @Test
    fun `level 6 returns 54dp (one step above default)`() {
        assertEquals(54f, MarkerSizeConfig.fromLevel(6).value)
    }

    @Test
    fun `level 10 returns 78dp (maximum)`() {
        assertEquals(78f, MarkerSizeConfig.fromLevel(10).value)
    }
}

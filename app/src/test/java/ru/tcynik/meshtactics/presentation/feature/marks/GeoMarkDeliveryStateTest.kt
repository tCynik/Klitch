package ru.tcynik.meshtactics.presentation.feature.marks

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryState
import ru.tcynik.meshtactics.presentation.feature.marks.models.resolveGeoMarkDeliveryState

class GeoMarkDeliveryStateTest {

    @Test
    fun `not self — received`() {
        assertEquals(
            GeoMarkDeliveryState.RECEIVED,
            resolveGeoMarkDeliveryState(isSelf = false, logicalChannelId = "ch-1", authorNodeId = "!abc"),
        )
    }

    @Test
    fun `self with empty channel and author — local`() {
        assertEquals(
            GeoMarkDeliveryState.LOCAL,
            resolveGeoMarkDeliveryState(isSelf = true, logicalChannelId = "", authorNodeId = ""),
        )
    }

    @Test
    fun `self with author node id — sent even without channel`() {
        assertEquals(
            GeoMarkDeliveryState.SENT,
            resolveGeoMarkDeliveryState(isSelf = true, logicalChannelId = "", authorNodeId = "!abc"),
        )
    }

    @Test
    fun `self with channel — sent`() {
        assertEquals(
            GeoMarkDeliveryState.SENT,
            resolveGeoMarkDeliveryState(isSelf = true, logicalChannelId = "ch-1", authorNodeId = ""),
        )
    }
}

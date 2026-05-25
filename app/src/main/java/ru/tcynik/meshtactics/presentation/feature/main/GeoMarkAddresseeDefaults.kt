package ru.tcynik.meshtactics.presentation.feature.main

import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency

internal const val GEO_MARK_LOCAL_STORAGE_ID = "__local__"

/** Saved contour id that should restore explicit user choice (not dynamic default). */
internal fun isPersistedGeoMarkAddresseeChoice(contourId: String): Boolean =
    contourId.isNotEmpty() && contourId != GEO_MARK_LOCAL_STORAGE_ID

internal fun resolveDefaultGeoMarkAddresseeId(
    activeContours: List<Contour>,
    isConnected: Boolean,
    localStorageId: String = GEO_MARK_LOCAL_STORAGE_ID,
): String {
    if (!isConnected) return localStorageId
    val candidates = activeContours.filter { !it.isEmergency }
    if (candidates.isEmpty()) return localStorageId
    return (candidates.find { it.id == DefaultActiveContour.ID } ?: candidates.first()).id.value
}

package ru.tcynik.klitch.presentation.feature.marks

import ru.tcynik.klitch.domain.marker.model.GeoMarkModel

internal object GeoMarkTitleFormatter {
    fun authorLabel(mark: GeoMarkModel, nodeNames: Map<String, String> = emptyMap()): String = when {
        mark.isSelf -> "Я"
        mark.authorNodeId.isNotEmpty() -> nodeNames[mark.authorNodeId] ?: mark.authorNodeId.take(6)
        else -> "—"
    }

    fun selectionTitle(mark: GeoMarkModel, nodeNames: Map<String, String> = emptyMap()): String =
        "${mark.name.ifBlank { "—" }} от ${authorLabel(mark, nodeNames)}"
}

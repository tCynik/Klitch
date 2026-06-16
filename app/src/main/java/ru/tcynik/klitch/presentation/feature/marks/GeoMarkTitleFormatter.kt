package ru.tcynik.klitch.presentation.feature.marks

import ru.tcynik.klitch.R
import ru.tcynik.klitch.domain.marker.model.GeoMarkModel
import ru.tcynik.klitch.presentation.ui.UiText

internal object GeoMarkTitleFormatter {
    fun authorLabel(mark: GeoMarkModel, nodeNames: Map<String, String> = emptyMap()): UiText = when {
        mark.isSelf -> UiText.Static(R.string.geo_mark_self_label)
        mark.authorNodeId.isNotEmpty() -> UiText.Raw(nodeNames[mark.authorNodeId] ?: mark.authorNodeId.take(6))
        else -> UiText.Raw("—")
    }
}

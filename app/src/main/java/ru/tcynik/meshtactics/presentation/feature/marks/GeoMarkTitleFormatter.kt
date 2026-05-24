package ru.tcynik.meshtactics.presentation.feature.marks

import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel

internal object GeoMarkTitleFormatter {
    fun authorLabel(mark: GeoMarkModel): String =
        if (mark.isSelf) "Я" else mark.authorNodeId.take(6)

    fun selectionTitle(mark: GeoMarkModel): String =
        "${mark.name.ifBlank { "—" }} от ${authorLabel(mark)}"
}

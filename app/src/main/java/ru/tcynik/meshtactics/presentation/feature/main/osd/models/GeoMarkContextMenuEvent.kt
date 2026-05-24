package ru.tcynik.meshtactics.presentation.feature.main.osd.models

sealed interface GeoMarkContextMenuEvent {
    val screenX: Float
    val screenY: Float
}

data class DraftPointContextMenuEvent(
    val pointIndex: Int,
    override val screenX: Float,
    override val screenY: Float,
) : GeoMarkContextMenuEvent

data class ExistingMarkContextMenuEvent(
    val markId: String,
    val title: String,
    override val screenX: Float,
    override val screenY: Float,
) : GeoMarkContextMenuEvent

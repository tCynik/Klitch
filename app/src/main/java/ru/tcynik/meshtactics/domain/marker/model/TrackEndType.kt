package ru.tcynik.meshtactics.domain.marker.model

enum class TrackEndType(val ends: Byte) {
    NONE(0),
    SMALL_FILLED_CIRCLE(1),
    LARGE_EMPTY_CIRCLE(2),
    ARROW(3);

    companion object {
        fun fromByte(value: Byte): TrackEndType =
            entries.firstOrNull { it.ends == value } ?: NONE
    }
}

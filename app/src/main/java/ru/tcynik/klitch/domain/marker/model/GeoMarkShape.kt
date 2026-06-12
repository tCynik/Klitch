package ru.tcynik.klitch.domain.marker.model

enum class GeoMarkShape {
    CIRCLE,
    SQUARE,
    TRIANGLE;

    val displayName: String get() = when (this) {
        CIRCLE   -> "Круг"
        SQUARE   -> "Квадрат"
        TRIANGLE -> "Треугольник"
    }
}

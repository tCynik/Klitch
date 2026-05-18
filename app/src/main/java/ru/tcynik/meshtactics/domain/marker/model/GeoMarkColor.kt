package ru.tcynik.meshtactics.domain.marker.model

import androidx.compose.ui.graphics.Color

object GeoMarkColor {

    val names: List<String> = listOf(
        "Белый",
        "Серый",
        "Тёмно-серый",
        "Чёрный",
        "Красный",
        "Оранжевый",
        "Жёлтый",
        "Лаймовый",
        "Зелёный",
        "Голубой",
        "Синий",
        "Тёмно-синий",
        "Фиолетовый",
        "Розовый",
        "Коричневый",
        "Золотой",
    )

    val palette: List<Color> = listOf(
        Color(0xFFFFFFFF),  // 0  White
        Color(0xFF9E9E9E),  // 1  Gray
        Color(0xFF424242),  // 2  Dark gray
        Color(0xFF212121),  // 3  Black
        Color(0xFFE53935),  // 4  Red
        Color(0xFFFF6D00),  // 5  Orange
        Color(0xFFFFEA00),  // 6  Yellow
        Color(0xFF76FF03),  // 7  Lime
        Color(0xFF2E7D32),  // 8  Green
        Color(0xFF26C6DA),  // 9  Cyan
        Color(0xFF1565C0),  // 10 Blue
        Color(0xFF283593),  // 11 Dark blue
        Color(0xFF7B1FA2),  // 12 Purple
        Color(0xFFE91E63),  // 13 Pink
        Color(0xFF6D4C41),  // 14 Brown
        Color(0xFFFFD600),  // 15 Gold
    )

    fun colorAt(index: Int): Color = palette.getOrElse(index) { palette[0] }

    fun indexOf(color: Color): Int = palette.indexOf(color).coerceAtLeast(0)
}

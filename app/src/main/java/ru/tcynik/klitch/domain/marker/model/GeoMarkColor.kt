package ru.tcynik.klitch.domain.marker.model

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

    /** ARGB color values (0xAARRGGBB). Use Color(argb) in presentation to get a Compose Color. */
    val palette: List<Int> = listOf(
        0xFFFFFFFF.toInt(),  // 0  White
        0xFF9E9E9E.toInt(),  // 1  Gray
        0xFF424242.toInt(),  // 2  Dark gray
        0xFF212121.toInt(),  // 3  Black
        0xFFE53935.toInt(),  // 4  Red
        0xFFFF6D00.toInt(),  // 5  Orange
        0xFFFFEA00.toInt(),  // 6  Yellow
        0xFF76FF03.toInt(),  // 7  Lime
        0xFF2E7D32.toInt(),  // 8  Green
        0xFF26C6DA.toInt(),  // 9  Cyan
        0xFF1565C0.toInt(),  // 10 Blue
        0xFF283593.toInt(),  // 11 Dark blue
        0xFF7B1FA2.toInt(),  // 12 Purple
        0xFFE91E63.toInt(),  // 13 Pink
        0xFF6D4C41.toInt(),  // 14 Brown
        0xFFFFD600.toInt(),  // 15 Gold
    )

    fun colorAt(index: Int): Int = palette.getOrElse(index) { palette[0] }

    fun indexOf(argb: Int): Int = palette.indexOf(argb).coerceAtLeast(0)
}

package ru.tcynik.meshtactics.presentation.feature.marks

import java.time.Instant
import java.time.ZoneId

internal object GeoMarkCreatedAtFormatter {
    fun format(
        createdAtSeconds: Long,
        nowSeconds: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val createdZoned = Instant.ofEpochSecond(createdAtSeconds).atZone(zone)
        val nowDate = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate()
        val createdDate = createdZoned.toLocalDate()

        if (createdDate == nowDate) {
            val time = createdZoned.toLocalTime()
            return "${time.hour}:${time.minute.toString().padStart(2, '0')}"
        }
        return "${createdDate.monthValue}мес.${createdDate.dayOfMonth}"
    }
}

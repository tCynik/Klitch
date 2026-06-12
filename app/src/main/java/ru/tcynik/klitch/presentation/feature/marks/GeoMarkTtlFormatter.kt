package ru.tcynik.klitch.presentation.feature.marks

import kotlin.math.ceil

internal object GeoMarkTtlFormatter {
    fun format(expiresAt: Long?, nowSeconds: Long): String {
        if (expiresAt == null) return "—"
        val remaining = expiresAt - nowSeconds
        if (remaining <= 0) return "истёк"
        if (remaining < 60) return "<1 мин."
        val minutes = ceil(remaining / 60.0).toInt()
        if (minutes < 60) return "$minutes мин."
        val hours = minutes / 60
        val mins = minutes % 60
        return if (mins == 0) "${hours}ч" else "${hours}ч ${mins}м"
    }
}

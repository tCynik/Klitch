package ru.tcynik.klitch.presentation.feature.marks

import ru.tcynik.klitch.R
import ru.tcynik.klitch.presentation.ui.UiText
import kotlin.math.ceil

internal object GeoMarkTtlFormatter {
    fun format(expiresAt: Long?, nowSeconds: Long): UiText {
        if (expiresAt == null) return UiText.Raw("—")
        val remaining = expiresAt - nowSeconds
        if (remaining <= 0) return UiText.Static(R.string.geo_mark_ttl_expired)
        if (remaining < 60) return UiText.Static(R.string.geo_mark_ttl_less_minute)
        val minutes = ceil(remaining / 60.0).toInt()
        if (minutes < 60) return UiText.Dynamic(R.string.geo_mark_ttl_minutes, minutes)
        val hours = minutes / 60
        val mins = minutes % 60
        return if (mins == 0)
            UiText.Dynamic(R.string.geo_mark_ttl_hours, hours)
        else
            UiText.Dynamic(R.string.geo_mark_ttl_hours_minutes, hours, mins)
    }
}

package ru.tcynik.klitch.presentation.feature.main.osd.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class DrawerMenuItem(
    @DrawableRes val iconRes: Int,
    @StringRes val label: Int,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

package ru.tcynik.meshtactics.presentation.feature.main.osd.models

import androidx.annotation.DrawableRes

data class DrawerMenuItem(
    @DrawableRes val iconRes: Int,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

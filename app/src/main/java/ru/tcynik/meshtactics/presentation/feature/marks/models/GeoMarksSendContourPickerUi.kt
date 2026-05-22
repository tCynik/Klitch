package ru.tcynik.meshtactics.presentation.feature.marks.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class GeoMarksSendContourPickerUi(
    val markId: String,
    val markName: String,
    val contours: ImmutableList<GeoMarkContourOptionUi> = persistentListOf(),
)

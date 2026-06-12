package ru.tcynik.klitch.domain.user.model

const val DISPLAY_NAME_MAX_LENGTH = 39

data class AppUser(
    val displayName: String,
)

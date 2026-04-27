package ru.tcynik.meshtactics.domain.channel.model

sealed interface ContourSyncResult {
    data object InSync : ContourSyncResult
    data object NeedsSync : ContourSyncResult
}

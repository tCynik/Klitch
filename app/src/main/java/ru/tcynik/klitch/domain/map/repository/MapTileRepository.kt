package ru.tcynik.klitch.domain.map.repository

// MVP: single tile source URL template.
// Beta 1.0: extend with source list, offline region management, MBTiles/PMTiles loading, KMZ import.
interface MapTileRepository {
    fun getTileUrlTemplate(): String
}

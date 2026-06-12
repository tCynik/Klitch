package ru.tcynik.klitch.data.map.repository

import ru.tcynik.klitch.domain.map.repository.MapTileRepository

// MVP: returns a single hardcoded OpenTopoMap XYZ tile URL template.
// Beta 1.0: replace with multi-source, offline cache, MBTiles/PMTiles support.
class MapTileRepositoryImpl : MapTileRepository {
    override fun getTileUrlTemplate(): String =
        "https://tile.opentopomap.org/{z}/{x}/{y}.png"
}

package ru.tcynik.meshtactics.domain.map.usecase

import ru.tcynik.meshtactics.domain.map.repository.MapTileRepository

class GetTileUrlUseCase(
    private val repository: MapTileRepository,
) {
    operator fun invoke(): String = repository.getTileUrlTemplate()
}

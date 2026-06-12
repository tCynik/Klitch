package ru.tcynik.klitch.domain.map.usecase

import ru.tcynik.klitch.domain.map.repository.MapTileRepository

class GetTileUrlUseCase(
    private val repository: MapTileRepository,
) {
    operator fun invoke(): String = repository.getTileUrlTemplate()
}

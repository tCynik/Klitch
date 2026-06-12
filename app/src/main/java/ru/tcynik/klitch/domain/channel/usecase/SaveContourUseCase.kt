package ru.tcynik.klitch.domain.channel.usecase

import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.usecase.base.UseCase

class SaveContourUseCase(
    private val repository: ContourRepository,
) : UseCase<Contour, Unit>() {
    override suspend fun invoke(params: Contour) =
        repository.saveContour(params)
}

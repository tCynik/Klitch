package ru.tcynik.meshtactics.domain.channel.usecase

import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

class SaveContourUseCase(
    private val repository: ContourRepository,
) : UseCase<Contour, Unit>() {
    override suspend fun invoke(params: Contour) =
        repository.saveContour(params)
}

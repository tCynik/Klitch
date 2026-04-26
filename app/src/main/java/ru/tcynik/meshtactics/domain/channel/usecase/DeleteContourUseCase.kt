package ru.tcynik.meshtactics.domain.channel.usecase

import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

class DeleteContourUseCase(
    private val repository: ContourRepository,
) : UseCase<ContourId, Unit>() {
    override suspend fun invoke(params: ContourId) =
        repository.deleteContour(params)
}

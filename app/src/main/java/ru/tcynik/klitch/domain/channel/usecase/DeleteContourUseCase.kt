package ru.tcynik.klitch.domain.channel.usecase

import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.usecase.base.UseCase

class DeleteContourUseCase(
    private val repository: ContourRepository,
) : UseCase<ContourId, Unit>() {
    override suspend fun invoke(params: ContourId) =
        repository.deleteContour(params)
}

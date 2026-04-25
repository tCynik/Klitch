package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository

class SetContourActiveUseCase(
    private val repository: ContourRepository,
) {
    suspend operator fun invoke(id: ContourId, isActive: Boolean) {
        val contour = repository.observeContours().first().find { it.id == id } ?: return
        repository.saveContour(contour.copy(isActive = isActive))
    }
}

package ru.tcynik.mymesh1.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshMessageModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshMessagingRepository
import ru.tcynik.mymesh1.domain.usecase.base.FlowUseCase

/** Params: contactKey (e.g. "^all" for broadcast, or node ID "!hexnum") */
class ObserveMessagesUseCase(
    private val repository: MeshMessagingRepository,
) : FlowUseCase<String, List<MeshMessageModel>>() {
    override fun invoke(params: String): Flow<List<MeshMessageModel>> =
        repository.observeMessages(params)
}

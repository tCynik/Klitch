package ru.tcynik.klitch.domain.usecase.node

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.model.NodeModel
import ru.tcynik.klitch.domain.repository.NodeRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class GetNodesUseCase(
    private val repository: NodeRepository,
) : FlowUseCase<NoParams, List<NodeModel>>() {

    override fun invoke(params: NoParams): Flow<List<NodeModel>> =
        repository.observeNodes()
}

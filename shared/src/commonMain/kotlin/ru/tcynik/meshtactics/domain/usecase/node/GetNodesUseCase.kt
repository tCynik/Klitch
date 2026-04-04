package ru.tcynik.meshtactics.domain.usecase.node

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.model.NodeModel
import ru.tcynik.meshtactics.domain.repository.NodeRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class GetNodesUseCase(
    private val repository: NodeRepository,
) : FlowUseCase<NoParams, List<NodeModel>>() {

    override fun invoke(params: NoParams): Flow<List<NodeModel>> =
        repository.observeNodes()
}

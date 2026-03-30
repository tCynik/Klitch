package ru.tcynik.mymesh1.domain.usecase.node

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.model.NodeModel
import ru.tcynik.mymesh1.domain.repository.NodeRepository
import ru.tcynik.mymesh1.domain.usecase.base.FlowUseCase
import ru.tcynik.mymesh1.domain.usecase.base.NoParams

class GetNodesUseCase(
    private val repository: NodeRepository,
) : FlowUseCase<NoParams, List<NodeModel>>() {

    override fun invoke(params: NoParams): Flow<List<NodeModel>> =
        repository.observeNodes()
}

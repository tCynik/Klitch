package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveNodeChannelsUseCase(
    private val repository: MeshConfigRepository,
) : FlowUseCase<NoParams, List<NodeChannelSlot>>() {
    override fun invoke(params: NoParams): Flow<List<NodeChannelSlot>> =
        repository.observeNodeChannels()
}

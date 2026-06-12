package ru.tcynik.klitch.domain.channel.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.channel.model.NodeChannelSlot
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveNodeChannelsUseCase(
    private val repository: MeshConfigRepository,
) : FlowUseCase<NoParams, List<NodeChannelSlot>>() {
    override fun invoke(params: NoParams): Flow<List<NodeChannelSlot>> =
        repository.observeNodeChannels()
}

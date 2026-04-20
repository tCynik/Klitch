package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveLogicalChannelsUseCase(
    private val repository: LogicalChannelRepository,
) : FlowUseCase<NoParams, List<LogicalChannel>>() {
    override fun invoke(params: NoParams): Flow<List<LogicalChannel>> =
        repository.observeChannels()
}

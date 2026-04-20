package ru.tcynik.meshtactics.domain.channel.usecase

import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

class SaveLogicalChannelUseCase(
    private val repository: LogicalChannelRepository,
) : UseCase<LogicalChannel, Unit>() {
    override suspend fun invoke(params: LogicalChannel) =
        repository.saveChannel(params)
}

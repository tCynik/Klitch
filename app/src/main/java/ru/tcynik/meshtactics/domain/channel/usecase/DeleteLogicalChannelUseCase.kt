package ru.tcynik.meshtactics.domain.channel.usecase

import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

class DeleteLogicalChannelUseCase(
    private val repository: LogicalChannelRepository,
) : UseCase<LogicalChannelId, Unit>() {
    override suspend fun invoke(params: LogicalChannelId) =
        repository.deleteChannel(params)
}

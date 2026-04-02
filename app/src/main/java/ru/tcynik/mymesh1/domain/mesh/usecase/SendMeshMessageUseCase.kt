package ru.tcynik.mymesh1.domain.mesh.usecase

import ru.tcynik.mymesh1.domain.mesh.repository.MeshMessagingRepository
import ru.tcynik.mymesh1.domain.usecase.base.UseCase

data class SendMeshMessageParams(
    val text: String,
    val contactKey: String,
    val channel: Int = 0,
)

class SendMeshMessageUseCase(
    private val repository: MeshMessagingRepository,
) : UseCase<SendMeshMessageParams, Unit>() {
    override suspend fun invoke(params: SendMeshMessageParams) =
        repository.sendMessage(params.text, params.contactKey, params.channel)
}

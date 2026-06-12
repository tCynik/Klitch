package ru.tcynik.klitch.domain.chat.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.data.chat.adapter.MeshToChatAdapter

class SyncEmergencyMuteUseCase(
    private val adapter: MeshToChatAdapter,
) {
    fun observe(): Flow<Unit> = adapter.observeEmergencyMuteSync()
}

package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

/**
 * После команды reboot BLE-транспорт может переподключиться без смены [ConnectionState],
 * из-за чего UI остаётся в состоянии «Перезагрузка». Принудительно разрываем сессию
 * после ожидания перезагрузки и подключаемся к последнему устройству заново.
 *
 * Если нода грузится дольше ожидаемого — до [MAX_ATTEMPTS] попыток с таймаутом на каждую.
 * После исчерпания попыток сбрасывает reboot-состояние и оставляет приложение отключённым.
 */
class ReconnectAfterNodeRebootUseCase(
    private val disconnectFromMesh: DisconnectFromMeshUseCase,
    private val connectToDevice: ConnectToMeshDeviceUseCase,
    private val getLastConnectedDevice: GetLastConnectedDeviceUseCase,
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val rebootStateRepository: RebootStateRepository,
) : UseCase<NoParams, Unit>() {

    override suspend fun invoke(params: NoParams) {
        delay(REBOOT_WAIT_MS)
        disconnectFromMesh(NoParams)
        delay(POST_DISCONNECT_DELAY_MS)

        val lastDevice = getLastConnectedDevice() ?: run {
            rebootStateRepository.setRebooting(false)
            return
        }
        val connectParams = ConnectToMeshDeviceParams(lastDevice.address, lastDevice.name)

        repeat(MAX_ATTEMPTS) { attempt ->
            connectToDevice(connectParams)
            val connected = withTimeoutOrNull(CONNECT_ATTEMPT_TIMEOUT_MS) {
                observeConnectionStatus(NoParams)
                    .filter { it is MeshConnectionStatus.Connected }
                    .first()
            }
            if (connected != null) return

            if (attempt < MAX_ATTEMPTS - 1) {
                disconnectFromMesh(NoParams)
                delay(RETRY_DELAY_MS)
            }
        }

        disconnectFromMesh(NoParams)
        rebootStateRepository.setRebooting(false)
    }

    internal companion object {
        /** Firmware reboot delay (5s) + margin for boot and BLE advertising. */
        const val REBOOT_WAIT_MS = 8_000L
        const val POST_DISCONNECT_DELAY_MS = 500L
        const val RETRY_DELAY_MS = 2_000L
        const val CONNECT_ATTEMPT_TIMEOUT_MS = 15_000L
        const val MAX_ATTEMPTS = 3
    }
}

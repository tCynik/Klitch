/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package ru.tcynik.klitch.mesh.data.manager

import co.touchlab.kermit.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import ru.tcynik.klitch.mesh.common.util.handledLaunch
import ru.tcynik.klitch.mesh.common.util.ioDispatcher
import ru.tcynik.klitch.mesh.common.util.nowMillis
import ru.tcynik.klitch.mesh.model.ConnectionState
import ru.tcynik.klitch.mesh.model.DataPacket
import ru.tcynik.klitch.mesh.model.MeshLog
import ru.tcynik.klitch.mesh.model.MessageStatus
import ru.tcynik.klitch.mesh.model.RadioNotConnectedException
import ru.tcynik.klitch.mesh.model.util.toOneLineString
import ru.tcynik.klitch.mesh.model.util.toPIIString
import ru.tcynik.klitch.mesh.repository.MeshLogRepository
import ru.tcynik.klitch.mesh.repository.PacketHandler
import ru.tcynik.klitch.mesh.repository.PacketRepository
import ru.tcynik.klitch.mesh.repository.RadioInterfaceService
import ru.tcynik.klitch.mesh.repository.ServiceBroadcasts
import ru.tcynik.klitch.mesh.repository.ServiceRepository
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
@Single
class PacketHandlerImpl(
    private val packetRepository: Lazy<PacketRepository>,
    private val serviceBroadcasts: ServiceBroadcasts,
    private val radioInterfaceService: RadioInterfaceService,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val serviceRepository: ServiceRepository,
) : PacketHandler {

    companion object {
        private val TIMEOUT = 5.seconds
        private const val LOCAL_ADMIN_HANDOFF_MS = 400L
    }

    private var queueJob: Job? = null
    private var scope: CoroutineScope = CoroutineScope(ioDispatcher)

    private val queueMutex = Mutex()
    private val queuedPackets = mutableListOf<MeshPacket>()

    private val queueResponse = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

    override fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    override fun sendToRadio(p: ToRadio) {
        Logger.d { "Sending to radio ${p.toPIIString()}" }
        val b = p.encode()

        radioInterfaceService.sendToRadio(b)
        p.packet?.id?.let { changeStatus(it, MessageStatus.ENROUTE) }

        val packet = p.packet
        if (packet?.decoded != null) {
            val packetToSave =
                MeshLog(
                    uuid = Uuid.random().toString(),
                    message_type = "Packet",
                    received_date = nowMillis,
                    raw_message = packet.toString(),
                    fromNum = MeshLog.NODE_NUM_LOCAL,
                    portNum = packet.decoded?.portnum?.value ?: 0,
                    fromRadio = FromRadio(packet = packet),
                )
            insertMeshLog(packetToSave)
        }
    }

    override fun sendToRadio(packet: MeshPacket) {
        registerSendDeferred(packet.id)
        scope.launch {
            queueMutex.withLock { queuedPackets.add(0, packet) }
            startPacketQueue()
        }
    }

    override fun sendLocalAdminPacket(packet: MeshPacket) {
        Logger.i("Klitch/Contour") {
            "sendLocalAdmin: id=${packet.id.toUInt()} to=${packet.to.toUInt()} from=${packet.from.toUInt()}"
        }
        val deferred = registerSendDeferred(packet.id)
        scope.launch {
            try {
                if (serviceRepository.connectionState.value != ConnectionState.Connected) {
                    Logger.w("Klitch/Contour") { "sendLocalAdmin skipped: not connected id=${packet.id.toUInt()}" }
                    completeSendDeferred(packet.id, deferred, success = false)
                    return@launch
                }
                sendToRadio(ToRadio(packet = packet))
                // Local BLE admin often has no per-packet QueueStatus — hand off after radio write.
                delay(LOCAL_ADMIN_HANDOFF_MS)
                val stillConnected = serviceRepository.connectionState.value == ConnectionState.Connected
                completeSendDeferred(packet.id, deferred, success = stillConnected)
                if (stillConnected) {
                    Logger.i("Klitch/Contour") { "sendLocalAdmin: handoff ok id=${packet.id.toUInt()}" }
                }
            } catch (ex: RadioNotConnectedException) {
                Logger.w(ex) { "sendLocalAdmin skipped: Not connected to radio" }
                completeSendDeferred(packet.id, deferred, success = false)
            } catch (ex: Exception) {
                Logger.e(ex) { "sendLocalAdmin error: ${ex.message}" }
                completeSendDeferred(packet.id, deferred, success = false)
            }
        }
    }

    override suspend fun prepareForAdminBurst() {
        queueJob?.cancel()
        queueJob = null
        val dropped = queueMutex.withLock {
            val count = queuedPackets.size
            queuedPackets.clear()
            count
        }
        if (dropped > 0) {
            Logger.i("Klitch/Contour") { "prepareForAdminBurst: dropped $dropped queued packets" }
        }
    }

    override fun stopPacketQueue() {
        if (queueJob?.isActive == true) {
            Logger.i { "Stopping packet queueJob" }
            queueJob?.cancel()
            queueJob = null
            scope.launch {
                queueMutex.withLock { queuedPackets.clear() }
                queueResponse.values.lastOrNull { !it.isCompleted }?.complete(false)
                queueResponse.clear()
            }
        }
    }

    override fun handleQueueStatus(queueStatus: QueueStatus) {
        Logger.d { "[queueStatus] ${queueStatus.toOneLineString()}" }
        if (queueStatus.mesh_packet_id != 0) {
            Logger.i("Klitch/Contour") {
                "queueStatus: id=${queueStatus.mesh_packet_id.toUInt()} res=${queueStatus.res} free=${queueStatus.free}"
            }
        }
        val (success, isFull, requestId) = with(queueStatus) { Triple(res == 0, free == 0, mesh_packet_id) }
        // Packet accepted into the device queue (queue full afterwards) — unblock the send job.
        if (success && isFull) {
            scope.launch { completeQueueResponse(requestId, success = true) }
            return
        }

        scope.launch {
            completeQueueResponse(requestId, success)
        }
    }

    private fun completeQueueResponse(requestId: Int, success: Boolean) {
        if (requestId == 0) {
            // Global queue status — must not complete unrelated admin TX waiters.
            Logger.d { "queueStatus: ignored id=0 res=$success" }
            return
        }
        queueResponse.remove(requestId)?.complete(success)
    }

    override fun removeResponse(dataRequestId: Int, complete: Boolean) {
        scope.launch { queueResponse.remove(dataRequestId)?.complete(complete) }
    }

    override suspend fun awaitPacketSendResult(packetId: Int, timeout: Duration): Boolean {
        if (packetId == 0) return false
        return try {
            withTimeout(timeout) {
                var deferred: CompletableDeferred<Boolean>? = null
                while (deferred == null) {
                    deferred = queueResponse[packetId]
                    if (deferred == null) delay(50.milliseconds)
                }
                deferred.await()
            }
        } catch (_: TimeoutCancellationException) {
            false
        }
    }

    private fun startPacketQueue() {
        if (queueJob?.isActive == true) return
        queueJob = scope.handledLaunch {
            try {
                while (serviceRepository.connectionState.value == ConnectionState.Connected) {
                    val packet = queueMutex.withLock { queuedPackets.removeFirstOrNull() } ?: break
                    @Suppress("TooGenericExceptionCaught", "SwallowedException")
                    try {
                        val response = sendPacket(packet)
                        Logger.d { "queueJob packet id=${packet.id.toUInt()} waiting" }
                        val success = withTimeout(TIMEOUT) { response.await() }
                        Logger.d { "queueJob packet id=${packet.id.toUInt()} success $success" }
                    } catch (e: TimeoutCancellationException) {
                        Logger.d { "queueJob packet id=${packet.id.toUInt()} timeout" }
                        queueResponse.remove(packet.id)?.complete(false)
                    } catch (e: Exception) {
                        Logger.d { "queueJob packet id=${packet.id.toUInt()} failed" }
                        queueResponse.remove(packet.id)?.complete(false)
                    }
                }
            } finally {
                queueJob = null
                if (queueMutex.withLock { queuedPackets.isNotEmpty() }) {
                    startPacketQueue()
                }
            }
        }
    }

    private fun registerSendDeferred(packetId: Int): CompletableDeferred<Boolean> {
        queueResponse.remove(packetId)?.takeIf { it.isCompleted }
        return queueResponse.compute(packetId) { _, existing ->
            if (existing == null || existing.isCompleted) CompletableDeferred() else existing
        }!!
    }

    private fun completeSendDeferred(
        packetId: Int,
        deferred: CompletableDeferred<Boolean>,
        success: Boolean,
    ) {
        queueResponse.remove(packetId, deferred)
        deferred.complete(success)
    }

    private fun changeStatus(packetId: Int, m: MessageStatus) = scope.handledLaunch {
        if (packetId != 0) {
            getDataPacketById(packetId)?.let { p ->
                if (p.status == m) return@handledLaunch
                packetRepository.value.updateMessageStatus(p, m)
                serviceBroadcasts.broadcastMessageStatus(packetId, m)
            }
        }
    }

    private suspend fun getDataPacketById(packetId: Int): DataPacket? = withTimeoutOrNull(1.seconds) {
        var dataPacket: DataPacket? = null
        while (dataPacket == null) {
            dataPacket = packetRepository.value.getPacketById(packetId)
            if (dataPacket == null) delay(100.milliseconds)
        }
        dataPacket
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendPacket(packet: MeshPacket): CompletableDeferred<Boolean> {
        val deferred = registerSendDeferred(packet.id)
        try {
            if (serviceRepository.connectionState.value != ConnectionState.Connected) {
                throw RadioNotConnectedException()
            }
            sendToRadio(ToRadio(packet = packet))
            // No-ack packets (incl. broadcast waypoints) do not get routing ACKs; pacing for
            // geo-marks is handled in GeoMarkSendQueue, not by blocking here on QueueStatus.
            if (packet.want_ack != true) {
                completeSendDeferred(packet.id, deferred, success = true)
            }
        } catch (ex: RadioNotConnectedException) {
            Logger.w(ex) { "sendToRadio skipped: Not connected to radio" }
            completeSendDeferred(packet.id, deferred, success = false)
        } catch (ex: Exception) {
            Logger.e(ex) { "sendToRadio error: ${ex.message}" }
            completeSendDeferred(packet.id, deferred, success = false)
        }
        return deferred
    }

    private fun insertMeshLog(packetToSave: MeshLog) {
        scope.handledLaunch {
            Logger.d {
                "insert: ${packetToSave.message_type} = " +
                    "${packetToSave.raw_message.toOneLineString()} from=${packetToSave.fromNum}"
            }
            meshLogRepository.value.insert(packetToSave)
        }
    }
}

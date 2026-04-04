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
package ru.tcynik.meshtactics.mesh.data.manager

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single
import ru.tcynik.meshtactics.mesh.common.util.NumberFormatter
import ru.tcynik.meshtactics.mesh.common.util.handledLaunch
import ru.tcynik.meshtactics.mesh.common.util.ioDispatcher
import ru.tcynik.meshtactics.mesh.common.util.nowMillis
import ru.tcynik.meshtactics.mesh.model.fullRouteDiscovery
import ru.tcynik.meshtactics.mesh.model.getTracerouteResponse
import ru.tcynik.meshtactics.mesh.model.service.TracerouteResponse
import ru.tcynik.meshtactics.mesh.repository.NodeManager
import ru.tcynik.meshtactics.mesh.repository.NodeRepository
import ru.tcynik.meshtactics.mesh.repository.ServiceRepository
import ru.tcynik.meshtactics.mesh.repository.TracerouteHandler
import ru.tcynik.meshtactics.mesh.repository.TracerouteSnapshotRepository
import org.meshtastic.proto.MeshPacket

@Single
class TracerouteHandlerImpl(
    private val nodeManager: NodeManager,
    private val serviceRepository: ServiceRepository,
    private val tracerouteSnapshotRepository: TracerouteSnapshotRepository,
    private val nodeRepository: NodeRepository,
) : TracerouteHandler {
    private var scope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val startTimes = atomic(persistentMapOf<Int, Long>())

    override fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    override fun recordStartTime(requestId: Int) {
        startTimes.update { it.put(requestId, nowMillis) }
    }

    override fun handleTraceroute(packet: MeshPacket, logUuid: String?, logInsertJob: Job?) {
        // Decode the route discovery once — avoids triple protobuf decode
        val routeDiscovery = packet.fullRouteDiscovery ?: return
        val forwardRoute = routeDiscovery.route
        val returnRoute = routeDiscovery.route_back

        // Require both directions for a "full" traceroute response
        if (forwardRoute.isEmpty() || returnRoute.isEmpty()) return

        val full =
            routeDiscovery.getTracerouteResponse(
                getUser = { num ->
                    nodeManager.nodeDBbyNodeNum[num]?.let { "${it.user.long_name} (${it.user.short_name})" }
                        ?: "Unknown" // TODO: Use core:resources once available in core:data
                },
                headerTowards = "Route towards destination:",
                headerBack = "Route back to us:",
            )

        val requestId = packet.decoded?.request_id ?: 0

        if (logUuid != null) {
            scope.handledLaunch {
                logInsertJob?.join()
                val routeNodeNums = (forwardRoute + returnRoute).distinct()
                val nodeDbByNum = nodeRepository.nodeDBbyNum.value
                val snapshotPositions =
                    routeNodeNums.mapNotNull { num -> nodeDbByNum[num]?.validPosition?.let { num to it } }.toMap()
                tracerouteSnapshotRepository.upsertSnapshotPositions(logUuid, requestId, snapshotPositions)
            }
        }

        val start = startTimes.value[requestId]
        startTimes.update { it.remove(requestId) }
        val responseText =
            if (start != null) {
                val elapsedMs = nowMillis - start
                val seconds = elapsedMs / MILLIS_PER_SECOND
                Logger.i { "Traceroute $requestId complete in $seconds s" }
                "$full\n\nDuration: ${NumberFormatter.format(seconds, 1)} s"
            } else {
                full
            }

        val destination = forwardRoute.firstOrNull() ?: returnRoute.lastOrNull() ?: 0

        serviceRepository.setTracerouteResponse(
            TracerouteResponse(
                message = responseText,
                destinationNodeNum = destination,
                requestId = requestId,
                forwardRoute = forwardRoute,
                returnRoute = returnRoute,
                logUuid = logUuid,
            ),
        )
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000.0
    }
}

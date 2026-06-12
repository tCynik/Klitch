package ru.tcynik.klitch.data.marker.repository



import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.channels.Channel

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

import ru.tcynik.klitch.domain.channel.model.ContourId

import ru.tcynik.klitch.domain.marker.model.GeoMarkModel



/**

 * Serializes mesh geo-mark transmissions (not local DB writes).

 * Enforces a minimum gap between radio sends (Meshtastic firmware: 10 s for WAYPOINT_APP from phone).

 */

internal class GeoMarkSendQueue(

    scope: CoroutineScope,

    private val minIntervalMs: Long,

    private val sendBlock: suspend (GeoMarkModel, ContourId?) -> Unit,

) {

    private var lastSendFinishedAtMs: Long = 0L



    private data class Job(

        val mark: GeoMarkModel,

        val contourId: ContourId?,

    )



    private val channel = Channel<Job>(Channel.UNLIMITED)



    init {

        scope.launch {

            for (job in channel) {

                try {

                    val now = System.currentTimeMillis()

                    val waitMs = minIntervalMs - (now - lastSendFinishedAtMs)

                    if (waitMs > 0) delay(waitMs)

                    sendBlock(job.mark, job.contourId)

                    lastSendFinishedAtMs = System.currentTimeMillis()

                } catch (_: Exception) {

                    // Mark stays in DB (QUEUED); user can resend from list.

                }

            }

        }

    }



    /** Queues mesh transmission; returns once the job is buffered (does not wait for radio). */

    suspend fun schedule(mark: GeoMarkModel, contourId: ContourId?) {

        channel.send(Job(mark, contourId))

    }

}



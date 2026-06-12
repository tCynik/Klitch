package ru.tcynik.meshtactics.domain.channel.model

/** Имя канала, которое записывается на ноду Meshtastic (case-sensitive). */
fun meshtasticChannelName(contour: Contour): String =
    meshtasticChannelName(contour.id, contour.name)

fun meshtasticChannelName(id: ContourId, displayName: String): String = when (id) {
    DefaultActiveContour.ID -> DefaultActiveContour.CHANNEL_NAME
    DefaultContour.ID -> DefaultContour.CHANNEL_NAME
    else -> displayName
}

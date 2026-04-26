package ru.tcynik.meshtactics.domain.channel.model

object DefaultContour {
    val ID = ContourId("00000000-0000-0000-0000-000000000001")
    const val DISPLAY_NAME = "Emergency"
    const val CHANNEL_NAME = ""
    const val OPEN_PSK = "AQ=="
    const val IS_ACTIVE_DEFAULT = false

    val CHANNEL_HASH = ContourHash.compute(CHANNEL_NAME, OPEN_PSK)

    val TRANSPORT = ContourTransport(
        meshtastic = MeshtasticChannel(psk = OPEN_PSK, channelHash = CHANNEL_HASH)
    )

    fun asContour() = Contour(
        id = ID,
        name = DISPLAY_NAME,
        description = null,
        expiration = null,
        exclusivityTime = null,
        isActive = IS_ACTIVE_DEFAULT,
        transport = TRANSPORT,
    )
    // TODO(contour): SOS mode — activate Primary automatically on alarm trigger
}

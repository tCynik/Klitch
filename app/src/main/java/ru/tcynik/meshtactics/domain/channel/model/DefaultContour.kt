package ru.tcynik.meshtactics.domain.channel.model

object DefaultContour {
    val ID = ContourId("00000000-0000-0000-0000-000000000001")
    const val DISPLAY_NAME = "Emergency"
    const val CHANNEL_NAME = "MTTestSOS" // ВРЕМЕННО — не коммитить
    const val OPEN_PSK = "QNnShqu6wbMFRBMvqPOzgA==" // ВРЕМЕННО — не коммитить
    const val IS_ACTIVE_DEFAULT = false

    val CHANNEL_HASH = ContourHash.compute(CHANNEL_NAME, OPEN_PSK)

    val TRANSPORT = ContourTransport(
        meshtastic = MeshtasticChannel(psk = OPEN_PSK, channelHash = CHANNEL_HASH)
    )

    const val DESCRIPTION = "Аварийный контур. Здесь можно попросить помощь."

    fun asContour() = Contour(
        id = ID,
        name = DISPLAY_NAME,
        description = DESCRIPTION,
        expiration = null,
        exclusivityTime = null,
        isActive = IS_ACTIVE_DEFAULT,
        transport = TRANSPORT,
    )
}

package ru.tcynik.klitch.domain.channel.model

object DefaultActiveContour {
    val ID = ContourId("00000000-0000-0000-0000-000000000002")
    const val DISPLAY_NAME = "Basic"
    const val CHANNEL_NAME = "basic"
    // SHA-256("MeshTactics.basic.contour.v1"), 32 bytes AES-256
    const val DEFAULT_PSK = "YkI0+1gBYtYfKYgP66/SdLh8KnloZyMZn6Ts5TSQodI="
    const val IS_ACTIVE = true
    const val DESCRIPTION = "Делись своими приключениями здесь и сейчас"
    // TODO(contour): replace hardcoded seed with contour sharing (QR/import)
}

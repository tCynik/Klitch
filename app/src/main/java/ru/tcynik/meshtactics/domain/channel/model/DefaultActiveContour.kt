package ru.tcynik.meshtactics.domain.channel.model

object DefaultActiveContour {
    val ID = ContourId("00000000-0000-0000-0000-000000000002")
    const val DISPLAY_NAME = "Default contour"
    const val CHANNEL_NAME = "default"
    const val IS_ACTIVE = true
    const val DESCRIPTION = "Делись своими приключениями здесь и сейчас"
    // TODO(contour): replace hardcoded seed with contour sharing (QR/import)
}

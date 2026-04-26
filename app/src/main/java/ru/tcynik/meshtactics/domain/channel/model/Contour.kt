package ru.tcynik.meshtactics.domain.channel.model

import java.time.Instant

data class Contour(
    val id: ContourId,
    val name: String,
    val description: String?,
    val expiration: Instant?,
    val exclusivityTime: Instant?,
    val isActive: Boolean,
    val transport: ContourTransport,
)

val Contour.isEmergency: Boolean get() = id == DefaultContour.ID

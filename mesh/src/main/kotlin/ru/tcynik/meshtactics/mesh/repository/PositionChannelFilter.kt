package ru.tcynik.meshtactics.mesh.repository

/** Decides whether an incoming position packet on [channel] should be accepted and stored. */
interface PositionChannelFilter {
    fun isChannelAccepted(channel: Int): Boolean
}

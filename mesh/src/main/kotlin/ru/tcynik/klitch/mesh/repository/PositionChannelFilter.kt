// Copyright (c) 2025 tCynik — modifications under GPL-3.0

package ru.tcynik.klitch.mesh.repository

/** Decides whether an incoming position packet on [channel] should be accepted and stored. */
interface PositionChannelFilter {
    fun isChannelAccepted(channel: Int): Boolean
}

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
package ru.tcynik.klitch.mesh.service

/**
 * Canonical position-cadence constants for the `mesh` module — `app` reads them directly
 * (allowed by the one-way `app` → `mesh` dependency). Single source of truth for both
 * broadcast scenarios (PHONE_GPS app-driven heartbeat in `AndroidMeshLocationManager`, NODE_GPS
 * firmware preset in `BackgroundPositionSession`) and for the map staleness threshold
 * (`ObserveNodeMarkersUseCase`) — see `docs/plans/position-broadcast-interval-unification.md`.
 */
object PositionTrackingPolicy {
    /** Heartbeat interval while stationary, in seconds — both PHONE_GPS and NODE_GPS send at this cadence at minimum. */
    const val STATIONARY_INTERVAL_SECS = 180

    /** Minimum gate between sends while moving, in seconds — no send faster than this even on motion. */
    const val MOBILE_MIN_GATE_SECS = 30

    /** A node is considered stale after missing this many consecutive expected heartbeats. */
    const val STALENESS_MULTIPLIER = 3
}

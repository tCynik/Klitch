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
 * Canonical position-cadence constant for the `mesh` module — `app` reads it directly
 * (allowed by the one-way `app` → `mesh` dependency). Only `STATIONARY_INTERVAL_SECS` exists
 * for now (NODE_GPS broadcast preset); other cadence values stay where they are until
 * `docs/plans/position-broadcast-interval-unification.md` derives them from here too.
 */
object PositionTrackingPolicy {
    const val STATIONARY_INTERVAL_SECS = 180
}

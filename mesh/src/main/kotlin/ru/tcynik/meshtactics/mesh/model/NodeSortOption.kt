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
package ru.tcynik.meshtactics.mesh.model

import ru.tcynik.meshtactics.mesh.resources.StringResource
import ru.tcynik.meshtactics.mesh.resources.Res
import ru.tcynik.meshtactics.mesh.resources.node_sort_alpha
import ru.tcynik.meshtactics.mesh.resources.node_sort_channel
import ru.tcynik.meshtactics.mesh.resources.node_sort_distance
import ru.tcynik.meshtactics.mesh.resources.node_sort_hops_away
import ru.tcynik.meshtactics.mesh.resources.node_sort_last_heard
import ru.tcynik.meshtactics.mesh.resources.node_sort_via_favorite
import ru.tcynik.meshtactics.mesh.resources.node_sort_via_mqtt

enum class NodeSortOption(val sqlValue: String, val stringRes: StringResource) {
    LAST_HEARD("last_heard", Res.string.node_sort_last_heard),
    ALPHABETICAL("alpha", Res.string.node_sort_alpha),
    DISTANCE("distance", Res.string.node_sort_distance),
    HOPS_AWAY("hops_away", Res.string.node_sort_hops_away),
    CHANNEL("channel", Res.string.node_sort_channel),
    VIA_MQTT("via_mqtt", Res.string.node_sort_via_mqtt),
    VIA_FAVORITE("via_favorite", Res.string.node_sort_via_favorite),
}

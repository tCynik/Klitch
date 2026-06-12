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
package ru.tcynik.klitch.mesh.common.util

import android.net.Uri

class CommonUri(private val uri: Uri) {
    val host: String?
        get() = uri.host

    val fragment: String?
        get() = uri.fragment

    val pathSegments: List<String>
        get() = uri.pathSegments

    fun getQueryParameter(key: String): String? = uri.getQueryParameter(key)

    fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean =
        uri.getBooleanQueryParameter(key, defaultValue)

    override fun toString(): String = uri.toString()

    companion object {
        fun parse(uriString: String): CommonUri = CommonUri(Uri.parse(uriString))
    }

    fun toUri(): Uri = uri
}

fun CommonUri.toPlatformUri(): Any = this.toUri()

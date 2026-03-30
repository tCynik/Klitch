package ru.tcynik.mymesh1.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NodeDto(
    @SerialName("id")           val id: String,
    @SerialName("name")         val name: String,
    @SerialName("address")      val address: String,
    @SerialName("rssi")         val rssi: Int,
    @SerialName("is_connected") val isConnected: Boolean,
    @SerialName("last_seen")    val lastSeen: Long,   // epoch millis
)

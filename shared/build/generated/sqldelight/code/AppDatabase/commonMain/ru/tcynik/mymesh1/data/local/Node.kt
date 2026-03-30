package ru.tcynik.mymesh1.`data`.local

import kotlin.Long
import kotlin.String

public data class Node(
  public val id: String,
  public val name: String,
  public val address: String,
  public val rssi: Long,
  public val is_connected: Long,
  public val last_seen: Long,
)

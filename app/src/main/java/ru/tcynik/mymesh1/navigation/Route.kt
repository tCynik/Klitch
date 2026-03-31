package ru.tcynik.mymesh1.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object Nodes : Route

    @Serializable
    data class NodeDetail(val nodeId: String) : Route

    @Serializable
    data class MeshTest(val nodeId: String) : Route
}

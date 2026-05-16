package ru.tcynik.meshtactics.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object Main : Route

    @Serializable
    data object Chat : Route

    @Serializable
    data object MainSettings : Route

    @Serializable
    data object MapSettings : Route

    @Serializable
    data object DisplaySettings : Route

    @Serializable
    data object UserSettings : Route

    @Serializable
    data object NodeSettings : Route

    @Serializable
    data object NodeStatus : Route

    @Serializable
    data object MarkerManagement : Route

    @Serializable
    data object GroupManagement : Route  // Beta 1.0

    // Legacy / prototype screens
    @Serializable
    data object Nodes : Route

    @Serializable
    data class NodeDetail(val nodeId: String) : Route

    @Serializable
    data class MeshTest(val nodeId: String = "") : Route
}

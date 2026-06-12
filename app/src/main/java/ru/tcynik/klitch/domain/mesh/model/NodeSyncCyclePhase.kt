package ru.tcynik.klitch.domain.mesh.model

enum class NodeSyncCyclePhase {
    Idle,
    Syncing,
    Rebooting,
    WaitingForNode,
}

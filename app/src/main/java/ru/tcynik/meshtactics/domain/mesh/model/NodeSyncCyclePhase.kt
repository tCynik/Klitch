package ru.tcynik.meshtactics.domain.mesh.model

enum class NodeSyncCyclePhase {
    Idle,
    Syncing,
    Rebooting,
    WaitingForNode,
}

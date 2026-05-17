package ru.tcynik.meshtactics.domain.mesh.model

data class NodeSecurityModel(
    val publicKeyHex: String,
    val hasKey: Boolean,
    val isMismatch: Boolean,
)

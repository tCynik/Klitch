package ru.tcynik.meshtactics.domain.channel.model

data class MeshtasticBinding(
    val psk: ByteArray,
    val resolvedSlot: Int? = null,
) : TransportBinding {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshtasticBinding) return false
        return psk.contentEquals(other.psk)
    }

    override fun hashCode(): Int = psk.contentHashCode()
}

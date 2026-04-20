package ru.tcynik.meshtactics.domain.channel.model

data class MeshtasticBinding(
    val channelIndex: Int,
    val psk: ByteArray,
) : TransportBinding {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshtasticBinding) return false
        return channelIndex == other.channelIndex && psk.contentEquals(other.psk)
    }

    override fun hashCode(): Int {
        var result = channelIndex
        result = 31 * result + psk.contentHashCode()
        return result
    }
}

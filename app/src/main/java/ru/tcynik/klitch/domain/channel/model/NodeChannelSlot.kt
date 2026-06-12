package ru.tcynik.klitch.domain.channel.model

data class NodeChannelSlot(
    val index: Int,
    val name: String,
    val psk: ByteArray,
    val isEnabled: Boolean,
    val positionPrecision: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NodeChannelSlot) return false
        return index == other.index && name == other.name && psk.contentEquals(other.psk) && isEnabled == other.isEnabled && positionPrecision == other.positionPrecision
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + name.hashCode()
        result = 31 * result + psk.contentHashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + positionPrecision
        return result
    }
}

package ru.tcynik.meshtactics.domain.channel.model

import java.security.MessageDigest

@JvmInline
value class LogicalChannelHash(val value: String) {
    companion object {
        fun compute(name: String, psk: ByteArray): LogicalChannelHash {
            val input = name.lowercase().toByteArray() + ":".toByteArray() + psk
            val digest = MessageDigest.getInstance("SHA-256").digest(input)
            val hex = digest.take(8).joinToString("") { "%02x".format(it) }
            return LogicalChannelHash(hex)
        }
    }
}

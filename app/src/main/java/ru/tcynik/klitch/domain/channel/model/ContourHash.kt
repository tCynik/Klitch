package ru.tcynik.klitch.domain.channel.model

import java.security.MessageDigest
import java.util.Base64

@JvmInline
value class ContourHash(val value: String) {
    companion object {
        fun compute(name: String, psk: ByteArray): ContourHash {
            val input = name.toByteArray() + ":".toByteArray() + psk
            val digest = MessageDigest.getInstance("SHA-256").digest(input)
            val hex = digest.take(8).joinToString("") { "%02x".format(it) }
            return ContourHash(hex)
        }

        fun compute(name: String, pskBase64: String): ContourHash =
            compute(name, Base64.getDecoder().decode(pskBase64))

        fun computeForContour(contour: Contour): ContourHash =
            compute(
                meshtasticChannelName(contour),
                Base64.getDecoder().decode(contour.transport.meshtastic.psk),
            )
    }
}

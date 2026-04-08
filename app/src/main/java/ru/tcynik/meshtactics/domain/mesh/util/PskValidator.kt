package ru.tcynik.meshtactics.domain.mesh.util

import android.util.Base64

object PskValidator {

    private val VALID_LENGTHS = setOf(0, 1, 16, 32)

    sealed class Result {
        data class Valid(val bytes: ByteArray) : Result()
        data class Invalid(val reason: String) : Result()
    }

    fun validate(base64: String): Result {
        if (base64.isBlank()) {
            return Result.Valid(ByteArray(0))
        }
        val decoded = runCatching {
            Base64.decode(base64.trim(), Base64.DEFAULT)
        }.getOrNull() ?: return Result.Invalid("Not valid Base64")

        if (decoded.size == 1 && decoded[0] != 0x01.toByte()) {
            return Result.Invalid("Single-byte PSK must be 0x01 (default key)")
        }
        if (decoded.size !in VALID_LENGTHS) {
            return Result.Invalid("PSK must be 0, 1, 16, or 32 bytes (got ${decoded.size})")
        }
        return Result.Valid(decoded)
    }
}

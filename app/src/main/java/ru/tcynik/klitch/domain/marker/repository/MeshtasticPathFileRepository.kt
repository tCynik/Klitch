package ru.tcynik.klitch.domain.marker.repository

interface MeshtasticPathFileRepository {
    suspend fun export(markId: String, destinationUri: String): Result<Unit>
}

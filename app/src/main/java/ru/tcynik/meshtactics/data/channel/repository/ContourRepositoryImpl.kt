package ru.tcynik.meshtactics.data.channel.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.local.Contour
import ru.tcynik.meshtactics.data.local.ContourQueries
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import java.time.Instant
import java.util.Base64
import ru.tcynik.meshtactics.domain.channel.model.Contour as ContourDomain

class ContourRepositoryImpl(
    private val queries: ContourQueries,
) : ContourRepository {

    override fun observeContours(): Flow<List<ContourDomain>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.mapNotNull { it.toDomain(queries) } }

    override suspend fun saveContour(contour: ContourDomain) {
        val pskBase64 = contour.transport.meshtastic.psk
        val pskBytes = runCatching { Base64.getDecoder().decode(pskBase64) }.getOrNull()
        queries.upsert(
            id = contour.id.value,
            name = contour.name,
            meshtasticSlot = null,
            meshtasticPsk = pskBytes,
            channelHash = contour.transport.meshtastic.channelHash.value,
            isActive = if (contour.isActive) 1L else 0L,
            description = contour.description,
            expiration = contour.expiration?.epochSecond,
            exclusivityTime = contour.exclusivityTime?.epochSecond,
        )
    }

    override suspend fun deleteContour(id: ContourId) {
        queries.deleteById(id.value)
    }

    override suspend fun findByChannelHash(hash: ContourHash): ContourDomain? =
        queries.selectByChannelHash(hash.value)
            .executeAsOneOrNull()
            ?.toDomain(queries)
}

private fun Contour.toDomain(queries: ContourQueries): ContourDomain? {
    val pskBytes = meshtastic_psk ?: return null
    val pskBase64 = Base64.getEncoder().encodeToString(pskBytes)
    val hash = channel_hash?.let { ContourHash(it) }
        ?: ContourHash.compute(name, pskBytes).also { computed ->
            queries.updateChannelHash(channelHash = computed.value, id = id)
        }
    return ContourDomain(
        id = ContourId(id),
        name = name,
        description = description,
        expiration = expiration?.let { Instant.ofEpochSecond(it) },
        exclusivityTime = exclusivity_time?.let { Instant.ofEpochSecond(it) },
        isActive = is_active != 0L,
        transport = ContourTransport(
            meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash),
        ),
    )
}

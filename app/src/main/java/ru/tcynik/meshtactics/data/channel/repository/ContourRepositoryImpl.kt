package ru.tcynik.meshtactics.data.channel.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.local.Contour
import ru.tcynik.meshtactics.data.local.ContourQueries
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import java.time.Instant
import java.util.Base64
import ru.tcynik.meshtactics.domain.channel.model.Contour as ContourDomain

class ContourRepositoryImpl(
    private val queries: ContourQueries,
    private val dataStore: DataStore<Preferences>,
) : ContourRepository {

    override fun observeContours(): Flow<List<ContourDomain>> =
        combine(
            queries.selectAll().asFlow().mapToList(Dispatchers.Default),
            dataStore.data.map { prefs -> prefs[EMERGENCY_IS_ACTIVE_KEY] ?: DefaultContour.IS_ACTIVE_DEFAULT },
        ) { rows, emergencyIsActive ->
            val emergency = DefaultContour.asContour().copy(isActive = emergencyIsActive)
            val dbContours = rows.mapNotNull { it.toDomain(queries) }
            listOf(emergency) + dbContours
        }

    override fun observeEmergencyIsActive(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[EMERGENCY_IS_ACTIVE_KEY] ?: DefaultContour.IS_ACTIVE_DEFAULT }

    override suspend fun setEmergencyActive(isActive: Boolean) {
        dataStore.edit { it[EMERGENCY_IS_ACTIVE_KEY] = isActive }
    }

    override suspend fun seedDefaultsIfAbsent() {
        val rows = queries.selectAll().executeAsList()
        if (rows.none { it.id == DefaultActiveContour.ID.value }) {
            val pskBytes = Base64.getDecoder().decode(DefaultContour.OPEN_PSK)
            val hash = ContourHash.compute(DefaultActiveContour.DISPLAY_NAME, pskBytes)
            queries.upsert(
                id = DefaultActiveContour.ID.value,
                name = DefaultActiveContour.DISPLAY_NAME,
                meshtasticSlot = null,
                meshtasticPsk = pskBytes,
                channelHash = hash.value,
                isActive = 1L,
                description = DefaultActiveContour.DESCRIPTION,
                expiration = null,
                exclusivityTime = null,
            )
        }
    }

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

    override suspend fun findByChannelHash(hash: ContourHash): ContourDomain? {
        if (hash == DefaultContour.CHANNEL_HASH) {
            val isActive = dataStore.data
                .map { it[EMERGENCY_IS_ACTIVE_KEY] ?: DefaultContour.IS_ACTIVE_DEFAULT }
                .first()
            return DefaultContour.asContour().copy(isActive = isActive)
        }
        return queries.selectByChannelHash(hash.value)
            .executeAsOneOrNull()
            ?.toDomain(queries)
    }

    companion object {
        private val EMERGENCY_IS_ACTIVE_KEY = booleanPreferencesKey("emergency_is_active")
    }
}

private fun Contour.toDomain(queries: ContourQueries): ContourDomain? {
    val pskBytes = meshtastic_psk ?: return null
    val pskBase64 = Base64.getEncoder().encodeToString(pskBytes)
    val hash = ContourHash.compute(name, pskBytes).also { computed ->
        if (channel_hash != computed.value) {
            queries.updateChannelHash(channelHash = computed.value, id = id)
        }
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

package ru.tcynik.meshtactics.data.channel.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
import ru.tcynik.meshtactics.domain.channel.model.meshtasticChannelName
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import java.time.Instant
import java.util.Base64
import ru.tcynik.meshtactics.domain.channel.model.Contour as ContourDomain

class ContourRepositoryImpl(
    private val queries: ContourQueries,
    private val dataStore: DataStore<Preferences>,
) : ContourRepository {

    override fun observeContours(): Flow<List<ContourDomain>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { rows ->
            val emergency = DefaultContour.asContour().copy(isActive = true)
            val dbContours = rows.mapNotNull { it.toDomain(queries) }
            listOf(emergency) + dbContours
        }

    override fun observePrimaryContourId(): Flow<ContourId> =
        dataStore.data.map { prefs ->
            ContourId(prefs[PRIMARY_CONTOUR_ID_KEY] ?: DefaultActiveContour.ID.value)
        }

    override suspend fun getPrimaryContourId(): ContourId = observePrimaryContourId().first()

    override suspend fun setPrimaryContour(id: ContourId) {
        dataStore.edit { it[PRIMARY_CONTOUR_ID_KEY] = id.value }
    }

    override fun observeSosMode(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[SOS_MODE_ACTIVE_KEY] ?: false }

    override suspend fun setSosMode(active: Boolean) {
        dataStore.edit { it[SOS_MODE_ACTIVE_KEY] = active }
    }

    override suspend fun getPreSosPrimaryId(): ContourId? {
        val value = dataStore.data.first()[PRE_SOS_PRIMARY_ID_KEY] ?: return null
        return ContourId(value)
    }

    override suspend fun savePreSosPrimaryId(id: ContourId?) {
        dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(PRE_SOS_PRIMARY_ID_KEY)
            } else {
                prefs[PRE_SOS_PRIMARY_ID_KEY] = id.value
            }
        }
    }

    override suspend fun seedDefaultsIfAbsent() {
        val pskBytes = Base64.getDecoder().decode(DefaultActiveContour.DEFAULT_PSK)
        val hash = ContourHash.compute(
            meshtasticChannelName(DefaultActiveContour.ID, DefaultActiveContour.DISPLAY_NAME),
            pskBytes,
        )
        val existing = queries.selectAll().executeAsList().find { it.id == DefaultActiveContour.ID.value }
        queries.upsert(
            id = DefaultActiveContour.ID.value,
            name = DefaultActiveContour.DISPLAY_NAME,
            meshtasticSlot = null,
            meshtasticPsk = pskBytes,
            channelHash = hash.value,
            isActive = existing?.is_active ?: 1L,
            description = DefaultActiveContour.DESCRIPTION,
            expiration = null,
            exclusivityTime = null,
        )
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
            return DefaultContour.asContour().copy(isActive = true)
        }
        return queries.selectByChannelHash(hash.value)
            .executeAsOneOrNull()
            ?.toDomain(queries)
    }

    companion object {
        private val PRIMARY_CONTOUR_ID_KEY = stringPreferencesKey("primary_contour_id")
        private val SOS_MODE_ACTIVE_KEY = booleanPreferencesKey("sos_mode_active")
        private val PRE_SOS_PRIMARY_ID_KEY = stringPreferencesKey("pre_sos_primary_id")
    }
}

private fun Contour.toDomain(queries: ContourQueries): ContourDomain? {
    val pskBytes = meshtastic_psk ?: return null
    val pskBase64 = Base64.getEncoder().encodeToString(pskBytes)
    val hash = ContourHash.compute(meshtasticChannelName(ContourId(id), name), pskBytes).also { computed ->
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

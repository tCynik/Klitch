package ru.tcynik.meshtactics.data.channel.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.data.local.AppDatabase
import ru.tcynik.meshtactics.domain.channel.model.ChannelMetadata
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelHash
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding

class LogicalChannelRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: AppDatabase
    private lateinit var repo: LogicalChannelRepositoryImpl

    private val psk = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
    private val channelName = "LongFast"
    private val expectedHash = LogicalChannelHash.compute(channelName, psk)

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        db = AppDatabase(driver)
        repo = LogicalChannelRepositoryImpl(db.logicalChannelQueries)
    }

    @After
    fun tearDown() {
        driver.close()
    }

    // ── backfill ──────────────────────────────────────────────────────────────

    @Test
    fun `backfill — row with null channel_hash gets hash computed and persisted on first observe`() = runTest {
        // Simulate pre-migration row: insert with channel_hash = null
        db.logicalChannelQueries.upsert(
            id = "ch-1",
            name = channelName,
            meshtasticSlot = null,
            meshtasticPsk = psk,
            isAutoSync = 0L,
            channelHash = null,
        )

        // observeChannels triggers toDomain(), which calls updateChannelHash for null rows
        repo.observeChannels().test {
            val channels = awaitItem()
            assertEquals(1, channels.size)
            cancelAndIgnoreRemainingEvents()
        }

        // The DB row should now have the computed hash persisted
        val row = db.logicalChannelQueries.selectByChannelHash(expectedHash.value).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("ch-1", row!!.id)
        assertEquals(expectedHash.value, row.channel_hash)
    }

    @Test
    fun `backfill — row with existing channel_hash is not overwritten`() = runTest {
        val preexistingHash = expectedHash.value
        db.logicalChannelQueries.upsert(
            id = "ch-2",
            name = channelName,
            meshtasticSlot = null,
            meshtasticPsk = psk,
            isAutoSync = 0L,
            channelHash = preexistingHash,
        )

        repo.observeChannels().test {
            val channels = awaitItem()
            assertEquals(1, channels.size)
            cancelAndIgnoreRemainingEvents()
        }

        // Hash unchanged
        val row = db.logicalChannelQueries.selectByChannelHash(preexistingHash).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals(preexistingHash, row!!.channel_hash)
    }

    // ── saveChannel ───────────────────────────────────────────────────────────

    @Test
    fun `saveChannel — persists channel_hash derived from name and psk`() = runTest {
        val channel = makeChannel("ch-save", channelName, psk)
        repo.saveChannel(channel)

        val row = db.logicalChannelQueries.selectByChannelHash(expectedHash.value).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("ch-save", row!!.id)
        assertEquals(expectedHash.value, row.channel_hash)
    }

    @Test
    fun `saveChannel — channel without transport binding saves null channel_hash`() = runTest {
        val channel = LogicalChannel(
            id = LogicalChannelId("ch-no-transport"),
            metadata = ChannelMetadata(name = "NoTransport"),
            transports = emptyList(),
        )
        repo.saveChannel(channel)

        val row = db.logicalChannelQueries.selectAll().executeAsList()
            .firstOrNull { it.id == "ch-no-transport" }
        assertNotNull(row)
        assertNull(row!!.channel_hash)
    }

    @Test
    fun `saveChannel — meshtastic_slot is always saved as null (not persisted)`() = runTest {
        val channel = makeChannel("ch-slot", channelName, psk)
        repo.saveChannel(channel)

        val row = db.logicalChannelQueries.selectAll().executeAsList()
            .firstOrNull { it.id == "ch-slot" }
        assertNotNull(row)
        assertNull(row!!.meshtastic_slot)
    }

    // ── findByChannelHash ─────────────────────────────────────────────────────

    @Test
    fun `findByChannelHash — returns channel when hash matches`() = runTest {
        val channel = makeChannel("ch-find", channelName, psk)
        repo.saveChannel(channel)

        val found = repo.findByChannelHash(expectedHash)
        assertNotNull(found)
        assertEquals("ch-find", found!!.id.value)
    }

    @Test
    fun `findByChannelHash — returns null when hash does not exist`() = runTest {
        val missing = LogicalChannelHash.compute("DoesNotExist", byteArrayOf(0x00))
        val found = repo.findByChannelHash(missing)
        assertNull(found)
    }

    // ── observeChannels ───────────────────────────────────────────────────────

    @Test
    fun `observeChannels — emits empty list when table is empty`() = runTest {
        repo.observeChannels().test {
            val list = awaitItem()
            assertEquals(0, list.size)
            cancel()
        }
    }

    @Test
    fun `observeChannels — domain model has correct hash after saveChannel`() = runTest {
        val channel = makeChannel("ch-obs", channelName, psk)
        repo.saveChannel(channel)

        repo.observeChannels().test {
            val channels = awaitItem()
            assertEquals(1, channels.size)
            val binding = channels[0].transports.filterIsInstance<MeshtasticBinding>().first()
            assertEquals(expectedHash, binding.channelHash)
            cancel()
        }
    }

    // ── deleteChannel ─────────────────────────────────────────────────────────

    @Test
    fun `deleteChannel — removes row from table`() = runTest {
        val channel = makeChannel("ch-del", channelName, psk)
        repo.saveChannel(channel)

        repo.deleteChannel(LogicalChannelId("ch-del"))

        val rows = db.logicalChannelQueries.selectAll().executeAsList()
        assertEquals(0, rows.size)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeChannel(id: String, name: String, psk: ByteArray): LogicalChannel {
        val hash = LogicalChannelHash.compute(name, psk)
        return LogicalChannel(
            id = LogicalChannelId(id),
            metadata = ChannelMetadata(name = name),
            transports = listOf(MeshtasticBinding(psk = psk, channelHash = hash)),
        )
    }
}

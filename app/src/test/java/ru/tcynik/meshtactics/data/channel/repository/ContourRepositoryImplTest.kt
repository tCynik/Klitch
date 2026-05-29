package ru.tcynik.meshtactics.data.channel.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.tcynik.meshtactics.data.local.AppDatabase
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import java.io.File
import java.util.Base64
import java.util.UUID

class ContourRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: AppDatabase
    private lateinit var repo: ContourRepositoryImpl
    private lateinit var dataStoreScope: CoroutineScope

    private val psk = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
    private val pskBase64 = Base64.getEncoder().encodeToString(psk)
    private val channelName = "LongFast"
    private val expectedHash = ContourHash.compute(channelName, psk)

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        db = AppDatabase(driver)
        dataStoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val dataStoreFile = File(tempFolder.root, "${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile },
        )
        repo = ContourRepositoryImpl(db.contourQueries, dataStore)
    }

    @After
    fun tearDown() {
        driver.close()
        dataStoreScope.cancel()
    }

    // ── backfill ──────────────────────────────────────────────────────────────

    @Test
    fun `backfill — row with null channel_hash gets hash computed and persisted on first observe`() = runTest {
        db.contourQueries.upsert(
            id = "00000000-0000-0000-0000-000000000010",
            name = channelName,
            meshtasticSlot = null,
            meshtasticPsk = psk,
            channelHash = null,
            isActive = 1L,
            description = null,
            expiration = null,
            exclusivityTime = null,
        )

        repo.observeContours().test {
            val contours = awaitItem()
            // Emergency prepended + 1 DB row
            assertEquals(2, contours.size)
            assertEquals(DefaultContour.ID, contours[0].id)
            cancelAndIgnoreRemainingEvents()
        }

        val row = db.contourQueries.selectByChannelHash(expectedHash.value).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals(expectedHash.value, row!!.channel_hash)
    }

    @Test
    fun `backfill — row with existing channel_hash is not overwritten`() = runTest {
        val preexistingHash = expectedHash.value
        db.contourQueries.upsert(
            id = "00000000-0000-0000-0000-000000000011",
            name = channelName,
            meshtasticSlot = null,
            meshtasticPsk = psk,
            channelHash = preexistingHash,
            isActive = 1L,
            description = null,
            expiration = null,
            exclusivityTime = null,
        )

        repo.observeContours().test {
            val contours = awaitItem()
            // Emergency prepended + 1 DB row
            assertEquals(2, contours.size)
            assertEquals(DefaultContour.ID, contours[0].id)
            cancelAndIgnoreRemainingEvents()
        }

        val row = db.contourQueries.selectByChannelHash(preexistingHash).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals(preexistingHash, row!!.channel_hash)
    }

    // ── saveContour ───────────────────────────────────────────────────────────

    @Test
    fun `saveContour — persists channel_hash derived from name and psk`() = runTest {
        val contour = makeContour("00000000-0000-0000-0000-000000000020", channelName, psk)
        repo.saveContour(contour)

        val row = db.contourQueries.selectByChannelHash(expectedHash.value).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("00000000-0000-0000-0000-000000000020", row!!.id)
        assertEquals(expectedHash.value, row.channel_hash)
    }

    @Test
    fun `saveContour — isActive persisted correctly`() = runTest {
        val contour = makeContour("00000000-0000-0000-0000-000000000021", channelName, psk, isActive = false)
        repo.saveContour(contour)

        val row = db.contourQueries.selectAll().executeAsList()
            .firstOrNull { it.id == "00000000-0000-0000-0000-000000000021" }
        assertNotNull(row)
        assertEquals(0L, row!!.is_active)
    }

    // ── findByChannelHash ─────────────────────────────────────────────────────

    @Test
    fun `findByChannelHash — returns contour when hash matches`() = runTest {
        val contour = makeContour("00000000-0000-0000-0000-000000000030", channelName, psk)
        repo.saveContour(contour)

        val found = repo.findByChannelHash(expectedHash)
        assertNotNull(found)
        assertEquals("00000000-0000-0000-0000-000000000030", found!!.id.value)
    }

    @Test
    fun `findByChannelHash — returns null when hash does not exist`() = runTest {
        val missing = ContourHash.compute("DoesNotExist", byteArrayOf(0x00))
        val found = repo.findByChannelHash(missing)
        assertNull(found)
    }

    // ── observeContours ───────────────────────────────────────────────────────

    @Test
    fun `observeContours — emits Emergency only when table is empty`() = runTest {
        repo.observeContours().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(DefaultContour.ID, list[0].id)
            cancel()
        }
    }

    @Test
    fun `observeContours — domain model has correct hash after saveContour`() = runTest {
        val contour = makeContour("00000000-0000-0000-0000-000000000040", channelName, psk)
        repo.saveContour(contour)

        repo.observeContours().test {
            val contours = awaitItem()
            // Emergency at index 0, DB contour at index 1
            assertEquals(2, contours.size)
            assertEquals(expectedHash, contours[1].transport.meshtastic.channelHash)
            cancel()
        }
    }

    // ── deleteContour ─────────────────────────────────────────────────────────

    @Test
    fun `deleteContour — removes row from table`() = runTest {
        val id = "00000000-0000-0000-0000-000000000050"
        val contour = makeContour(id, channelName, psk)
        repo.saveContour(contour)

        repo.deleteContour(ContourId(id))

        val rows = db.contourQueries.selectAll().executeAsList()
        assertEquals(0, rows.size)
    }

    // ── seedDefaultsIfAbsent ──────────────────────────────────────────────────

    @Test
    fun `seedDefaultsIfAbsent — empty DB — creates DefaultActive row`() = runTest {
        repo.seedDefaultsIfAbsent()

        val rows = db.contourQueries.selectAll().executeAsList()
        assertEquals(1, rows.size)
        assertEquals(DefaultActiveContour.ID.value, rows[0].id)
        assertEquals(DefaultActiveContour.DISPLAY_NAME, rows[0].name)
        assertEquals(1L, rows[0].is_active)
    }

    @Test
    fun `seedDefaultsIfAbsent — DefaultActive already present — no duplicate`() = runTest {
        repo.seedDefaultsIfAbsent()
        repo.seedDefaultsIfAbsent()

        val rows = db.contourQueries.selectAll().executeAsList()
            .filter { it.id == DefaultActiveContour.ID.value }
        assertEquals(1, rows.size)
    }

    @Test
    fun `seedDefaultsIfAbsent — does not create Emergency row`() = runTest {
        repo.seedDefaultsIfAbsent()

        val rows = db.contourQueries.selectAll().executeAsList()
        assertEquals(0, rows.count { it.id == DefaultContour.ID.value })
    }

    // ── Primary + SOS mode ─────────────────────────────────────────────────────

    @Test
    fun `observePrimaryContourId — default is DefaultActiveContour`() = runTest {
        repo.observePrimaryContourId().test {
            assertEquals(DefaultActiveContour.ID, awaitItem())
            cancel()
        }
    }

    @Test
    fun `setPrimaryContour — observePrimaryContourId emits new id`() = runTest {
        val customId = ContourId("00000000-0000-0000-0000-000000000099")
        repo.setPrimaryContour(customId)

        repo.observePrimaryContourId().test {
            assertEquals(customId, awaitItem())
            cancel()
        }
    }

    @Test
    fun `setSosMode true — observeSosMode emits true`() = runTest {
        repo.setSosMode(true)

        repo.observeSosMode().test {
            assertEquals(true, awaitItem())
            cancel()
        }
    }

    @Test
    fun `observeSosMode — emits false by default`() = runTest {
        repo.observeSosMode().test {
            assertEquals(false, awaitItem())
            cancel()
        }
    }

    @Test
    fun `observeContours — Emergency isActive always true`() = runTest {
        repo.observeContours().test {
            val list = awaitItem()
            assertEquals(DefaultContour.ID, list[0].id)
            assertEquals(true, list[0].isActive)
            cancel()
        }
    }

    @Test
    fun `savePreSosPrimaryId — getPreSosPrimaryId returns saved id`() = runTest {
        repo.savePreSosPrimaryId(DefaultActiveContour.ID)
        assertEquals(DefaultActiveContour.ID, repo.getPreSosPrimaryId())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeContour(id: String, name: String, psk: ByteArray, isActive: Boolean = true): Contour {
        val pskBase64 = Base64.getEncoder().encodeToString(psk)
        val hash = ContourHash.compute(name, psk)
        return Contour(
            id = ContourId(id),
            name = name,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = isActive,
            transport = ContourTransport(
                meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash),
            ),
        )
    }
}

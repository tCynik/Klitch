package ru.tcynik.meshtactics.data.local.map

import android.content.ContentResolver
import android.content.Context
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.data.local.AppDatabase

class ImportedMapRepositoryImplTest {

    private lateinit var repo: ImportedMapRepositoryImpl
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        db = AppDatabase(driver)

        val contentResolver = mockk<ContentResolver>(relaxed = true)
        val context = mockk<Context> {
            every { this@mockk.contentResolver } returns contentResolver
        }

        repo = ImportedMapRepositoryImpl(context = context, queries = db.importedMapOverlayQueries, parser = mockk(relaxed = true))
    }

    // ── setSelected ──────────────────────────────────────────────────────────

    @Test
    fun `setSelected true persists is_selected=1 in database`() = runTest {
        db.importedMapOverlayQueries.insert(
            id = "id1", name = "test.kmz", uri = "content://test", createdAt = 1000L, isSelected = 0L,
        )

        repo.setSelected("id1", true)

        val row = db.importedMapOverlayQueries.selectById("id1").executeAsOneOrNull()
        assertEquals(1L, row?.is_selected)
    }

    @Test
    fun `setSelected false persists is_selected=0 in database`() = runTest {
        db.importedMapOverlayQueries.insert(
            id = "id2", name = "test.kmz", uri = "content://test", createdAt = 1000L, isSelected = 1L,
        )

        repo.setSelected("id2", false)

        val row = db.importedMapOverlayQueries.selectById("id2").executeAsOneOrNull()
        assertEquals(0L, row?.is_selected)
    }

    @Test
    fun `is_selected survives toggle round-trip`() = runTest {
        db.importedMapOverlayQueries.insert(
            id = "id3", name = "test.kmz", uri = "content://test", createdAt = 1000L, isSelected = 0L,
        )

        repo.setSelected("id3", true)
        repo.setSelected("id3", false)

        val row = db.importedMapOverlayQueries.selectById("id3").executeAsOneOrNull()
        assertEquals(0L, row?.is_selected)
    }

    // ── hide ─────────────────────────────────────────────────────────────────

    @Test
    fun `hide removes row from database`() = runTest {
        db.importedMapOverlayQueries.insert(
            id = "id4", name = "test.kmz", uri = "content://test", createdAt = 1000L, isSelected = 0L,
        )

        repo.hide("id4")

        assertNull(db.importedMapOverlayQueries.selectById("id4").executeAsOneOrNull())
    }

    @Test
    fun `hide on unknown id is a no-op`() = runTest {
        repo.hide("does-not-exist")
        assertTrue(db.importedMapOverlayQueries.selectAll().executeAsList().isEmpty())
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    fun `delete removes row from database`() = runTest {
        db.importedMapOverlayQueries.insert(
            id = "id5", name = "test.kml", uri = "content://test2", createdAt = 2000L, isSelected = 0L,
        )

        repo.delete("id5")

        assertNull(db.importedMapOverlayQueries.selectById("id5").executeAsOneOrNull())
    }

    // ── selectAll ordering ───────────────────────────────────────────────────

    @Test
    fun `selectAll returns items ordered by created_at descending`() = runTest {
        db.importedMapOverlayQueries.insert("older", "old.kmz", "uri://1", 1000L, 0L)
        db.importedMapOverlayQueries.insert("newer", "new.kmz", "uri://2", 9000L, 0L)

        val rows = db.importedMapOverlayQueries.selectAll().executeAsList()
        assertEquals("newer", rows[0].id)
        assertEquals("older", rows[1].id)
    }

    // ── multiple items independence ──────────────────────────────────────────

    @Test
    fun `setSelected on one item does not affect others`() = runTest {
        db.importedMapOverlayQueries.insert("a", "a.kmz", "uri://a", 1000L, 0L)
        db.importedMapOverlayQueries.insert("b", "b.kmz", "uri://b", 2000L, 0L)

        repo.setSelected("a", true)

        val rowA = db.importedMapOverlayQueries.selectById("a").executeAsOneOrNull()
        val rowB = db.importedMapOverlayQueries.selectById("b").executeAsOneOrNull()
        assertEquals(1L, rowA?.is_selected)
        assertEquals(0L, rowB?.is_selected)
    }
}

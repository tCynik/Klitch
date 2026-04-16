package ru.tcynik.meshtactics.data.local.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.tcynik.meshtactics.data.local.ImportedMapOverlayQueries
import ru.tcynik.meshtactics.domain.map.model.ImportedMapOverlay
import ru.tcynik.meshtactics.domain.map.repository.ImportedMapRepository
import java.util.UUID

class ImportedMapRepositoryImpl(
    private val context: Context,
    private val queries: ImportedMapOverlayQueries,
    private val parser: KmlOverlayParser,
) : ImportedMapRepository {

    override fun observeAll(): Flow<List<ImportedMapOverlay>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeSelected(): Flow<List<ImportedMapOverlay>> =
        queries.selectSelected()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun import(uri: String, name: String, createdAt: Long) {
        val parsedUri = Uri.parse(uri)
        context.contentResolver.takePersistableUriPermission(
            parsedUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        withContext(Dispatchers.IO) {
            val fileDate = queryFileDate(parsedUri) ?: createdAt
            val id = UUID.randomUUID().toString()
            queries.insert(
                id = id,
                name = name,
                uri = uri,
                createdAt = fileDate,
                isSelected = 0L,
            )
            val result = parser.parse(id, parsedUri)
            queries.updateParsedPaths(
                geoJsonPath = result.geoJsonPath,
                groundOverlayPath = result.groundOverlayPath,
                id = id,
            )
        }
    }

    override suspend fun hide(id: String) {
        withContext(Dispatchers.IO) {
            val row = queries.selectById(id).executeAsOneOrNull() ?: return@withContext
            releaseSafPermission(row.uri)
            queries.deleteById(id)
        }
    }

    override suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            val row = queries.selectById(id).executeAsOneOrNull() ?: return@withContext
            val uri = Uri.parse(row.uri)
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
                // Файл мог быть удалён вручную — продолжаем чистку БД
            }
            releaseSafPermission(row.uri)
            queries.deleteById(id)
        }
    }

    override suspend fun setSelected(id: String, selected: Boolean) {
        withContext(Dispatchers.IO) {
            queries.setSelected(isSelected = if (selected) 1L else 0L, id = id)
        }
    }

    private fun queryFileDate(uri: Uri): Long? = try {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idx >= 0) cursor.getLong(idx).takeIf { it > 0 } else null
            } else null
        }
    } catch (_: Exception) { null }

    private fun releaseSafPermission(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // Разрешение могло быть уже отозвано
        }
    }

    private fun ru.tcynik.meshtactics.data.local.ImportedMapOverlay.toDomain() = ImportedMapOverlay(
        id = id,
        name = name,
        uri = uri,
        createdAt = created_at,
        isSelected = is_selected != 0L,
        geoJsonPath = geo_json_path,
        groundOverlayPath = ground_overlay_path,
    )
}

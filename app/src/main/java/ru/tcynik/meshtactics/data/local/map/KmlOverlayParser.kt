package ru.tcynik.meshtactics.data.local.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import org.osmdroid.bonuspack.kml.KmlDocument
import org.osmdroid.bonuspack.kml.KmlFeature
import org.osmdroid.bonuspack.kml.KmlFolder
import org.osmdroid.bonuspack.kml.KmlGroundOverlay
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

private const val TAG = "KmlOverlayParser"

class KmlOverlayParser(private val context: Context) {

    data class ParseResult(
        val geoJsonPath: String?,
        val groundOverlayPath: String?,
    )

    fun parse(id: String, uri: Uri): ParseResult {
        Log.d(TAG, "parse: start id=$id uri=$uri")
        val outDir = File(context.filesDir, "overlays/$id").also { it.mkdirs() }
        Log.d(TAG, "parse: outDir=$outDir")
        val kmlDoc = KmlDocument()

        val uriPath = uri.path.orEmpty()
        val isKmz = uriPath.endsWith(".kmz", ignoreCase = true)
        Log.d(TAG, "parse: isKmz=$isKmz path=$uriPath")

        if (isKmz) {
            val kmlFile = extractKmzContents(uri, outDir)
            if (kmlFile == null) {
                Log.w(TAG, "parse: failed to extract KML from KMZ")
                return ParseResult(null, null)
            }
            Log.d(TAG, "parse: extracted KML file=$kmlFile")
            val ok = kmlDoc.parseKMLFile(kmlFile)
            Log.d(TAG, "parse: parseKMLFile result=$ok")
            // Загрузить изображения в GroundOverlay из извлечённых файлов
            loadGroundOverlayIcons(kmlDoc.mKmlRoot, outDir)
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val kmlFile = File(outDir, "source.kml")
                kmlFile.outputStream().use { stream.copyTo(it) }
                val kmlSize = kmlFile.length()
                Log.d(TAG, "parse: saved KML file=$kmlFile size=$kmlSize")
                val kmlContent = runCatching { kmlFile.readText().take(2000) }.getOrDefault("")
                Log.d(TAG, "parse: KML preview=$kmlContent")
                val ok = kmlDoc.parseKMLFile(kmlFile)
                Log.d(TAG, "parse: parseKMLFile result=$ok")
            } ?: return ParseResult(null, null).also {
                Log.w(TAG, "parse: failed to open input stream for uri=$uri")
            }
        }

        Log.d(TAG, "parse: mKmlRoot=${kmlDoc.mKmlRoot?.javaClass?.simpleName}")
        logKmlStructure(kmlDoc.mKmlRoot, indent = 0)

        val geoJsonPath = saveGeoJson(kmlDoc, outDir)
        val groundOverlayPath = saveGroundOverlay(kmlDoc, outDir)

        Log.d(TAG, "parse: geoJsonPath=$geoJsonPath")
        Log.d(TAG, "parse: groundOverlayPath=$groundOverlayPath")

        return ParseResult(
            geoJsonPath = geoJsonPath,
            groundOverlayPath = groundOverlayPath,
        )
    }

    /**
     * Извлекает все файлы из KMZ архива. Возвращает путь к .kml файлу.
     */
    private fun extractKmzContents(uri: Uri, outDir: File): File? {
        Log.d(TAG, "extractKmzContents: start uri=$uri")
        var kmlFile: File? = null
        var entriesCount = 0

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entriesCount++
                    Log.d(TAG, "extractKmzContents: entry=${entry.name} dir=${entry.isDirectory}")
                    if (!entry.isDirectory) {
                        val targetFile = File(outDir, entry.name)
                        // Защита от path traversal
                        if (targetFile.absolutePath.startsWith(outDir.absolutePath)) {
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { out -> zip.copyTo(out) }
                            Log.d(TAG, "extractKmzContents: saved ${targetFile.absolutePath} size=${targetFile.length()}")
                            if (entry.name.endsWith(".kml", ignoreCase = true)) {
                                kmlFile = targetFile
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } ?: run {
            Log.w(TAG, "extractKmzContents: null input stream")
            return null
        }

        Log.d(TAG, "extractKmzContents: total entries=$entriesCount kmlFile=$kmlFile")
        return kmlFile
    }

    /**
     * Рекурсивно ищет KmlGroundOverlay и загружает изображение из локального файла.
     */
    private fun loadGroundOverlayIcons(feature: KmlFeature?, outDir: File) {
        if (feature == null) return
        if (feature is KmlGroundOverlay) {
            val href = feature.mIconHref
            if (feature.mIcon == null && href != null && href.isNotBlank()) {
                // Нормализуем путь: убираем префиксы вроде "files/", "./"
                val cleanPath = href
                    .removePrefix("files/")
                    .removePrefix("./")
                    .removePrefix("/")

                // Try candidates in priority order: exact href → stripped path → recursive search
                val candidates = listOf(
                    File(outDir, href),
                    File(outDir, cleanPath),
                ).distinct().firstOrNull { it.exists() }
                    ?: outDir.walkTopDown().firstOrNull { f ->
                        !f.isDirectory && (f.name == File(href).name || f.name == File(cleanPath).name)
                    }

                Log.d(TAG, "loadGroundOverlayIcons: candidate=$candidates (href=$href)")
                if (candidates != null) {
                    val bitmap = BitmapFactory.decodeFile(candidates.absolutePath)
                    if (bitmap != null) {
                        feature.mIcon = bitmap
                        Log.d(TAG, "loadGroundOverlayIcons: icon loaded size=${bitmap.byteCount}")
                    } else {
                        Log.w(TAG, "loadGroundOverlayIcons: BitmapFactory returned null for $candidates")
                    }
                } else {
                    Log.w(TAG, "loadGroundOverlayIcons: no image found for href=$href in $outDir")
                }
            }
        }
        if (feature is KmlFolder) {
            for (child in feature.mItems) {
                loadGroundOverlayIcons(child, outDir)
            }
        }
    }

    private fun saveGeoJson(doc: KmlDocument, outDir: File): String? {
        return try {
            val file = File(outDir, "features.geojson")
            Log.d(TAG, "saveGeoJson: saving to $file")
            Log.d(TAG, "saveGeoJson: kmlRoot=${doc.mKmlRoot} kmlRoot type=${doc.mKmlRoot?.javaClass?.simpleName}")
            val success = doc.saveAsGeoJSON(file)
            if (!success) {
                Log.w(TAG, "saveGeoJson: saveAsGeoJSON returned false")
                return null
            }
            Log.d(TAG, "saveGeoJson: saved successfully size=${file.length()}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "saveGeoJson: exception", e)
            null
        }
    }

    private fun saveGroundOverlay(doc: KmlDocument, outDir: File): String? {
        Log.d(TAG, "saveGroundOverlay: start")
        val overlays = findAllGroundOverlays(doc.mKmlRoot).filter { it.mIcon != null }
        Log.d(TAG, "saveGroundOverlay: found ${overlays.size} overlay(s) with bitmaps")

        if (overlays.isEmpty()) {
            Log.d(TAG, "saveGroundOverlay: no GroundOverlay with bitmap found in KML")
            return null
        }

        return try {
            val bitmap: Bitmap
            val north: Double
            val south: Double
            val east: Double
            val west: Double

            if (overlays.size == 1) {
                val overlay = overlays[0]
                bitmap = overlay.mIcon!!
                val bounds = overlay.getBoundingBox()
                north = bounds.latNorth
                south = bounds.latSouth
                east  = bounds.lonEast
                west  = bounds.lonWest
                Log.d(TAG, "saveGroundOverlay: single tile bounds N=$north S=$south E=$east W=$west")
            } else {
                // Multiple tiles — merge into one bitmap to cover the full geographic extent.
                val allBounds = overlays.map { it.getBoundingBox() }
                north = allBounds.maxOf { it.latNorth }
                south = allBounds.minOf { it.latSouth }
                east  = allBounds.maxOf { it.lonEast }
                west  = allBounds.minOf { it.lonWest }
                Log.d(TAG, "saveGroundOverlay: merging ${overlays.size} tiles into bounds N=$north S=$south E=$east W=$west")

                val lonSpan = east - west
                val latSpan = north - south

                // Scale merged canvas so the longest side is at most MAX_TEXTURE_SIZE.
                val MAX_TEXTURE_SIZE = 4096
                val outWidth: Int
                val outHeight: Int
                if (lonSpan >= latSpan) {
                    outWidth  = MAX_TEXTURE_SIZE
                    outHeight = (MAX_TEXTURE_SIZE * latSpan / lonSpan).toInt().coerceAtLeast(1)
                } else {
                    outHeight = MAX_TEXTURE_SIZE
                    outWidth  = (MAX_TEXTURE_SIZE * lonSpan / latSpan).toInt().coerceAtLeast(1)
                }

                val merged = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(merged)

                for (overlay in overlays) {
                    val b = overlay.getBoundingBox()
                    val left   = ((b.lonWest  - west)  / lonSpan * outWidth).toFloat()
                    val top    = ((north - b.latNorth)  / latSpan * outHeight).toFloat()
                    val right  = ((b.lonEast  - west)  / lonSpan * outWidth).toFloat()
                    val bottom = ((north - b.latSouth)  / latSpan * outHeight).toFloat()
                    canvas.drawBitmap(overlay.mIcon!!, null, RectF(left, top, right, bottom), null)
                }
                bitmap = merged
            }

            val file = File(outDir, "ground_overlay.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "saveGroundOverlay: saved bitmap size=${file.length()}")

            val boundsJson = GroundOverlayBoundsJson(
                north = north, south = south, east = east, west = west,
            )
            File(outDir, "ground_overlay_bounds.json").writeText(boundsJson.toJson())
            Log.d(TAG, "saveGroundOverlay: saved bounds json")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "saveGroundOverlay: exception", e)
            null
        }
    }

    private fun findAllGroundOverlays(feature: KmlFeature?): List<KmlGroundOverlay> {
        if (feature == null) return emptyList()
        return when (feature) {
            is KmlGroundOverlay -> {
                Log.d(TAG, "findAllGroundOverlays: found overlay mIcon=${feature.mIcon != null} href=${feature.mIconHref} coords=${feature.mCoordinates?.size}")
                listOf(feature)
            }
            is KmlFolder -> feature.mItems.flatMap { findAllGroundOverlays(it) }
            else -> emptyList()
        }
    }

    private fun logKmlStructure(feature: KmlFeature?, indent: Int) {
        if (feature == null) return
        val prefix = "  ".repeat(indent)
        when (feature) {
            is KmlFolder -> {
                Log.d(TAG, "${prefix}KmlFolder name=${feature.mName} children=${feature.mItems.size}")
                for (child in feature.mItems) {
                    logKmlStructure(child, indent + 1)
                }
            }
            is KmlGroundOverlay -> {
                Log.d(TAG, "${prefix}KmlGroundOverlay name=${feature.mName} icon=${feature.mIcon != null} iconHref=${feature.mIconHref}")
            }
            else -> {
                Log.d(TAG, "${prefix}${feature.javaClass.simpleName} name=${feature.mName}")
            }
        }
    }
}

private data class GroundOverlayBoundsJson(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
) {
    fun toJson(): String =
        """{"north":$north,"south":$south,"east":$east,"west":$west}"""
}

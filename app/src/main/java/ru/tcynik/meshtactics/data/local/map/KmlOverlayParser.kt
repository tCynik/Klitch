package ru.tcynik.meshtactics.data.local.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

                val imageFile = File(outDir, cleanPath)
                Log.d(TAG, "loadGroundOverlayIcons: trying to load icon from $imageFile (href=$href)")

                if (imageFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap != null) {
                        feature.mIcon = bitmap
                        Log.d(TAG, "loadGroundOverlayIcons: icon loaded successfully size=${bitmap.byteCount}")
                    } else {
                        Log.w(TAG, "loadGroundOverlayIcons: BitmapFactory returned null")
                    }
                } else {
                    Log.w(TAG, "loadGroundOverlayIcons: file not found, trying all images in outDir")
                    // Fallback: ищем любой jpg/png в outDir
                    val fallback = outDir.listFiles { _, name ->
                        name.endsWith(".jpg", ignoreCase = true) ||
                            name.endsWith(".jpeg", ignoreCase = true) ||
                            name.endsWith(".png", ignoreCase = true)
                    }?.firstOrNull()

                    if (fallback != null) {
                        val bitmap = BitmapFactory.decodeFile(fallback.absolutePath)
                        if (bitmap != null) {
                            feature.mIcon = bitmap
                            Log.d(TAG, "loadGroundOverlayIcons: loaded fallback icon from ${fallback.name}")
                        }
                    } else {
                        Log.w(TAG, "loadGroundOverlayIcons: no image files found in $outDir")
                    }
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
        val overlay = findGroundOverlay(doc.mKmlRoot)
        if (overlay == null) {
            Log.d(TAG, "saveGroundOverlay: no GroundOverlay found in KML")
            return null
        }
        Log.d(TAG, "saveGroundOverlay: found GroundOverlay icon=${overlay.mIcon != null}")
        val bitmap = overlay.mIcon ?: run {
            Log.w(TAG, "saveGroundOverlay: GroundOverlay has no bitmap")
            return null
        }
        return try {
            val file = File(outDir, "ground_overlay.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "saveGroundOverlay: saved bitmap size=${file.length()}")
            val bounds = overlay.getBoundingBox()
            Log.d(TAG, "saveGroundOverlay: bounds N=${bounds.latNorth} S=${bounds.latSouth} E=${bounds.lonEast} W=${bounds.lonWest}")
            val boundsJson = GroundOverlayBoundsJson(
                north = bounds.latNorth,
                south = bounds.latSouth,
                east = bounds.lonEast,
                west = bounds.lonWest,
            )
            File(outDir, "ground_overlay_bounds.json").writeText(boundsJson.toJson())
            Log.d(TAG, "saveGroundOverlay: saved bounds json")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "saveGroundOverlay: exception", e)
            null
        }
    }

    private fun findGroundOverlay(feature: KmlFeature?): KmlGroundOverlay? {
        if (feature == null) return null
        if (feature is KmlGroundOverlay) {
            Log.d(TAG, "findGroundOverlay: found at ${feature.javaClass.simpleName}")
            Log.d(TAG, "findGroundOverlay: mIcon=${feature.mIcon != null} mIconHref=${feature.mIconHref}")
            Log.d(TAG, "findGroundOverlay: mCoordinates=${feature.mCoordinates}")
            Log.d(TAG, "findGroundOverlay: mRotation=${feature.mRotation} mColor=0x${feature.mColor.toString(16)}")
            return feature
        }
        if (feature is KmlFolder) {
            Log.d(TAG, "findGroundOverlay: searching in KmlFolder children=${feature.mItems.size}")
            for (child in feature.mItems) {
                val result = findGroundOverlay(child)
                if (result != null) return result
            }
        }
        return null
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

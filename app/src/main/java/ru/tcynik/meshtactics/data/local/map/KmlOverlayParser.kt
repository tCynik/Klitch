package ru.tcynik.meshtactics.data.local.map

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import org.osmdroid.bonuspack.kml.KmlDocument
import org.osmdroid.bonuspack.kml.KmlFeature
import org.osmdroid.bonuspack.kml.KmlFolder
import org.osmdroid.bonuspack.kml.KmlGroundOverlay
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class KmlOverlayParser(private val context: Context) {

    data class ParseResult(
        val geoJsonPath: String?,
        val groundOverlayPath: String?,
    )

    fun parse(id: String, uri: Uri): ParseResult {
        val outDir = File(context.filesDir, "overlays/$id").also { it.mkdirs() }
        val kmlDoc = KmlDocument()

        val uriPath = uri.path.orEmpty()
        val isKmz = uriPath.endsWith(".kmz", ignoreCase = true)

        if (isKmz) {
            val kmlFile = extractKmlFromKmz(uri, outDir) ?: return ParseResult(null, null)
            kmlDoc.parseKMLFile(kmlFile)
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val kmlFile = File(outDir, "source.kml")
                kmlFile.outputStream().use { stream.copyTo(it) }
                kmlDoc.parseKMLFile(kmlFile)
            } ?: return ParseResult(null, null)
        }

        val geoJsonPath = saveGeoJson(kmlDoc, outDir)
        val groundOverlayPath = saveGroundOverlay(kmlDoc, outDir)

        return ParseResult(
            geoJsonPath = geoJsonPath,
            groundOverlayPath = groundOverlayPath,
        )
    }

    private fun extractKmlFromKmz(uri: Uri, outDir: File): File? {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".kml", ignoreCase = true)) {
                        val kmlFile = File(outDir, "source.kml")
                        FileOutputStream(kmlFile).use { out -> zip.copyTo(out) }
                        return kmlFile
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }

    private fun saveGeoJson(doc: KmlDocument, outDir: File): String? {
        return try {
            val json = doc.asGeoJSON(false)?.toString() ?: return null
            val file = File(outDir, "features.geojson")
            file.writeText(json)
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun saveGroundOverlay(doc: KmlDocument, outDir: File): String? {
        val overlay = findGroundOverlay(doc.mKmlRoot) ?: return null
        val bitmap = overlay.mIcon ?: return null
        return try {
            val file = File(outDir, "ground_overlay.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val bounds = GroundOverlayBoundsJson(
                north = overlay.mNorth.toDouble(),
                south = overlay.mSouth.toDouble(),
                east = overlay.mEast.toDouble(),
                west = overlay.mWest.toDouble(),
            )
            File(outDir, "ground_overlay_bounds.json").writeText(bounds.toJson())
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun findGroundOverlay(feature: KmlFeature?): KmlGroundOverlay? {
        if (feature == null) return null
        if (feature is KmlGroundOverlay) return feature
        if (feature is KmlFolder) {
            for (child in feature.mItems) {
                val result = findGroundOverlay(child)
                if (result != null) return result
            }
        }
        return null
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

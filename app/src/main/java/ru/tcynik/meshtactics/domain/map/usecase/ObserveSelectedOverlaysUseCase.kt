package ru.tcynik.meshtactics.domain.map.usecase

import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import ru.tcynik.meshtactics.domain.map.repository.ImportedMapRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GroundOverlayBounds
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.OverlayRenderModel
import java.io.File

class ObserveSelectedOverlaysUseCase(
    private val repository: ImportedMapRepository,
) : FlowUseCase<NoParams, List<OverlayRenderModel>>() {

    override fun invoke(params: NoParams): Flow<List<OverlayRenderModel>> =
        repository.observeSelected().map { overlays ->
            overlays.mapNotNull { overlay ->
                val geoJson = overlay.geoJsonPath?.let { path ->
                    runCatching { File(path).readText() }.getOrNull()
                }
                val bitmap = overlay.groundOverlayPath?.let { path ->
                    runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                }
                val bounds = overlay.groundOverlayPath?.let { path ->
                    val boundsFile = File(path).resolveSibling("ground_overlay_bounds.json")
                    runCatching {
                        val json = JSONObject(boundsFile.readText())
                        GroundOverlayBounds(
                            north = json.getDouble("north"),
                            south = json.getDouble("south"),
                            east = json.getDouble("east"),
                            west = json.getDouble("west"),
                        )
                    }.getOrNull()
                }
                if (geoJson == null && bitmap == null) return@mapNotNull null
                OverlayRenderModel(
                    id = overlay.id,
                    geoJson = geoJson,
                    groundOverlayBitmap = bitmap,
                    groundOverlayBounds = bounds,
                )
            }
        }
}

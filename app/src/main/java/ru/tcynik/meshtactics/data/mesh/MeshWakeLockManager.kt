package ru.tcynik.meshtactics.data.mesh

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.tcynik.meshtactics.mesh.repository.GeoSendPolicy
import ru.tcynik.meshtactics.mesh.repository.UiPrefs

private const val WAKE_LOCK_TAG = "MeshTactics::GeoSession"
private const val WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L

class MeshWakeLockManager(
    context: Context,
    private val uiPrefs: UiPrefs,
    private val geoSendPolicy: GeoSendPolicy,
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        .apply { setReferenceCounted(false) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        uiPrefs.useWakeLock
            .combine(geoSendPolicy.observeAllowed()) { useWakeLock, geoAllowed ->
                useWakeLock && geoAllowed
            }
            .onEach { shouldHold ->
                if (shouldHold) {
                    if (!wakeLock.isHeld) wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
                } else {
                    if (wakeLock.isHeld) wakeLock.release()
                }
            }
            .launchIn(scope)
    }
}

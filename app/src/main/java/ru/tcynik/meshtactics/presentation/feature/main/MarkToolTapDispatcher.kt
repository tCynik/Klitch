package ru.tcynik.meshtactics.presentation.feature.main

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Defers single-tap callbacks until the system double-tap window expires.
 * Prevents a lone tap from being mistaken for the first half of a double-tap.
 */
internal class MarkToolTapDispatcher(
    private val scope: CoroutineScope,
    private val doubleTapTimeoutMs: Long,
    private val onSingleTap: (lat: Double, lon: Double) -> Unit,
    private val onDoubleTap: (lat: Double, lon: Double) -> Unit,
) {
    private var lastTapUptimeMs = 0L
    private var pendingSingleTapJob: Job? = null

    fun reset() {
        lastTapUptimeMs = 0L
        pendingSingleTapJob?.cancel()
        pendingSingleTapJob = null
    }

    fun onTapRelease(lat: Double, lon: Double) {
        val now = SystemClock.uptimeMillis()
        if (lastTapUptimeMs > 0L && now - lastTapUptimeMs < doubleTapTimeoutMs) {
            pendingSingleTapJob?.cancel()
            pendingSingleTapJob = null
            lastTapUptimeMs = 0L
            onDoubleTap(lat, lon)
        } else {
            lastTapUptimeMs = now
            pendingSingleTapJob?.cancel()
            pendingSingleTapJob = scope.launch {
                delay(doubleTapTimeoutMs)
                pendingSingleTapJob = null
                lastTapUptimeMs = 0L
                onSingleTap(lat, lon)
            }
        }
    }
}

package ru.tcynik.meshtactics.domain.settings.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.settings.model.ScreenOrientationMode

interface ScreenOrientationRepository {
    fun getOrientationLocked(): Boolean
    fun setOrientationLocked(locked: Boolean)
    fun getOrientationMode(): ScreenOrientationMode
    fun setOrientationMode(mode: ScreenOrientationMode)
    fun observeOrientationSettings(): Flow<Pair<Boolean, ScreenOrientationMode>>
}

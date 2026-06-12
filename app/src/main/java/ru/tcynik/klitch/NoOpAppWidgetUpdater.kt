package ru.tcynik.klitch

import ru.tcynik.klitch.mesh.repository.AppWidgetUpdater

class NoOpAppWidgetUpdater : AppWidgetUpdater {
    override suspend fun updateAll() = Unit
}

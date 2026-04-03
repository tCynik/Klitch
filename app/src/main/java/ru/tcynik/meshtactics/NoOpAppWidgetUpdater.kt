package ru.tcynik.meshtactics

import ru.tcynik.meshtactics.mesh.repository.AppWidgetUpdater

class NoOpAppWidgetUpdater : AppWidgetUpdater {
    override suspend fun updateAll() = Unit
}

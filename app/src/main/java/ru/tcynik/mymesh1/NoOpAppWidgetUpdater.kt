package ru.tcynik.mymesh1

import ru.tcynik.mymesh1.mesh.repository.AppWidgetUpdater

class NoOpAppWidgetUpdater : AppWidgetUpdater {
    override suspend fun updateAll() = Unit
}

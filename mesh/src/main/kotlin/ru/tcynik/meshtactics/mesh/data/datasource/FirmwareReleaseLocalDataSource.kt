/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package ru.tcynik.meshtactics.mesh.data.datasource

import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import ru.tcynik.meshtactics.mesh.database.DatabaseProvider
import ru.tcynik.meshtactics.mesh.database.entity.FirmwareReleaseEntity
import ru.tcynik.meshtactics.mesh.database.entity.FirmwareReleaseType
import ru.tcynik.meshtactics.mesh.database.entity.asDeviceVersion
import ru.tcynik.meshtactics.mesh.database.entity.asEntity
import ru.tcynik.meshtactics.mesh.di.CoroutineDispatchers
import ru.tcynik.meshtactics.mesh.model.NetworkFirmwareRelease

@Single
class FirmwareReleaseLocalDataSource(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
) {
    private val firmwareReleaseDao
        get() = dbManager.currentDb.value.firmwareReleaseDao()

    suspend fun insertFirmwareReleases(
        firmwareReleases: List<NetworkFirmwareRelease>,
        releaseType: FirmwareReleaseType,
    ) = withContext(dispatchers.io) {
        firmwareReleases.forEach { firmwareRelease ->
            firmwareReleaseDao.insert(firmwareRelease.asEntity(releaseType))
        }
    }

    suspend fun deleteAllFirmwareReleases() = withContext(dispatchers.io) { firmwareReleaseDao.deleteAll() }

    suspend fun getLatestRelease(releaseType: FirmwareReleaseType): FirmwareReleaseEntity? =
        withContext(dispatchers.io) {
            val releases = firmwareReleaseDao.getReleasesByType(releaseType)
            if (releases.isEmpty()) {
                return@withContext null
            } else {
                val latestRelease = releases.maxBy { it.asDeviceVersion() }
                return@withContext latestRelease
            }
        }
}

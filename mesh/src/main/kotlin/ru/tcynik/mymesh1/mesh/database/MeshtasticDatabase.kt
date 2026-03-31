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
package ru.tcynik.mymesh1.mesh.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.tcynik.mymesh1.mesh.common.util.ioDispatcher
import ru.tcynik.mymesh1.mesh.database.dao.DeviceHardwareDao
import ru.tcynik.mymesh1.mesh.database.dao.FirmwareReleaseDao
import ru.tcynik.mymesh1.mesh.database.dao.MeshLogDao
import ru.tcynik.mymesh1.mesh.database.dao.NodeInfoDao
import ru.tcynik.mymesh1.mesh.database.dao.PacketDao
import ru.tcynik.mymesh1.mesh.database.dao.QuickChatActionDao
import ru.tcynik.mymesh1.mesh.database.dao.TracerouteNodePositionDao
import ru.tcynik.mymesh1.mesh.database.entity.ContactSettings
import ru.tcynik.mymesh1.mesh.database.entity.DeviceHardwareEntity
import ru.tcynik.mymesh1.mesh.database.entity.FirmwareReleaseEntity
import ru.tcynik.mymesh1.mesh.database.entity.MeshLog
import ru.tcynik.mymesh1.mesh.database.entity.MetadataEntity
import ru.tcynik.mymesh1.mesh.database.entity.MyNodeEntity
import ru.tcynik.mymesh1.mesh.database.entity.NodeEntity
import ru.tcynik.mymesh1.mesh.database.entity.Packet
import ru.tcynik.mymesh1.mesh.database.entity.QuickChatAction
import ru.tcynik.mymesh1.mesh.database.entity.ReactionEntity
import ru.tcynik.mymesh1.mesh.database.entity.TracerouteNodePositionEntity

@Database(
    entities =
    [
        MyNodeEntity::class,
        NodeEntity::class,
        Packet::class,
        ContactSettings::class,
        MeshLog::class,
        QuickChatAction::class,
        ReactionEntity::class,
        MetadataEntity::class,
        DeviceHardwareEntity::class,
        FirmwareReleaseEntity::class,
        TracerouteNodePositionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MeshtasticDatabase : RoomDatabase() {
    abstract fun nodeInfoDao(): NodeInfoDao

    abstract fun packetDao(): PacketDao

    abstract fun meshLogDao(): MeshLogDao

    abstract fun quickChatActionDao(): QuickChatActionDao

    abstract fun deviceHardwareDao(): DeviceHardwareDao

    abstract fun firmwareReleaseDao(): FirmwareReleaseDao

    abstract fun tracerouteNodePositionDao(): TracerouteNodePositionDao

    companion object {
        /** Configures a [RoomDatabase.Builder] with standard settings for this project. */
        fun <T : RoomDatabase> RoomDatabase.Builder<T>.configureCommon(): RoomDatabase.Builder<T> =
            this.fallbackToDestructiveMigration().setQueryCoroutineContext(ioDispatcher)
    }
}


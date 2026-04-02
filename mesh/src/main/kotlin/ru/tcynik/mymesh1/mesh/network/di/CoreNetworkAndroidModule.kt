/*
 * Copyright (c) 2026 Meshtastic LLC
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
package ru.tcynik.mymesh1.mesh.network.di

import android.app.Application
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialProber
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import ru.tcynik.mymesh1.mesh.network.repository.ProbeTableProvider

@Module
@ComponentScan("ru.tcynik.mymesh1.mesh.network")
class CoreNetworkAndroidModule {

    @Single
    fun provideConnectivityManager(app: Application): ConnectivityManager =
        ContextCompat.getSystemService(app, ConnectivityManager::class.java)!!

    @Single
    fun provideNsdManager(app: Application): NsdManager =
        ContextCompat.getSystemService(app, NsdManager::class.java)!!

    @Single
    fun provideUsbManager(app: Application): UsbManager =
        ContextCompat.getSystemService(app, UsbManager::class.java)!!

    @Single
    fun provideUsbSerialProber(probeTableProvider: ProbeTableProvider): UsbSerialProber =
        UsbSerialProber(probeTableProvider.get())
}

package ru.tcynik.klitch

import ru.tcynik.klitch.BuildConfig
import ru.tcynik.klitch.mesh.common.BuildConfigProvider
import ru.tcynik.klitch.mesh.model.DeviceVersion

class AppBuildConfigProvider : BuildConfigProvider {
    override val isDebug: Boolean = BuildConfig.DEBUG
    override val applicationId: String = BuildConfig.APPLICATION_ID
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val versionName: String = BuildConfig.VERSION_NAME
    override val absoluteMinFwVersion: String = DeviceVersion.ABS_MIN_FW_VERSION
    override val minFwVersion: String = DeviceVersion.MIN_FW_VERSION
    override val mainActivityClass: Class<*> = MainActivity::class.java
}

package ru.tcynik.meshtactics

import ru.tcynik.meshtactics.BuildConfig
import ru.tcynik.meshtactics.mesh.common.BuildConfigProvider
import ru.tcynik.meshtactics.mesh.model.DeviceVersion

class AppBuildConfigProvider : BuildConfigProvider {
    override val isDebug: Boolean = BuildConfig.DEBUG
    override val applicationId: String = BuildConfig.APPLICATION_ID
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val versionName: String = BuildConfig.VERSION_NAME
    override val absoluteMinFwVersion: String = DeviceVersion.ABS_MIN_FW_VERSION
    override val minFwVersion: String = DeviceVersion.MIN_FW_VERSION
    override val mainActivityClass: Class<*> = MainActivity::class.java
}

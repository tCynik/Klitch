package ru.tcynik.mymesh1

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import ru.tcynik.mymesh1.di.androidModule
import ru.tcynik.mymesh1.di.commonModule
import ru.tcynik.mymesh1.di.presentationModule
import ru.tcynik.mymesh1.mesh.di.meshModule

class MyMeshApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MyMeshApplication)
            modules(commonModule, androidModule, presentationModule)
            modules(meshModule)
        }
    }
}

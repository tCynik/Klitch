package ru.tcynik.mymesh1

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
import org.koin.core.logger.Level
import ru.tcynik.mymesh1.di.androidModule
import ru.tcynik.mymesh1.di.commonModule
import ru.tcynik.mymesh1.di.meshDataModule
import ru.tcynik.mymesh1.di.presentationModule
import ru.tcynik.mymesh1.mesh.common.ContextServices
import ru.tcynik.mymesh1.mesh.di.meshModule
import ru.tcynik.mymesh1.mesh.service.MeshServiceOrchestrator

class MyMeshApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ContextServices.app = this
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MyMeshApplication)
            modules(commonModule, androidModule, meshModule, meshDataModule, presentationModule)
        }
        GlobalContext.get().get<MeshServiceOrchestrator>().start()
    }
}

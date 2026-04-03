package ru.tcynik.meshtactics

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import ru.tcynik.meshtactics.di.androidModule
import ru.tcynik.meshtactics.di.commonModule
import ru.tcynik.meshtactics.di.mapDataModule
import ru.tcynik.meshtactics.di.markerDataModule
import ru.tcynik.meshtactics.di.meshDataModule
import ru.tcynik.meshtactics.di.presentationModule
import ru.tcynik.meshtactics.di.userDataModule
import ru.tcynik.meshtactics.mesh.common.ContextServices
import ru.tcynik.meshtactics.mesh.di.meshModule
import ru.tcynik.meshtactics.mesh.service.MeshServiceOrchestrator

class MyMeshApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ContextServices.app = this
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MyMeshApplication)
            modules(
                commonModule,
                androidModule,
                meshModule,
                meshDataModule,
                mapDataModule,
                markerDataModule,
                userDataModule,
                presentationModule,
            )
        }
        GlobalContext.get().get<MeshServiceOrchestrator>().start()
    }
}

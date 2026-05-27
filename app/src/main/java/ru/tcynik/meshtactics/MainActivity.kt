package ru.tcynik.meshtactics

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import ru.tcynik.meshtactics.domain.settings.model.ScreenOrientationMode
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveScreenOrientationSettingsUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.navigation.NavGraph
import ru.tcynik.meshtactics.ui.theme.MeshTacticsTheme

class MainActivity : ComponentActivity() {

    private val observeScreenOrientationSettings: ObserveScreenOrientationSettingsUseCase by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        applyScreenOrientation()

        setContent {
            MeshTacticsTheme {
                NavGraph()
            }
        }
    }

    private fun applyScreenOrientation() {
        // TODO: replace with flow-based implementation when landscape orientation is implemented
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // TODO: uncomment and remove the hardcode above when landscape orientation is implemented
//        observeScreenOrientationSettings(NoParams)
//            .onEach { (locked, mode) ->
//                requestedOrientation = when {
//                    !locked -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
//                    mode == ScreenOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//                    mode == ScreenOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
//                }
//            }
//            .launchIn(lifecycleScope)
    }
}

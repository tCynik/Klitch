package ru.tcynik.mymesh1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.tcynik.mymesh1.navigation.NavGraph
import ru.tcynik.mymesh1.ui.theme.MyMesh1Theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyMesh1Theme {
                NavGraph()
            }
        }
    }
}

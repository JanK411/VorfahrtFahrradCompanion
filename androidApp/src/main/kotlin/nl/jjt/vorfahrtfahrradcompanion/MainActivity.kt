package nl.jjt.vorfahrtfahrradcompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nl.jjt.vorfahrtfahrradcompanion.util.di.androidModule

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(listOf(androidModule(applicationContext, window)))
        }
    }
}

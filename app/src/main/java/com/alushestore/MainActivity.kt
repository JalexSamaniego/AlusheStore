package com.alushestore

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.alushestore.ui.AlusheNavGraph
import com.alushestore.ui.theme.AlusheStoreTheme
import com.alushestore.updater.AppUpdateManager

class AlusheApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppUpdateManager.limpiarApksResiduales(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { it.remove() }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppUpdateManager.limpiarApksResiduales(this)

        setContent {
            AlusheStoreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlusheNavGraph()
                }
            }
        }
    }
}

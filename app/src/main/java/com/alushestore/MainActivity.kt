package com.alushestore

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alushestore.ui.AlusheNavGraph
import com.alushestore.ui.theme.AlusheStoreTheme
import com.alushestore.updater.AppUpdateManager
import com.alushestore.workers.AppUpdateWorker
import java.util.concurrent.TimeUnit

class AlusheApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppUpdateManager.limpiarApksResiduales(this)
        AppUpdateManager.initNotificationChannel(this)

        // Programar verificación periódica de actualizaciones cada 6 horas en segundo plano
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val updateWork = PeriodicWorkRequestBuilder<AppUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                AppUpdateWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                updateWork
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { it.remove() }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppUpdateManager.limpiarApksResiduales(this)

        // Manejar toque en la notificación de actualización
        if (intent?.getBooleanExtra(AppUpdateManager.EXTRA_SHOW_UPDATE, false) == true) {
            AppUpdateManager.pendingUpdatePrompt = true
        }

        // Solicitar permiso de notificaciones en Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra(AppUpdateManager.EXTRA_SHOW_UPDATE, false) == true) {
            AppUpdateManager.pendingUpdatePrompt = true
        }
    }
}

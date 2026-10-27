package com.verbilume.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.verbilume.app.ui.Routes
import com.verbilume.app.ui.VerbilumeApp

class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingRoute = routeFrom(intent)
        val graph = (application as VerbilumeApplication).graph
        setContent {
            VerbilumeApp(
                graph = graph,
                pendingRoute = pendingRoute,
                onRouteConsumed = { pendingRoute = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = routeFrom(intent)
    }

    private fun routeFrom(intent: Intent?): String? = when (intent?.getStringExtra("route")) {
        "practice" -> Routes.PRACTICE
        "learn" -> Routes.LEARN
        "daily" -> Routes.DAILY
        "stats" -> Routes.STATS
        else -> null
    }
}

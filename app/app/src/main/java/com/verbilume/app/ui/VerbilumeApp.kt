package com.verbilume.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.ThemeMode
import com.verbilume.app.ui.screens.MainShell
import com.verbilume.app.ui.screens.OnboardingScreen
import com.verbilume.app.ui.theme.ManropeFamily
import com.verbilume.app.ui.theme.VerbilumeTheme
import com.verbilume.app.ui.theme.LocalVerbiColors

@Composable
fun VerbilumeApp(
    graph: AppGraph,
    pendingRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
) {
    val settings by graph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val dark = settings?.theme == ThemeMode.DARK

    VerbilumeTheme(darkTheme = dark) {
        val current = settings
        when {
            current == null -> BrandLoad()
            !current.onboardingComplete -> OnboardingScreen(
                graph = graph,
                initial = current,
                onComplete = {},
            )
            else -> MainShell(
                graph = graph,
                settings = current,
                pendingRoute = pendingRoute,
                onRouteConsumed = onRouteConsumed,
            )
        }
    }
}

@Composable
private fun BrandLoad() {
    val colors = LocalVerbiColors.current
    val pulse by rememberInfiniteTransition(label = "brand").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "brandPulse",
    )
    PaperBackground(decorated = true) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            VerbiMark(Modifier.size(92.dp).scale(pulse), color = colors.ink, accent = colors.accent)
            Spacer(Modifier.height(30.dp))
            Text(
                "VERBILUME",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 7.sp,
                color = colors.ink,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Every word, a little light.",
                fontFamily = ManropeFamily,
                fontSize = 14.5.sp,
                color = colors.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.9f),
            )
        }
    }
}

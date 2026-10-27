package com.verbilume.app.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.verbilume.app.R

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = (LocalConfiguration.current.screenWidthDp - 28).coerceAtLeast(320)
    AndroidView(
        factory = { ctx ->
            AdView(ctx).apply {
                adUnitId = ctx.getString(R.string.admob_banner)
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier,
    )
}

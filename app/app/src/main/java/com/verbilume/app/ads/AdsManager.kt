package com.verbilume.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.verbilume.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdsManager(context: Context) {
    private val appContext = context.applicationContext

    private var adInitialized = false
    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false
    private var lastInterstitialAt = 0L

    @Volatile
    private var adsRemoved = false

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()

    fun setAdsRemoved(removed: Boolean) {
        adsRemoved = removed
    }

    fun initialize() {
        if (adInitialized) return
        adInitialized = true
        MobileAds.initialize(appContext) {
            loadInterstitial()
            loadRewarded()
        }
    }

    fun showInterstitial(activity: Activity?, onFinished: () -> Unit) {
        if (adsRemoved) {
            onFinished()
            return
        }
        initialize()
        if (activity == null || activity.isFinishing) {
            onFinished()
            return
        }
        val ad = interstitial
        if (ad == null || System.currentTimeMillis() - lastInterstitialAt < MIN_INTERSTITIAL_INTERVAL_MS) {
            loadInterstitial()
            onFinished()
            return
        }
        interstitial = null
        lastInterstitialAt = System.currentTimeMillis()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial()
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadInterstitial()
                onFinished()
            }
        }
        ad.show(activity)
    }

    fun showRewarded(activity: Activity?, onReward: () -> Unit, onUnavailable: () -> Unit) {
        initialize()
        val ad = rewarded
        if (activity == null || activity.isFinishing || ad == null) {
            loadRewarded()
            onUnavailable()
            return
        }
        rewarded = null
        _rewardedReady.value = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadRewarded()
            }
        }
        ad.show(activity) { onReward() }
    }

    private fun loadInterstitial() {
        if (adsRemoved || !adInitialized || interstitial != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            appContext,
            appContext.getString(R.string.admob_interstitial),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitial = null
                }
            },
        )
    }

    private fun loadRewarded() {
        if (!adInitialized || rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(
            appContext,
            appContext.getString(R.string.admob_rewarded),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    rewarded = ad
                    _rewardedReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    rewarded = null
                    _rewardedReady.value = false
                }
            },
        )
    }

    private companion object {
        const val MIN_INTERSTITIAL_INTERVAL_MS = 90_000L
    }
}

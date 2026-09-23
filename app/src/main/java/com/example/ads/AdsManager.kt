package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized Manager for Google Mobile Ads (GMA) Next-Gen SDK for Android.
 * Implements Banner, Interstitial, Rewarded, Rewarded Interstitial, App Open, and Native ads.
 */
object AdsManager {

    private const val TAG = "AdsManager"

    private val isInitializing = AtomicBoolean(false)
    var isInitialized = false
        private set

    // Fullscreen Ad Protection & Frequency Capping
    var isShowingFullScreenAd = false
        private set

    private var lastInterstitialShowTime = 0L
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 30_000L // 30 seconds cooldown

    // Cached Ad instances
    private var cachedInterstitialAd: InterstitialAd? = null
    private var cachedRewardedAd: RewardedAd? = null
    private var cachedRewardedInterstitialAd: RewardedInterstitialAd? = null
    private var cachedAppOpenAd: AppOpenAd? = null

    /**
     * Initializes the GMA Next-Gen SDK.
     */
    fun initialize(context: Context, onComplete: () -> Unit = {}) {
        if (isInitialized) {
            onComplete()
            return
        }
        if (isInitializing.getAndSet(true)) {
            return
        }

        Log.d(TAG, "Initializing GMA Next-Gen SDK...")
        val config = InitializationConfig.Builder(AdIds.appId).build()
        MobileAds.initialize(context.applicationContext, config)
        isInitialized = true
        isInitializing.set(false)
        Log.d(TAG, "GMA Next-Gen SDK initialized successfully")

        // Preload default full-screen ads for smooth UX
        preloadAds(context.applicationContext)
        onComplete()
    }

    /**
     * Preloads ads in the background.
     */
    fun preloadAds(context: Context) {
        if (!isInitialized) return
        loadInterstitial(context)
        loadRewarded(context)
        loadAppOpenAd(context)
    }

    // ---------------------------------------------------------------------------------------------
    // BANNER ADS
    // ---------------------------------------------------------------------------------------------

    fun loadBannerAd(
        context: Context,
        adUnitId: String = AdIds.bannerAdId,
        adSize: AdSize = AdSize.BANNER,
        onLoaded: (BannerAd) -> Unit = {},
        onFailed: (LoadAdError) -> Unit = {}
    ): AdView? {
        if (!isInitialized) {
            initialize(context)
        }
        return try {
            val adView = AdView(context)
            val request = BannerAdRequest.Builder(adUnitId, adSize).build()

            adView.loadAd(request, object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    Log.d(TAG, "Banner ad loaded successfully")
                    onLoaded(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w(TAG, "Banner ad failed to load: ${adError.message}")
                    onFailed(adError)
                }
            })

            adView
        } catch (e: Exception) {
            Log.e(TAG, "Exception while loading banner ad", e)
            null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // INTERSTITIAL ADS
    // ---------------------------------------------------------------------------------------------

    fun loadInterstitial(
        context: Context,
        adUnitId: String = AdIds.interstitialAdId,
        onLoaded: () -> Unit = {},
        onFailed: (LoadAdError) -> Unit = {}
    ) {
        if (!isInitialized) {
            initialize(context)
        }
        if (cachedInterstitialAd != null) {
            onLoaded()
            return
        }

        Log.d(TAG, "Loading Interstitial ad...")
        try {
            val request = AdRequest.Builder(adUnitId).build()

            InterstitialAd.load(request, object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded")
                    cachedInterstitialAd = ad
                    onLoaded()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w(TAG, "Interstitial ad failed to load: ${adError.message}")
                    cachedInterstitialAd = null
                    onFailed(adError)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception while loading Interstitial ad", e)
        }
    }

    fun showInterstitial(
        activity: Activity,
        onDismissed: () -> Unit = {}
    ): Boolean {
        if (isShowingFullScreenAd) {
            Log.d(TAG, "Skipped showing Interstitial: another fullscreen ad is currently active")
            onDismissed()
            return false
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInterstitialShowTime < MIN_INTERSTITIAL_INTERVAL_MS) {
            Log.d(TAG, "Skipped showing Interstitial: frequency cap cooldown active")
            onDismissed()
            return false
        }

        val ad = cachedInterstitialAd
        if (ad == null) {
            Log.d(TAG, "Interstitial ad not ready; loading new one")
            loadInterstitial(activity.applicationContext)
            onDismissed()
            return false
        }

        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdClicked() {
                Log.d(TAG, "Interstitial ad clicked")
            }

            override fun onAdImpression() {
                Log.d(TAG, "Interstitial ad impression recorded")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad showed full screen")
                isShowingFullScreenAd = true
                lastInterstitialShowTime = System.currentTimeMillis()
                cachedInterstitialAd = null
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed")
                isShowingFullScreenAd = false
                preloadAds(activity.applicationContext)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.w(TAG, "Interstitial ad failed to show: ${fullScreenContentError.message}")
                isShowingFullScreenAd = false
                cachedInterstitialAd = null
                preloadAds(activity.applicationContext)
                onDismissed()
            }
        }

        ad.show(activity)
        return true
    }

    // ---------------------------------------------------------------------------------------------
    // REWARDED ADS
    // ---------------------------------------------------------------------------------------------

    fun loadRewarded(
        context: Context,
        adUnitId: String = AdIds.rewardedAdId,
        onLoaded: () -> Unit = {},
        onFailed: (LoadAdError) -> Unit = {}
    ) {
        if (cachedRewardedAd != null) {
            onLoaded()
            return
        }

        Log.d(TAG, "Loading Rewarded ad...")
        val request = AdRequest.Builder(adUnitId).build()

        RewardedAd.load(request, object : AdLoadCallback<RewardedAd> {
            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Rewarded ad loaded")
                cachedRewardedAd = ad
                onLoaded()
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.w(TAG, "Rewarded ad failed to load: ${adError.message}")
                cachedRewardedAd = null
                onFailed(adError)
            }
        })
    }

    fun showRewarded(
        activity: Activity,
        onRewardEarned: (amount: Int, type: String) -> Unit,
        onDismissed: () -> Unit = {}
    ): Boolean {
        if (isShowingFullScreenAd) {
            Log.d(TAG, "Skipped showing Rewarded ad: another fullscreen ad is active")
            onDismissed()
            return false
        }

        val ad = cachedRewardedAd
        if (ad == null) {
            Log.d(TAG, "Rewarded ad not ready; requesting reload")
            loadRewarded(activity.applicationContext)
            onDismissed()
            return false
        }

        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdClicked() {}
            override fun onAdImpression() {}

            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
                cachedRewardedAd = null
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false
                loadRewarded(activity.applicationContext)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                isShowingFullScreenAd = false
                cachedRewardedAd = null
                loadRewarded(activity.applicationContext)
                onDismissed()
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onRewardEarned(rewardItem.amount, rewardItem.type)
        }

        return true
    }

    // ---------------------------------------------------------------------------------------------
    // REWARDED INTERSTITIAL ADS
    // ---------------------------------------------------------------------------------------------

    fun loadRewardedInterstitial(
        context: Context,
        adUnitId: String = AdIds.rewardedInterstitialAdId,
        onLoaded: () -> Unit = {},
        onFailed: (LoadAdError) -> Unit = {}
    ) {
        if (cachedRewardedInterstitialAd != null) {
            onLoaded()
            return
        }

        Log.d(TAG, "Loading Rewarded Interstitial ad...")
        val request = AdRequest.Builder(adUnitId).build()

        RewardedInterstitialAd.load(request, object : AdLoadCallback<RewardedInterstitialAd> {
            override fun onAdLoaded(ad: RewardedInterstitialAd) {
                Log.d(TAG, "Rewarded Interstitial ad loaded")
                cachedRewardedInterstitialAd = ad
                onLoaded()
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.w(TAG, "Rewarded Interstitial ad failed: ${adError.message}")
                cachedRewardedInterstitialAd = null
                onFailed(adError)
            }
        })
    }

    fun showRewardedInterstitial(
        activity: Activity,
        onRewardEarned: (amount: Int, type: String) -> Unit,
        onDismissed: () -> Unit = {}
    ): Boolean {
        if (isShowingFullScreenAd) {
            onDismissed()
            return false
        }

        val ad = cachedRewardedInterstitialAd
        if (ad == null) {
            loadRewardedInterstitial(activity.applicationContext)
            onDismissed()
            return false
        }

        ad.adEventCallback = object : RewardedInterstitialAdEventCallback {
            override fun onAdClicked() {}
            override fun onAdImpression() {}

            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
                cachedRewardedInterstitialAd = null
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false
                loadRewardedInterstitial(activity.applicationContext)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                isShowingFullScreenAd = false
                cachedRewardedInterstitialAd = null
                loadRewardedInterstitial(activity.applicationContext)
                onDismissed()
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onRewardEarned(rewardItem.amount, rewardItem.type)
        }

        return true
    }

    // ---------------------------------------------------------------------------------------------
    // APP OPEN ADS
    // ---------------------------------------------------------------------------------------------

    fun loadAppOpenAd(
        context: Context,
        adUnitId: String = AdIds.appOpenAdId,
        onLoaded: () -> Unit = {},
        onFailed: (LoadAdError) -> Unit = {}
    ) {
        if (cachedAppOpenAd != null) {
            onLoaded()
            return
        }

        Log.d(TAG, "Loading App Open ad...")
        val request = AdRequest.Builder(adUnitId).build()

        AppOpenAd.load(request, object : AdLoadCallback<AppOpenAd> {
            override fun onAdLoaded(ad: AppOpenAd) {
                Log.d(TAG, "App Open ad loaded")
                cachedAppOpenAd = ad
                onLoaded()
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.w(TAG, "App Open ad failed: ${adError.message}")
                cachedAppOpenAd = null
                onFailed(adError)
            }
        })
    }

    fun showAppOpenAdIfAvailable(
        activity: Activity,
        onDismissed: () -> Unit = {}
    ): Boolean {
        if (isShowingFullScreenAd) {
            Log.d(TAG, "Skipped showing App Open ad: another fullscreen ad is active")
            onDismissed()
            return false
        }

        val ad = cachedAppOpenAd
        if (ad == null) {
            loadAppOpenAd(activity.applicationContext)
            onDismissed()
            return false
        }

        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdClicked() {}
            override fun onAdImpression() {}

            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
                cachedAppOpenAd = null
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false
                loadAppOpenAd(activity.applicationContext)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                isShowingFullScreenAd = false
                cachedAppOpenAd = null
                loadAppOpenAd(activity.applicationContext)
                onDismissed()
            }
        }

        ad.show(activity)
        return true
    }

    // ---------------------------------------------------------------------------------------------
    // NATIVE ADS
    // ---------------------------------------------------------------------------------------------

    fun loadNativeAd(
        context: Context,
        adUnitId: String = AdIds.nativeAdId,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (LoadAdError) -> Unit = {}
    ) {
        if (!isInitialized) {
            initialize(context)
        }
        Log.d(TAG, "Loading Native ad...")
        try {
            val request = NativeAdRequest.Builder(adUnitId, emptyList()).build()

            val callback = object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    Log.d(TAG, "Native ad loaded successfully: ${nativeAd.headline}")
                    onLoaded(nativeAd)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w(TAG, "Native ad failed to load: ${adError.message}")
                    onFailed(adError)
                }
            }

            NativeAdLoader.load(request, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while loading Native ad", e)
        }
    }
}

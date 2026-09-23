package com.example.ads

import com.example.BuildConfig

/**
 * Centralized AdMob Ad Unit IDs configuration.
 *
 * During development (DEBUG builds), Google's official test ad unit IDs are used automatically.
 * For RELEASE builds, replace the production constants below with your real AdMob Ad Unit IDs from the AdMob Console.
 */
object AdIds {

    // =========================================================================================
    // INSERT YOUR REAL PRODUCTION ADMOB IDs HERE FOR RELEASE BUILDS
    // =========================================================================================
    private const val PROD_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    private const val PROD_BANNER_AD_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val PROD_INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val PROD_NATIVE_AD_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val PROD_APP_OPEN_AD_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val PROD_REWARDED_AD_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val PROD_REWARDED_INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/5354046379"

    // =========================================================================================
    // GOOGLE'S OFFICIAL TEST AD UNIT IDs (Always active in DEBUG builds)
    // =========================================================================================
    private const val TEST_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    private const val TEST_BANNER_AD_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_NATIVE_AD_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN_AD_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_REWARDED_AD_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_REWARDED_INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/5354046379"

    val appId: String
        get() = if (BuildConfig.DEBUG) TEST_ADMOB_APP_ID else PROD_ADMOB_APP_ID

    val bannerAdId: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER_AD_ID else PROD_BANNER_AD_ID

    val interstitialAdId: String
        get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL_AD_ID else PROD_INTERSTITIAL_AD_ID

    val nativeAdId: String
        get() = if (BuildConfig.DEBUG) TEST_NATIVE_AD_ID else PROD_NATIVE_AD_ID

    val appOpenAdId: String
        get() = if (BuildConfig.DEBUG) TEST_APP_OPEN_AD_ID else PROD_APP_OPEN_AD_ID

    val rewardedAdId: String
        get() = if (BuildConfig.DEBUG) TEST_REWARDED_AD_ID else PROD_REWARDED_AD_ID

    val rewardedInterstitialAdId: String
        get() = if (BuildConfig.DEBUG) TEST_REWARDED_INTERSTITIAL_AD_ID else PROD_REWARDED_INTERSTITIAL_AD_ID
}

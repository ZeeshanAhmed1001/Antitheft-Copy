package com.example.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView

/**
 * A Jetpack Compose wrapper for GMA Next-Gen Banner Ad.
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    adUnitId: String = AdIds.bannerAdId,
    adSize: AdSize = AdSize.BANNER
) {
    val context = LocalContext.current
    val adView = remember(adUnitId) {
        AdsManager.loadBannerAd(context, adUnitId, adSize)
    }

    DisposableEffect(adView) {
        onDispose {
            // Memory cleanup when composable leaves hierarchy
        }
    }

    if (adView == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("banner_ad_container"),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

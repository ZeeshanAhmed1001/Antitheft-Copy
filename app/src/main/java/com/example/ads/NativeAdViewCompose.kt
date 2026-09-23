package com.example.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * A Jetpack Compose Native Ad Card displaying Headline, Body, Call to Action, Advertiser, and "Ad" badge.
 * Auto-loads and manages NativeAd lifecycle safely.
 */
@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier,
    adUnitId: String = AdIds.nativeAdId
) {
    val context = LocalContext.current
    var loadedNativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(adUnitId) {
        isLoading = true
        AdsManager.loadNativeAd(
            context = context,
            adUnitId = adUnitId,
            onLoaded = { ad ->
                loadedNativeAd = ad
                isLoading = false
            },
            onFailed = {
                isLoading = false
            }
        )
    }

    DisposableEffect(loadedNativeAd) {
        onDispose {
            loadedNativeAd?.destroy()
        }
    }

    val ad = loadedNativeAd ?: return

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22314E)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("native_ad_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF2A900)
                ) {
                    Text(
                        text = "Ad",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                ad.advertiser?.let { advertiserName ->
                    Text(
                        text = advertiserName,
                        fontSize = 11.sp,
                        color = Color(0xFF8C9BAE)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            ad.headline?.let { headlineText ->
                Text(
                    text = headlineText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            ad.body?.let { bodyText ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bodyText,
                    fontSize = 12.sp,
                    color = Color(0xFF8C9BAE),
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ad.callToAction?.let { ctaText ->
                Button(
                    onClick = { /* Native Ad handles click internal registration */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = ctaText,
                        color = Color(0xFF0A111E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

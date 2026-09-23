package com.example

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ads.AdsManager
import com.example.ads.ConsentManager
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.AegisNavyDark
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(applicationContext) as T
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize GMA Next-Gen SDK immediately so MobileAds is ready for UI composables
        AdsManager.initialize(this)

        // Gather UMP Consent asynchronously
        ConsentManager.gatherConsent(this) { consentError ->
            if (consentError != null) {
                Log.w("MainActivity", "Consent error: ${consentError.message}")
            }
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AegisNavyDark
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onShowInterstitial = { onDismiss ->
                            AdsManager.showInterstitial(this, onDismiss)
                        },
                        onShowRewarded = { onReward, onDismiss ->
                            AdsManager.showRewarded(this, onReward, onDismiss)
                        },
                        onShowPrivacyOptions = {
                            ConsentManager.showPrivacyOptionsForm(this) { _ -> }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()

        if (ConsentManager.canRequestAds(this) && AdsManager.isInitialized) {
            AdsManager.showAppOpenAdIfAvailable(this)
        }
    }
}

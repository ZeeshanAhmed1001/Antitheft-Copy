package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppSettingsAlt
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.BannerAdView
import com.example.ads.NativeAdCard
import com.example.data.SecurityLogEntry
import com.example.ui.theme.AegisAlertCrimson
import com.example.ui.theme.AegisArmedGreen
import com.example.ui.theme.AegisCyanPrimary
import com.example.ui.theme.AegisNavyCard
import com.example.ui.theme.AegisNavyDark
import com.example.ui.theme.AegisNavySurface
import com.example.ui.theme.AegisSafetyAmber
import com.example.ui.theme.AegisTextPrimary
import com.example.ui.theme.AegisTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestNotificationPermission: () -> Unit,
    onShowInterstitial: (onDismiss: () -> Unit) -> Unit = {},
    onShowRewarded: (onReward: (Int, String) -> Unit, onDismiss: () -> Unit) -> Unit = { _, _ -> },
    onShowPrivacyOptions: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogPurpose by remember { mutableStateOf(PinPurpose.DISARM) } // DISARM or CHANGE_PIN
    var showChangePinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "App Icon",
                            tint = AegisCyanPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "AEGIS ANTI-THEFT",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AegisTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShowPrivacyOptions) {
                        Icon(
                            imageVector = Icons.Default.AppSettingsAlt,
                            contentDescription = "Privacy Options",
                            tint = AegisTextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (uiState.isArmed) AegisArmedGreen.copy(alpha = 0.2f) else AegisSafetyAmber.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (uiState.isArmed) AegisArmedGreen else AegisSafetyAmber
                        ),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (uiState.isArmed) "ARMED" else "DISARMED",
                            color = if (uiState.isArmed) AegisArmedGreen else AegisSafetyAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AegisNavyDark)
            )
        },
        bottomBar = {
            BannerAdView(modifier = Modifier.background(AegisNavyDark))
        },
        containerColor = AegisNavyDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Status Hero Banner
            item {
                StatusHeroCard(
                    isArmed = uiState.isArmed,
                    isAlarmRinging = uiState.isAlarmRinging,
                    onToggleArmed = { shouldArm ->
                        if (!shouldArm) {
                            // Require PIN to disarm
                            pinDialogPurpose = PinPurpose.DISARM
                            showPinDialog = true
                        } else {
                            viewModel.toggleArmed(true)
                        }
                    },
                    onStopAlarm = {
                        pinDialogPurpose = PinPurpose.STOP_ALARM
                        showPinDialog = true
                    }
                )
            }

            // 2. Quick Action Grid
            item {
                Text(
                    text = "QUICK ACTIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AegisTextSecondary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ActionTile(
                        title = "Fake Power Off",
                        subtitle = "Simulate shutdown",
                        icon = Icons.Default.PowerSettingsNew,
                        iconTint = AegisAlertCrimson,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_fake_shutdown"),
                        onClick = {
                            if (!uiState.hasOverlayPermission) {
                                openOverlaySettings(context)
                            } else {
                                viewModel.triggerFakeShutdown()
                            }
                        }
                    )
                    ActionTile(
                        title = if (uiState.isAlarmRinging) "Stop Siren" else "Test Siren",
                        subtitle = if (uiState.isAlarmRinging) "Tap to mute alarm" else "Check max audio",
                        icon = if (uiState.isAlarmRinging) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        iconTint = if (uiState.isAlarmRinging) AegisAlertCrimson else AegisCyanPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_test_siren"),
                        onClick = {
                            if (uiState.isAlarmRinging) {
                                viewModel.stopSirenAlarm()
                            } else {
                                onShowInterstitial {
                                    viewModel.testSirenAlarm()
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Support Aegis / Watch Rewarded Ad Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF132238)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AegisCyanPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onShowRewarded(
                                { amount, type ->
                                    Log.d("MainScreen", "Reward earned: $amount $type")
                                },
                                {}
                            )
                        }
                        .testTag("action_rewarded_ad")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Reward Icon",
                            tint = AegisCyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Support Aegis Security",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AegisTextPrimary
                            )
                            Text(
                                text = "Watch a short ad to earn Pro Security Pass",
                                fontSize = 11.sp,
                                color = AegisTextSecondary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AegisCyanPrimary
                        ) {
                            Text(
                                text = "WATCH AD",
                                color = AegisNavyDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3. Security Settings & Toggles
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AegisNavySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22314E))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "SECURITY PROTECTION RULES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AegisTextSecondary
                        )

                        // Charger Disconnect Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = AegisSafetyAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Charger Disconnect Siren",
                                    fontWeight = FontWeight.SemiBold,
                                    color = AegisTextPrimary
                                )
                                Text(
                                    text = "Sounds loud siren if charger is unplugged",
                                    fontSize = 12.sp,
                                    color = AegisTextSecondary
                                )
                            }
                            Switch(
                                checked = uiState.isChargerAlertEnabled,
                                onCheckedChange = { viewModel.toggleChargerAlert(it) },
                                modifier = Modifier.testTag("toggle_charger_alert")
                            )
                        }

                        Divider(color = Color(0xFF22314E))

                        // Volume Down Anti-Tampering Protection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = AegisCyanPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Volume Lock & Siren Trigger",
                                    fontWeight = FontWeight.SemiBold,
                                    color = AegisTextPrimary
                                )
                                Text(
                                    text = "Triggers siren & PIN screen if volume is lowered while armed",
                                    fontSize = 12.sp,
                                    color = AegisTextSecondary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (uiState.isArmed) AegisCyanPrimary.copy(alpha = 0.2f) else Color(0xFF22314E)
                            ) {
                                Text(
                                    text = if (uiState.isArmed) "ACTIVE" else "ARM DEVICE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isArmed) AegisCyanPrimary else AegisTextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Divider(color = Color(0xFF22314E))

                        // Safe Mode Bypass
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = AegisCyanPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Safe Mode Bypass",
                                    fontWeight = FontWeight.SemiBold,
                                    color = AegisTextPrimary
                                )
                                Text(
                                    text = "Allow real power menu when app is disarmed",
                                    fontSize = 12.sp,
                                    color = AegisTextSecondary
                                )
                            }
                            Switch(
                                checked = uiState.isSafeModeEnabled,
                                onCheckedChange = { viewModel.toggleSafeMode(it) },
                                modifier = Modifier.testTag("toggle_safe_mode")
                            )
                        }

                        Divider(color = Color(0xFF22314E))

                        // PIN Management Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChangePinDialog = true }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AegisCyanPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.isPinSet) "Change Security PIN" else "Set Security PIN",
                                    fontWeight = FontWeight.SemiBold,
                                    color = AegisTextPrimary
                                )
                                Text(
                                    text = if (uiState.isPinSet) "Current PIN: ${uiState.currentPin.replace(".".toRegex(), "•")}" else "No PIN set yet. Tap to create security PIN.",
                                    fontSize = 12.sp,
                                    color = AegisTextSecondary
                                )
                            }
                            Text(
                                text = if (uiState.isPinSet) "EDIT" else "SET PIN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AegisCyanPrimary
                            )
                        }
                    }
                }
            }

            // 4. Permissions & System Health
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AegisNavySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22314E))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "SYSTEM PERMISSIONS & HEALTH",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AegisTextSecondary
                        )

                        // Overlay Permission Status
                        PermissionStatusRow(
                            title = "Display Over Other Apps (Overlay)",
                            description = "Required to display fake shutdown overlay",
                            isGranted = uiState.hasOverlayPermission,
                            onGrantClick = { openOverlaySettings(context) }
                        )

                        // Notification Permission Status
                        PermissionStatusRow(
                            title = "Post Notifications",
                            description = "Required for anti-kill foreground service",
                            isGranted = uiState.hasNotificationPermission,
                            onGrantClick = onRequestNotificationPermission
                        )

                        // Foreground Service Running Status
                        PermissionStatusRow(
                            title = "Foreground Service Engine",
                            description = "Keeps monitoring active in background",
                            isGranted = uiState.isServiceRunning,
                            onGrantClick = { viewModel.toggleArmed(true) }
                        )
                    }
                }
            }

            // Native Ad Card
            item {
                NativeAdCard()
            }

            // 5. Security Logs History
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "SECURITY EVENT LOGS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AegisTextSecondary
                    )

                    if (uiState.logs.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearLogs() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Logs",
                                tint = AegisTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (uiState.logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No security events logged yet.",
                            color = AegisTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.logs.take(10).forEach { log ->
                            LogItemCard(log = log)
                        }
                    }
                }
            }
        }
    }

    // PIN Authentication Dialog
    if (showPinDialog) {
        PinAuthDialog(
            title = if (pinDialogPurpose == PinPurpose.DISARM) "Enter PIN to Disarm Protection" else "Enter PIN to Stop Siren",
            onVerify = { enteredPin ->
                if (viewModel.verifyPin(enteredPin)) {
                    showPinDialog = false
                    if (pinDialogPurpose == PinPurpose.DISARM) {
                        viewModel.toggleArmed(false)
                    } else {
                        viewModel.stopSirenAlarm()
                    }
                }
            },
            onDismiss = { showPinDialog = false }
        )
    }

    // Change PIN Dialog
    if (showChangePinDialog) {
        ChangePinDialog(
            currentPin = uiState.currentPin,
            isPinSet = uiState.isPinSet,
            onPinChanged = { newPin ->
                viewModel.updatePin(newPin)
                showChangePinDialog = false
            },
            onDismiss = { showChangePinDialog = false }
        )
    }
}

enum class PinPurpose {
    DISARM, STOP_ALARM
}

@Composable
fun StatusHeroCard(
    isArmed: Boolean,
    isAlarmRinging: Boolean,
    onToggleArmed: (Boolean) -> Unit,
    onStopAlarm: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isAlarmRinging -> AegisAlertCrimson
            isArmed -> AegisArmedGreen
            else -> AegisSafetyAmber
        },
        label = "BorderColor"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlarmRinging) Color(0xFF33080A) else AegisNavySurface
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, cardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(if (isAlarmRinging) pulseScale else 1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isAlarmRinging -> AegisAlertCrimson.copy(alpha = 0.2f)
                                isArmed -> AegisArmedGreen.copy(alpha = 0.2f)
                                else -> AegisSafetyAmber.copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isAlarmRinging -> Icons.Default.Warning
                            isArmed -> Icons.Default.Shield
                            else -> Icons.Default.Security
                        },
                        contentDescription = "Shield State",
                        tint = when {
                            isAlarmRinging -> AegisAlertCrimson
                            isArmed -> AegisArmedGreen
                            else -> AegisSafetyAmber
                        },
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when {
                    isAlarmRinging -> "⚠️ SIREN ALARM RINGING!"
                    isArmed -> "SYSTEM ARMED & SECURED"
                    else -> "PROTECTION DISARMED"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isAlarmRinging -> AegisAlertCrimson
                    isArmed -> AegisArmedGreen
                    else -> AegisSafetyAmber
                }
            )

            Text(
                text = when {
                    isAlarmRinging -> "Security breach detected. Enter PIN to disarm siren."
                    isArmed -> "Monitoring charger unplug, power tampering & background status."
                    else -> "Toggle switch below to arm anti-theft service."
                },
                fontSize = 12.sp,
                color = AegisTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            if (isAlarmRinging) {
                Button(
                    onClick = onStopAlarm,
                    colors = ButtonDefaults.buttonColors(containerColor = AegisAlertCrimson),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("button_silence_alarm")
                ) {
                    Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "SILENCE SIREN ALARM", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AegisNavyCard)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isArmed) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isArmed) AegisArmedGreen else AegisSafetyAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArmed) "Armed Mode Active" else "Disarmed Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AegisTextPrimary
                        )
                    }

                    Switch(
                        checked = isArmed,
                        onCheckedChange = { onToggleArmed(it) },
                        modifier = Modifier.testTag("switch_arm_protection")
                    )
                }
            }
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AegisNavySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22314E)),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AegisTextPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = AegisTextSecondary)
        }
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) AegisArmedGreen else AegisSafetyAmber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AegisTextPrimary)
            Text(text = description, fontSize = 11.sp, color = AegisTextSecondary)
        }
        if (!isGranted) {
            TextButton(onClick = onGrantClick) {
                Text(text = "GRANT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AegisCyanPrimary)
            }
        }
    }
}

@Composable
fun LogItemCard(log: SecurityLogEntry) {
    val logColor = when (log.type) {
        SecurityLogEntry.LogType.ALARM -> AegisAlertCrimson
        SecurityLogEntry.LogType.WARNING -> AegisSafetyAmber
        SecurityLogEntry.LogType.PIN_SUCCESS -> AegisArmedGreen
        SecurityLogEntry.LogType.PIN_FAILED -> AegisAlertCrimson
        SecurityLogEntry.LogType.INFO -> AegisCyanPrimary
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AegisNavySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A45)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(logColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = log.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AegisTextPrimary)
                Text(text = log.description, fontSize = 11.sp, color = AegisTextSecondary)
            }
            Text(text = log.timestamp, fontSize = 10.sp, color = AegisTextSecondary)
        }
    }
}

@Composable
fun PinAuthDialog(
    title: String,
    onVerify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AegisNavySurface,
        title = {
            Text(text = title, color = AegisTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it },
                    label = { Text("4-Digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AegisCyanPrimary,
                        unfocusedBorderColor = AegisTextSecondary,
                        focusedLabelColor = AegisCyanPrimary,
                        unfocusedLabelColor = AegisTextSecondary,
                        focusedTextColor = AegisTextPrimary,
                        unfocusedTextColor = AegisTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText.isNotEmpty()) {
                    Text(text = errorText, color = AegisAlertCrimson, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length == 4) {
                        onVerify(pin)
                    } else {
                        errorText = "PIN must be 4 digits"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AegisCyanPrimary)
            ) {
                Text("VERIFY", color = AegisNavyDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = AegisTextSecondary)
            }
        }
    )
}

@Composable
fun ChangePinDialog(
    currentPin: String,
    isPinSet: Boolean,
    onPinChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var oldPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AegisNavySurface,
        title = {
            Text(
                text = if (isPinSet) "Change Security PIN" else "Set Security PIN",
                color = AegisTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isPinSet) {
                    OutlinedTextField(
                        value = oldPinInput,
                        onValueChange = { if (it.length <= 4) oldPinInput = it },
                        label = { Text("Current PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AegisCyanPrimary,
                            unfocusedBorderColor = AegisTextSecondary,
                            focusedTextColor = AegisTextPrimary,
                            unfocusedTextColor = AegisTextPrimary
                        )
                    )
                }

                OutlinedTextField(
                    value = newPinInput,
                    onValueChange = { if (it.length <= 4) newPinInput = it },
                    label = { Text("New 4-Digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AegisCyanPrimary,
                        unfocusedBorderColor = AegisTextSecondary,
                        focusedTextColor = AegisTextPrimary,
                        unfocusedTextColor = AegisTextPrimary
                    )
                )

                OutlinedTextField(
                    value = confirmPinInput,
                    onValueChange = { if (it.length <= 4) confirmPinInput = it },
                    label = { Text("Confirm New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AegisCyanPrimary,
                        unfocusedBorderColor = AegisTextSecondary,
                        focusedTextColor = AegisTextPrimary,
                        unfocusedTextColor = AegisTextPrimary
                    )
                )

                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = AegisAlertCrimson, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isPinSet && oldPinInput != currentPin) {
                        errorMessage = "Current PIN is incorrect"
                    } else if (newPinInput.length != 4) {
                        errorMessage = "New PIN must be 4 digits"
                    } else if (newPinInput != confirmPinInput) {
                        errorMessage = "New PINs do not match"
                    } else {
                        onPinChanged(newPinInput)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AegisCyanPrimary)
            ) {
                Text(if (isPinSet) "SAVE PIN" else "SET PIN", color = AegisNavyDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = AegisTextSecondary)
            }
        }
    )
}

fun openOverlaySettings(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}

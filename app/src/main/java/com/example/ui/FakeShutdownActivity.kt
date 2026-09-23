package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SecurityPreferences
import com.example.service.TheftProtectionService
import com.example.ui.theme.MyApplicationTheme

enum class ShutdownStage {
    ANIMATING_POWER_OFF, // Showing "Shutting down..." circular progress bar
    BLACK_SCREEN_SILENT, // Screen pitch black for 1 second
    ALARM_RINGING        // Loud anti-theft alarm ringing with PIN unlock keypad overlay
}

class FakeShutdownActivity : ComponentActivity() {

    private lateinit var preferences: SecurityPreferences
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = SecurityPreferences(this)

        // Make Activity turn screen on, show over keyguard, and go full screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Set screen brightness
        setScreenBrightness(1.0f)

        val directAlarm = intent?.getBooleanExtra(TheftProtectionService.EXTRA_DIRECT_ALARM, false) ?: false
        val reasonText = intent?.getStringExtra(TheftProtectionService.EXTRA_REASON_TEXT) ?: "Fake Power Off Triggered!"

        setContent {
            MyApplicationTheme {
                var currentStage by remember {
                    mutableStateOf(if (directAlarm) ShutdownStage.ALARM_RINGING else ShutdownStage.ANIMATING_POWER_OFF)
                }
                var enteredPin by remember { mutableStateOf("") }
                var errorMessage by remember { mutableStateOf("") }
                var countdownSeconds by remember { mutableIntStateOf(60) }

                LaunchedEffect(Unit) {
                    if (directAlarm) {
                        setScreenBrightness(1.0f)
                        val serviceIntent = Intent(this@FakeShutdownActivity, TheftProtectionService::class.java).apply {
                            action = TheftProtectionService.ACTION_START_ALARM
                            putExtra(TheftProtectionService.EXTRA_ALARM_REASON, reasonText)
                        }
                        startService(serviceIntent)
                    } else {
                        // Stage 1: Animating power off (3 seconds)
                        handler.postDelayed({
                            currentStage = ShutdownStage.BLACK_SCREEN_SILENT
                            setScreenBrightness(0.01f) // Pitch black simulation

                            // Stage 2: Exactly 1 second on black screen, then trigger Alarm
                            handler.postDelayed({
                                currentStage = ShutdownStage.ALARM_RINGING
                                setScreenBrightness(1.0f) // Brighten screen for PIN keypad

                                // Trigger service alarm
                                val serviceIntent = Intent(this@FakeShutdownActivity, TheftProtectionService::class.java).apply {
                                    action = TheftProtectionService.ACTION_START_ALARM
                                    putExtra(TheftProtectionService.EXTRA_ALARM_REASON, reasonText)
                                }
                                startService(serviceIntent)

                            }, 1000)

                        }, 3000)
                    }
                }

                // Countdown Timer for Alarm
                LaunchedEffect(currentStage) {
                    if (currentStage == ShutdownStage.ALARM_RINGING) {
                        while (countdownSeconds > 0) {
                            kotlinx.coroutines.delay(1000)
                            countdownSeconds--
                        }
                        // 60-second timer expired
                        disarmAndExit()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    when (currentStage) {
                        ShutdownStage.ANIMATING_POWER_OFF -> {
                            FakeShutdownAnimationScreen()
                        }
                        ShutdownStage.BLACK_SCREEN_SILENT -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            )
                        }
                        ShutdownStage.ALARM_RINGING -> {
                            EmergencyPinUnlockScreen(
                                enteredPin = enteredPin,
                                errorMessage = errorMessage,
                                countdownSeconds = countdownSeconds,
                                onNumberClick = { digit ->
                                    if (enteredPin.length < 4) {
                                        enteredPin += digit
                                        errorMessage = ""
                                        if (enteredPin.length == 4) {
                                            if (preferences.verifyPin(enteredPin)) {
                                                disarmAndExit()
                                            } else {
                                                errorMessage = "INVALID PIN CODE!"
                                                enteredPin = ""
                                            }
                                        }
                                    }
                                },
                                onDeleteClick = {
                                    if (enteredPin.isNotEmpty()) {
                                        enteredPin = enteredPin.dropLast(1)
                                        errorMessage = ""
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setScreenBrightness(brightness: Float) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }

    private fun disarmAndExit() {
        val serviceIntent = Intent(this, TheftProtectionService::class.java).apply {
            action = TheftProtectionService.ACTION_STOP_ALARM
        }
        startService(serviceIntent)
        finish()
    }

    // Intercept hardware volume keys to disable volume manipulation
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true // Consume key press
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        // Intercept back button during fake shutdown
    }
}

@Composable
fun FakeShutdownAnimationScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Shutting down...",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun EmergencyPinUnlockScreen(
    enteredPin: String,
    errorMessage: String,
    countdownSeconds: Int,
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert Icon",
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ANTI-THEFT SIREN ACTIVE",
                color = Color(0xFFFF3B30),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Enter Security PIN to Disarm (${countdownSeconds}s remaining)",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            )

            // PIN Dot Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                repeat(4) { index ->
                    val isFilled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) Color(0xFF00E5FF) else Color(0xFF1E2A45)
                            )
                    )
                }
            }

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF3B30),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Numeric Keypad
            val buttons = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                buttons.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.2f)
                            ) {
                                if (digit == "DEL") {
                                    IconButton(
                                        onClick = onDeleteClick,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "Delete",
                                            tint = Color.White
                                        )
                                    }
                                } else if (digit.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF141E33))
                                            .clickable { onNumberClick(digit) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = digit,
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

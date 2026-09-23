package com.example.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SecurityLogEntry
import com.example.data.SecurityPreferences
import com.example.service.TheftProtectionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MainUiState(
    val isArmed: Boolean = false,
    val isChargerAlertEnabled: Boolean = true,
    val isSafeModeEnabled: Boolean = false,
    val isAlarmRinging: Boolean = false,
    val currentPin: String = "1234",
    val isPinSet: Boolean = false,
    val logs: List<SecurityLogEntry> = emptyList(),
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isServiceRunning: Boolean = false
)

private data class SecurityFlags(
    val isArmed: Boolean,
    val chargerAlert: Boolean,
    val safeMode: Boolean,
    val alarmRinging: Boolean,
    val isPinSet: Boolean
)

private data class PrefState(
    val isArmed: Boolean,
    val chargerAlert: Boolean,
    val safeMode: Boolean,
    val alarmRinging: Boolean,
    val isPinSet: Boolean,
    val logs: List<SecurityLogEntry>
)

class MainViewModel(private val context: Context) : ViewModel() {

    private val preferences = SecurityPreferences(context)

    private val _hasOverlayPermission = MutableStateFlow(checkOverlayPermission())
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(checkNotificationPermission())
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(isServiceRunningCheck())
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val flagsFlow = combine(
        preferences.isArmed,
        preferences.isChargerAlertEnabled,
        preferences.isSafeModeEnabled,
        preferences.isAlarmRinging,
        preferences.isPinSet
    ) { isArmed, chargerAlert, safeMode, alarmRinging, isPinSet ->
        SecurityFlags(isArmed, chargerAlert, safeMode, alarmRinging, isPinSet)
    }

    private val prefFlow = combine(
        flagsFlow,
        preferences.logs
    ) { flags, logs ->
        PrefState(flags.isArmed, flags.chargerAlert, flags.safeMode, flags.alarmRinging, flags.isPinSet, logs)
    }

    private val systemFlow = combine(
        _hasOverlayPermission,
        _hasNotificationPermission,
        _isServiceRunning
    ) { overlayPerm, notifPerm, serviceRunning ->
        Triple(overlayPerm, notifPerm, serviceRunning)
    }

    val uiState: StateFlow<MainUiState> = combine(
        prefFlow,
        systemFlow
    ) { prefData, systemData ->
        MainUiState(
            isArmed = prefData.isArmed,
            isChargerAlertEnabled = prefData.chargerAlert,
            isSafeModeEnabled = prefData.safeMode,
            isAlarmRinging = prefData.alarmRinging,
            currentPin = preferences.getPin(),
            isPinSet = prefData.isPinSet,
            logs = prefData.logs,
            hasOverlayPermission = systemData.first,
            hasNotificationPermission = systemData.second,
            isServiceRunning = systemData.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun refreshPermissions() {
        _hasOverlayPermission.value = checkOverlayPermission()
        _hasNotificationPermission.value = checkNotificationPermission()
        _isServiceRunning.value = isServiceRunningCheck()
    }

    fun toggleArmed(shouldArm: Boolean) {
        if (shouldArm) {
            startProtectionService()
        } else {
            stopProtectionService()
        }
    }

    fun toggleChargerAlert(enabled: Boolean) {
        preferences.setChargerAlertEnabled(enabled)
    }

    fun toggleSafeMode(enabled: Boolean) {
        preferences.setSafeModeEnabled(enabled)
    }

    fun updatePin(newPin: String) {
        preferences.setPin(newPin)
    }

    fun verifyPin(pin: String): Boolean {
        return preferences.verifyPin(pin)
    }

    fun clearLogs() {
        preferences.clearLogs()
    }

    fun triggerFakeShutdown() {
        val intent = Intent(context, FakeShutdownActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
        preferences.addLog("Fake Shutdown Triggered", "User manually launched Fake Power Off simulation.", SecurityLogEntry.LogType.INFO)
    }

    fun testSirenAlarm() {
        val intent = Intent(context, TheftProtectionService::class.java).apply {
            action = TheftProtectionService.ACTION_START_ALARM
            putExtra(TheftProtectionService.EXTRA_ALARM_REASON, "Test Alarm triggered from Dashboard.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopSirenAlarm() {
        val intent = Intent(context, TheftProtectionService::class.java).apply {
            action = TheftProtectionService.ACTION_STOP_ALARM
        }
        context.startService(intent)
    }

    private fun startProtectionService() {
        preferences.setArmed(true)
        val intent = Intent(context, TheftProtectionService::class.java).apply {
            action = TheftProtectionService.ACTION_ARM_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _isServiceRunning.value = true
    }

    private fun stopProtectionService() {
        preferences.setArmed(false)
        val intent = Intent(context, TheftProtectionService::class.java).apply {
            action = TheftProtectionService.ACTION_DISARM_SERVICE
        }
        context.startService(intent)
        _isServiceRunning.value = false
    }

    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunningCheck(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (TheftProtectionService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

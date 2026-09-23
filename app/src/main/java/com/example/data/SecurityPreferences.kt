package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SecurityLogEntry(
    val id: String = System.currentTimeMillis().toString(),
    val timestamp: String,
    val title: String,
    val description: String,
    val type: LogType
) {
    enum class LogType {
        INFO, WARNING, ALARM, PIN_SUCCESS, PIN_FAILED
    }
}

class SecurityPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        syncWithPrefs(prefs)
    }

    val isArmed: StateFlow<Boolean> = _isArmed.asStateFlow()
    val isChargerAlertEnabled: StateFlow<Boolean> = _isChargerAlertEnabled.asStateFlow()
    val isSafeModeEnabled: StateFlow<Boolean> = _isSafeModeEnabled.asStateFlow()
    val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()
    val isPinSet: StateFlow<Boolean> = _isPinSet.asStateFlow()
    val logs: StateFlow<List<SecurityLogEntry>> = _logs.asStateFlow()

    fun getPin(): String {
        return prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
    }

    fun isCustomPinSet(): Boolean {
        return _isPinSet.value
    }

    fun setPin(newPin: String) {
        prefs.edit().putString(KEY_PIN, newPin).putBoolean(KEY_IS_PIN_SET, true).apply()
        _isPinSet.value = true
        addLog("PIN Changed", "Master security PIN was updated.", SecurityLogEntry.LogType.INFO)
    }

    fun verifyPin(enteredPin: String): Boolean {
        val currentPin = getPin()
        val isCorrect = enteredPin == currentPin
        if (isCorrect) {
            addLog("PIN Verified", "Successful security authentication.", SecurityLogEntry.LogType.PIN_SUCCESS)
        } else {
            addLog("Failed PIN Attempt", "Incorrect PIN code entered!", SecurityLogEntry.LogType.PIN_FAILED)
        }
        return isCorrect
    }

    fun setArmed(armed: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ARMED, armed).apply()
        _isArmed.value = armed
        val statusText = if (armed) "Anti-Theft Protection ARMED" else "Anti-Theft Protection DISARMED"
        addLog(statusText, "Protection status toggled by user.", if (armed) SecurityLogEntry.LogType.INFO else SecurityLogEntry.LogType.WARNING)
    }

    fun setChargerAlertEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHARGER_ALERT, enabled).apply()
        _isChargerAlertEnabled.value = enabled
        addLog("Charger Alert Toggled", "Charger disconnection trigger set to $enabled", SecurityLogEntry.LogType.INFO)
    }

    fun setSafeModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAFE_MODE, enabled).apply()
        _isSafeModeEnabled.value = enabled
        addLog("Safe Mode Toggled", "Safe mode set to $enabled", SecurityLogEntry.LogType.INFO)
    }

    fun setAlarmRinging(ringing: Boolean, reason: String = "") {
        _isAlarmRinging.value = ringing
        if (ringing) {
            addLog("SIREN ALARM TRIGGERED!", reason.ifEmpty { "Security breach detected!" }, SecurityLogEntry.LogType.ALARM)
        } else {
            addLog("Siren Alarm Silenced", "Alarm disarmed successfully.", SecurityLogEntry.LogType.INFO)
        }
    }

    fun addLog(title: String, description: String, type: SecurityLogEntry.LogType) {
        val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val newEntry = SecurityLogEntry(timestamp = timestamp, title = title, description = description, type = type)
        val currentList = _logs.value.toMutableList()
        currentList.add(0, newEntry)
        if (currentList.size > MAX_LOGS) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logs.value = currentList
        saveLogs(currentList)
    }

    fun clearLogs() {
        _logs.value = emptyList()
        prefs.edit().remove(KEY_LOGS_JSON).apply()
    }

    private fun saveLogs(logList: List<SecurityLogEntry>) {
        val jsonArray = JSONArray()
        logList.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("timestamp", entry.timestamp)
                put("title", entry.title)
                put("description", entry.description)
                put("type", entry.type.name)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_LOGS_JSON, jsonArray.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "aegis_security_prefs"
        private const val KEY_PIN = "key_security_pin"
        private const val KEY_IS_PIN_SET = "key_is_pin_set"
        private const val KEY_IS_ARMED = "key_is_armed"
        private const val KEY_CHARGER_ALERT = "key_charger_alert"
        private const val KEY_SAFE_MODE = "key_safe_mode"
        private const val KEY_LOGS_JSON = "key_logs_json"
        private const val DEFAULT_PIN = "1234"
        private const val MAX_LOGS = 50

        private var isSynced = false
        private val _isArmed = MutableStateFlow(false)
        private val _isChargerAlertEnabled = MutableStateFlow(true)
        private val _isSafeModeEnabled = MutableStateFlow(false)
        private val _isAlarmRinging = MutableStateFlow(false)
        private val _isPinSet = MutableStateFlow(false)
        private val _logs = MutableStateFlow<List<SecurityLogEntry>>(emptyList())

        private fun syncWithPrefs(prefs: SharedPreferences) {
            if (!isSynced) {
                isSynced = true
                _isArmed.value = prefs.getBoolean(KEY_IS_ARMED, false)
                _isChargerAlertEnabled.value = prefs.getBoolean(KEY_CHARGER_ALERT, true)
                _isSafeModeEnabled.value = prefs.getBoolean(KEY_SAFE_MODE, false)
                _isPinSet.value = prefs.getBoolean(KEY_IS_PIN_SET, false)
                _logs.value = loadLogs(prefs)
            }
        }

        private fun loadLogs(prefs: SharedPreferences): List<SecurityLogEntry> {
            val jsonStr = prefs.getString(KEY_LOGS_JSON, null) ?: return defaultLogs()
            val result = mutableListOf<SecurityLogEntry>()
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val typeName = obj.optString("type", SecurityLogEntry.LogType.INFO.name)
                    val type = try { SecurityLogEntry.LogType.valueOf(typeName) } catch (e: Exception) { SecurityLogEntry.LogType.INFO }
                    result.add(
                        SecurityLogEntry(
                            id = obj.optString("id", i.toString()),
                            timestamp = obj.optString("timestamp", ""),
                            title = obj.optString("title", ""),
                            description = obj.optString("description", ""),
                            type = type
                        )
                    )
                }
            } catch (e: Exception) {
                return defaultLogs()
            }
            return if (result.isEmpty()) defaultLogs() else result
        }

        private fun defaultLogs(): List<SecurityLogEntry> {
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
            return listOf(
                SecurityLogEntry(
                    timestamp = dateFormat.format(Date()),
                    title = "Aegis System Initialized",
                    description = "Anti-theft protection engine ready.",
                    type = SecurityLogEntry.LogType.INFO
                )
            )
        }
    }
}

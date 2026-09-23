package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.SecurityPreferences
import com.example.ui.FakeShutdownActivity

class TheftProtectionService : Service() {

    private lateinit var preferences: SecurityPreferences
    private lateinit var alarmAudioPlayer: AlarmAudioPlayer
    private var wakeLock: PowerManager.WakeLock? = null
    private var chargerReceiver: BroadcastReceiver? = null
    private var volumeReceiver: BroadcastReceiver? = null
    private var volumeObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        preferences = SecurityPreferences(this)
        alarmAudioPlayer = AlarmAudioPlayer(this)

        acquireWakeLock()
        registerChargerReceiver()
        registerVolumeMonitoring()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_ARM_SERVICE

        val notification = createForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        when (action) {
            ACTION_ARM_SERVICE -> {
                preferences.setArmed(true)
            }
            ACTION_DISARM_SERVICE -> {
                preferences.setArmed(false)
                stopAlarm()
            }
            ACTION_START_ALARM -> {
                val reason = intent?.getStringExtra(EXTRA_ALARM_REASON) ?: "Security trigger activated!"
                startAlarm(reason)
            }
            ACTION_STOP_ALARM -> {
                stopAlarm()
            }
        }

        return START_STICKY
    }

    private fun startAlarm(reason: String) {
        preferences.setAlarmRinging(true, reason)
        alarmAudioPlayer.startSiren()

        // Update foreground notification to ALERT
        val alertNotification = createAlertNotification(reason)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, alertNotification)
    }

    private fun stopAlarm() {
        preferences.setAlarmRinging(false)
        alarmAudioPlayer.stopSiren()

        // Restore normal notification
        val normalNotification = createForegroundNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, normalNotification)
    }

    private fun registerChargerReceiver() {
        if (chargerReceiver == null) {
            chargerReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                        Log.w("TheftProtectionService", "Power disconnected broadcast received!")
                        if (preferences.isArmed.value && preferences.isChargerAlertEnabled.value) {
                            startAlarm("Charger Unplugged Alarm!")
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_POWER_CONNECTED)
            }
            registerReceiver(chargerReceiver, filter)
        }
    }

    private fun registerVolumeMonitoring() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (volumeReceiver == null) {
            volumeReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                        val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                        val newVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                        val prevVol = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)

                        if (preferences.isArmed.value) {
                            val maxVol = if (streamType >= 0) audioManager.getStreamMaxVolume(streamType) else -1
                            if (newVol < prevVol || (maxVol > 0 && newVol < maxVol)) {
                                maximizeAllVolumes(audioManager)
                                handleVolumeTampering()
                            }
                        }
                    }
                }
            }
            try {
                val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                registerReceiver(volumeReceiver, filter)
            } catch (e: Exception) {
                Log.e("TheftProtectionService", "Error registering volume receiver: ${e.message}")
            }
        }

        if (volumeObserver == null) {
            volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    if (preferences.isArmed.value) {
                        val currentMusicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val currentAlarmVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                        val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

                        if (currentMusicVol < maxMusicVol || currentAlarmVol < maxAlarmVol) {
                            maximizeAllVolumes(audioManager)
                            handleVolumeTampering()
                        }
                    }
                }
            }
            try {
                contentResolver.registerContentObserver(
                    android.provider.Settings.System.CONTENT_URI,
                    true,
                    volumeObserver!!
                )
            } catch (e: Exception) {
                Log.e("TheftProtectionService", "Error registering volume observer: ${e.message}")
            }
        }
    }

    private fun maximizeAllVolumes(audioManager: AudioManager) {
        val streams = intArrayOf(
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
        )
        for (s in streams) {
            try {
                val max = audioManager.getStreamMaxVolume(s)
                audioManager.setStreamVolume(s, max, 0)
            } catch (_: Exception) {}
        }
    }

    private fun handleVolumeTampering() {
        startAlarm("Volume Decrease Tampering Detected!")

        val pinIntent = Intent(this, FakeShutdownActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_DIRECT_ALARM, true)
            putExtra(EXTRA_REASON_TEXT, "Volume Decrease Tampering Detected!")
        }
        startActivity(pinIntent)
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AegisAntiTheft::ServiceWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes max per acquire cycle
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e("TheftProtectionService", "Error releasing WakeLock: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aegis Security Protection Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors device security, charger disconnection, and anti-shutdown triggers."
                setSound(null, null)
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aegis Security Active")
            .setContentText("Device protected against theft & power-off tampering.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createAlertNotification(reason: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ SIREN ALARM TRIGGERED!")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(0xFFFF3B30.toInt())
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        chargerReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        volumeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        volumeObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
        }
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "aegis_protection_channel"
        const val NOTIFICATION_ID = 8842

        const val ACTION_ARM_SERVICE = "com.example.action.ARM_SERVICE"
        const val ACTION_DISARM_SERVICE = "com.example.action.DISARM_SERVICE"
        const val ACTION_START_ALARM = "com.example.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.action.STOP_ALARM"
        const val EXTRA_ALARM_REASON = "extra_alarm_reason"
        const val EXTRA_DIRECT_ALARM = "extra_direct_alarm"
        const val EXTRA_REASON_TEXT = "extra_reason_text"
    }
}

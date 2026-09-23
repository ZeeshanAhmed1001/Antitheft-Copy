package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.RingtoneManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.sin

class AlarmAudioPlayer(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var originalVolume = 0
    private var audioFocusRequest: AudioFocusRequest? = null

    private fun maximizeVolume() {
        val streams = intArrayOf(
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
        )
        for (stream in streams) {
            try {
                val max = audioManager.getStreamMaxVolume(stream)
                audioManager.setStreamVolume(stream, max, 0)
            } catch (_: Exception) {}
        }
    }

    fun startSiren() {
        if (isPlaying) return
        isPlaying = true

        try {
            // Save current volume and max out all audio streams
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            maximizeVolume()

            // Continuous Volume Enforcement Loop so user/thief cannot turn volume down
            thread(name = "VolumeLockThread") {
                while (isPlaying) {
                    maximizeVolume()
                    try {
                        Thread.sleep(400)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }

            // Request Audio Focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(focusAttrs)
                    .build()

                audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            // Try default system alarm ringtone first
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            if (alarmUri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } else {
                // Fallback to custom synthesized dual-tone emergency siren
                startSynthesizedSiren()
            }
        } catch (e: Exception) {
            Log.e("AlarmAudioPlayer", "Error playing media player alarm, using synth siren: ${e.message}")
            startSynthesizedSiren()
        }
    }

    private fun startSynthesizedSiren() {
        thread(name = "SirenSynthThread") {
            val sampleRate = 44100
            val buffSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val samples = ShortArray(buffSize)
            var phase = 0.0
            var time = 0.0

            while (isPlaying) {
                // Dual frequency sweep (800 Hz to 1400 Hz police siren modulation)
                val freq = 800.0 + 600.0 * (0.5 + 0.5 * sin(2.0 * Math.PI * 1.5 * time))
                val phaseInc = 2.0 * Math.PI * freq / sampleRate

                for (i in samples.indices) {
                    samples[i] = (sin(phase) * 32767).toInt().toShort()
                    phase += phaseInc
                    if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI
                }

                track.write(samples, 0, samples.size)
                time += buffSize.toDouble() / sampleRate
            }

            try {
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }

    fun stopSiren() {
        isPlaying = false

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmAudioPlayer", "Error stopping MediaPlayer: ${e.message}")
        }

        try {
            audioTrack?.let {
                it.stop()
                it.release()
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.e("AlarmAudioPlayer", "Error stopping AudioTrack: ${e.message}")
        }

        // Abandon audio focus
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}

        // Restore volume if safe
        try {
            if (originalVolume > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            }
        } catch (_: Exception) {}
    }
}

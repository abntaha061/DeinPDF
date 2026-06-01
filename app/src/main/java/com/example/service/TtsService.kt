package com.example.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.util.Locale

class TtsService : Service() {

    companion object {
        const val ACTION_SPEAK = "com.example.pdfreader.SPEAK"
        const val ACTION_STOP = "com.example.pdfreader.STOP"
        const val EXTRA_TEXT = "text"
        const val EXTRA_LANG = "lang"
        const val EXTRA_SPEED = "speed"
        const val CHANNEL_ID = "tts_channel"
        const val NOTIFICATION_ID = 1001
    }

    private var tts: TextToSpeech? = null
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isPlaying = true
                        updateNotification("جاري القراءة بصوت مسموع...")
                    }
                    override fun onDone(utteranceId: String?) {
                        isPlaying = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                        stopSelf()
                    }
                    override fun onError(utteranceId: String?) {
                        isPlaying = false
                        stopSelf()
                    }
                })
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SPEAK -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return START_NOT_STICKY
                val lang = intent.getStringExtra(EXTRA_LANG) ?: "de"
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification("جاري تحضير النطق..."),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification("جاري تحضير النطق..."))
                }

                tts?.let { engine ->
                    engine.language = when(lang) {
                        "de" -> Locale.GERMAN
                        "ar" -> Locale.forLanguageTag("ar")
                        else -> Locale.getDefault()
                    }
                    engine.setSpeechRate(speed)
                    val params = android.os.Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "tts_$startId")
                    }
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tts_$startId")
                }
            }
            ACTION_STOP -> {
                tts?.stop()
                isPlaying = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, TtsService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("المساعد الصوتي")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "قراءة PDF بصوت مسموع",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار التحكم في نطق الكلمات والجمل الألمانية"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

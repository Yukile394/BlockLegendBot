package com.silvera.blocklegendbot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class BotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(
            "bot_channel",
            "Bot Servisi",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Block Legend Bot çalışıyor" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }
}

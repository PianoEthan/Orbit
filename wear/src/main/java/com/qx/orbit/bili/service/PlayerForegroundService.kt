package com.qx.orbit.bili.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder

class PlayerForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        
        val action = intent.action
        if (action == "STOP") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent.getStringExtra("title") ?: "Orbit"
        val isPlaying = intent.getBooleanExtra("isPlaying", false)
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("token", MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("token")
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("orbit_player", "播放控制", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        
        val activityClass = try {
            Class.forName("com.qx.orbit.bili.presentation.MainActivity")
        } catch (e: Exception) { null }
        
        val pendingIntent = if (activityClass != null) {
            val activityIntent = Intent(this, activityClass).apply {
                this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE)
        } else null
        
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "orbit_player")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        
        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(if (isPlaying) "正在播放" else "已暂停")
            .setOngoing(isPlaying)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            
        if (token != null) {
            builder.setStyle(Notification.MediaStyle().setMediaSession(token))
        }
        
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }
        
        try {
            startForeground(1001, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return START_NOT_STICKY
    }
}

package com.example.a24imbeded_sw

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // 스프링부트 서버에서 메세지가 전송되면 자동 호출됨
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val sensorId = remoteMessage.data["sensorId"] ?: "S03"
        val title = remoteMessage.notification?.title ?: "⚠️ 긴급 누수 감지"
        val body = remoteMessage.notification?.body ?: "이상치가 감지되었습니다. 즉시 확인하세요."

        sendNotification(sensorId, title, body)
    }

    private fun sendNotification(sensorId: String, title: String, body: String) {
        // 알림 클릭 시 MainActivity로 이동하며 해당 센서 ID 전달 (딥링크)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_SENSOR_ID", sensorId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "EMERGENCY_CHANNEL"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // 안드로이드 8.0 이상 대응 알림 채널 생성
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "긴급 경보 알림", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 생성 및 출력
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
package com.example.swproject

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "critical_channel"
        private const val NOTIFICATION_ID = 200
    }

    // ==================================================
    // FCM 토큰이 새로 발급되거나 변경될 때
    // ==================================================

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // 서버에 새 토큰 전달
        sendTokenToServer(token)
    }

    // ==================================================
    // FCM 메시지 수신
    // ==================================================

    override fun onMessageReceived(
        remoteMessage: RemoteMessage
    ) {
        super.onMessageReceived(remoteMessage)

        val title =
            remoteMessage.notification?.title
                ?: remoteMessage.data["title"]
                ?: "수위 알림"

        val message =
            remoteMessage.notification?.body
                ?: remoteMessage.data["message"]
                ?: "센서 상태가 변경되었습니다."

        val status =
            remoteMessage.data["status"]
                ?: "CRITICAL"

        showNotification(
            title,
            message,
            status
        )
    }

    // ==================================================
    // 알림 표시
    // ==================================================

    private fun showNotification(
        title: String,
        message: String,
        status: String
    ) {

        createNotificationChannel()

        val intent =
            Intent(
                this,
                ControlActivity::class.java
            ).apply {

                putExtra(
                    "status",
                    status.uppercase()
                )
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.mipmap.ic_launcher
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    message
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(
                    true
                )
                .setContentIntent(
                    pendingIntent
                )
                .build()

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            notification
        )
    }

    // ==================================================
    // 알림 채널 생성
    // ==================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "수위 위험 알림",
                    NotificationManager.IMPORTANCE_HIGH
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    // ==================================================
    // FCM 토큰 서버 전송
    // ==================================================

    private fun sendTokenToServer(
        token: String
    ) {

        Thread {

            try {

                val url =
                    java.net.URL(
                        "http://10.0.2.2:8080/api/devices/token"
                    )

                val connection =
                    url.openConnection()
                            as java.net.HttpURLConnection

                connection.requestMethod =
                    "POST"

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.doOutput =
                    true

                val json =
                    """{"token":"$token"}"""

                connection.outputStream.use { output ->

                    output.write(
                        json.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }

                val responseCode =
                    connection.responseCode

                android.util.Log.d(
                    "FCM",
                    "새 토큰 서버 등록 응답: $responseCode"
                )

                connection.disconnect()

            } catch (e: Exception) {

                android.util.Log.e(
                    "FCM",
                    "새 토큰 서버 등록 실패",
                    e
                )
            }

        }.start()
    }
}
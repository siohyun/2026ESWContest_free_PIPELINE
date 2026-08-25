package com.example.swproject

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.swproject.databinding.ActivityMainBinding
import android.annotation.SuppressLint

class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var currentStatus = "NORMAL"

    companion object {
        const val CHANNEL_ID = "danger_channel"
        const val NOTIFICATION_ID = 100
        const val NOTIFICATION_PERMISSION_CODE = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(binding.root)

        createNotificationChannel()
        requestNotificationPermission()

        // 처음에는 정상 이미지 표시
        binding.imgWater.setImageResource(R.drawable.normal)

        if (intent.getBooleanExtra("action_complete", false)) {

            currentStatus = "NORMAL"

            binding.imgWater.setImageResource(
                R.drawable.normal
            )

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager.cancel(
                NOTIFICATION_ID
            )
        }
    }

    // STM32에서 받은 신호에 따라 이미지 변경
    private fun changeImage(signal: String) {

        val status = signal.trim()

        runOnUiThread {

            when (status) {

                "NORMAL" -> {

                    binding.imgWater.setImageResource(
                        R.drawable.normal
                    )

                    currentStatus = "NORMAL"

                    val notificationManager =
                        getSystemService(
                            Context.NOTIFICATION_SERVICE
                        ) as NotificationManager

                    notificationManager.cancel(
                        NOTIFICATION_ID
                    )
                }


                "WARNING" -> {

                    binding.imgWater.setImageResource(
                        R.drawable.warning
                    )

                    // 같은 WARNING 상태에서 알림 반복 방지
                    if (currentStatus != "WARNING") {

                        showNotification(
                            "수위 경고",
                            "수위가 상승했습니다. 조치가 필요합니다.",
                            "WARNING"
                        )
                    }

                    currentStatus = "WARNING"
                }


                "DANGER" -> {

                    binding.imgWater.setImageResource(
                        R.drawable.danger
                    )

                    // DANGER 상태가 처음 발생했을 때 알림
                    if (currentStatus != "DANGER") {

                        showNotification(
                            "위험 발생",
                            "위험 수위입니다. 즉시 조치를 취해주세요.",
                            "DANGER"
                        )
                    }

                    currentStatus = "DANGER"
                }
            }
        }
    }


    // Bluetooth 데이터 수신
    private fun receiveData(socket: BluetoothSocket) {

        Thread {

            try {

                val reader =
                    socket.inputStream.bufferedReader()

                while (true) {

                    val message = reader.readLine()

                    if (message != null) {

                        changeImage(message)

                    } else {

                        break
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()
            }

        }.start()
    }


    // Android 8 이상 알림 채널 생성
    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "수위 위험 알림",
                NotificationManager.IMPORTANCE_HIGH
            )

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager.createNotificationChannel(
                channel
            )
        }
    }


    // Android 13 이상 알림 권한 요청
    private fun requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun showNotification(
        title: String,
        message: String,
        status: String
    ) {

        val intent = Intent(
            this,
            ControlActivity::class.java
        ).apply {
            putExtra("status", status)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat
            .from(this)
            .notify(
                NOTIFICATION_ID,
                notification
            )
    }
    // 메뉴 생성
    override fun onCreateOptionsMenu(
        menu: Menu?
    ): Boolean {

        menuInflater.inflate(
            R.menu.menu_test,
            menu
        )

        return true
    }


    // 메뉴 클릭
    override fun onOptionsItemSelected(
        item: MenuItem
    ): Boolean {

        return when (item.itemId) {

            R.id.controller -> {

                val intent = Intent(
                    this,
                    ControlActivity::class.java
                )

                intent.putExtra(
                    "status",
                    currentStatus
                )

                startActivity(intent)

                true
            }

            else -> super.onOptionsItemSelected(
                item
            )
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.getBooleanExtra("action_complete", false)) {

            currentStatus = "NORMAL"

            binding.imgWater.setImageResource(
                R.drawable.normal
            )

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager.cancel(NOTIFICATION_ID)
        }
    }
}
package com.example.swproject

import android.Manifest
import android.net.Uri
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
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.FrameLayout
import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout

class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var currentStatus = "NORMAL"
    private val sensorMarkers =
        mutableMapOf<String, ImageView>()

    private val sensorStatuses =
        mutableMapOf<String, String>()

    companion object {
        const val CHANNEL_ID = "danger_channel"
        const val NOTIFICATION_ID = 100
        const val NOTIFICATION_PERMISSION_CODE = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(binding.root)
        // 저장된 설정 가져오기
        val prefs = getSharedPreferences(
            "app_pref",
            MODE_PRIVATE
        )

        // 지도 업로드 여부 확인
        val mapUploaded = prefs.getBoolean(
            "map_uploaded",
            false
        )

        // 지도를 아직 업로드하지 않았다면
        // 지도 업로드 화면으로 이동
        if (!mapUploaded) {

            startActivity(
                Intent(
                    this,
                    MapUploadActivity::class.java
                )
            )

            finish()

            return
        }

        // 저장된 지도 불러오기
        val mapUri = prefs.getString(
            "map_uri",
            null
        )

        if (mapUri != null) {

            binding.imgMap.setImageURI(
                Uri.parse(mapUri)
            )
            loadSensors()
        }

        createNotificationChannel()
        requestNotificationPermission()

        // 처음에는 정상 이미지 표시
        binding.imgWater.setImageResource(R.drawable.normal)

        setupMapTouch()
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
    private fun changeSensorStatus(
        sensorId: String,
        status: String
    ) {

        val marker = sensorMarkers[sensorId]
            ?: return

        runOnUiThread {

            when (status) {

                "NORMAL" -> {
                    marker.setImageResource(
                        R.drawable.sensor_normal
                    )
                }

                "WARNING" -> {
                    marker.setImageResource(
                        R.drawable.sensor_warning
                    )
                }

                "DANGER" -> {
                    marker.setImageResource(
                        R.drawable.sensor_danger
                    )
                }
            }
        }
    }

    // STM32에서 받은 신호에 따라 이미지 변경
    private fun changeImage(signal: String) {

        val data = signal.trim().split("|")

        // 예: SENSOR_01|WARNING
        if (data.size != 2) {
            return
        }

        val sensorId = data[0]
        val status = data[1]

        // 센서의 현재 상태 저장
        sensorStatuses[sensorId] = status


        // 해당 센서 아이콘 변경
        changeSensorStatus(
            sensorId,
            status
        )

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

                    if (currentStatus != "WARNING") {

                        showNotification(
                            "수위 경고",
                            "$sensorId 수위가 상승했습니다.",
                            "WARNING"
                        )
                    }

                    currentStatus = "WARNING"
                }

                "DANGER" -> {

                    binding.imgWater.setImageResource(
                        R.drawable.danger
                    )

                    if (currentStatus != "DANGER") {

                        showNotification(
                            "위험 발생",
                            "$sensorId 위험 수위입니다.",
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

            // 조치 완료한 센서 ID
            val sensorId =
                intent.getStringExtra("sensor_id")

            // 해당 센서만 정상으로 변경
            if (sensorId != null) {

                sensorStatuses[sensorId] = "NORMAL"

                changeSensorStatus(
                    sensorId,
                    "NORMAL"
                )
            }

            // 모든 센서의 상태를 확인해서
            // 가장 위험한 상태를 메인 이미지에 표시
            updateOverallStatus()

            // 알림 제거
            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager.cancel(
                NOTIFICATION_ID
            )
        }
    }
    private fun updateOverallStatus() {

        var overallStatus = "NORMAL"

        for (status in sensorStatuses.values) {

            if (status == "DANGER") {

                overallStatus = "DANGER"
                break

            } else if (
                status == "WARNING" &&
                overallStatus != "DANGER"
            ) {

                overallStatus = "WARNING"
            }
        }

        currentStatus = overallStatus

        when (overallStatus) {

            "NORMAL" -> {

                binding.imgWater.setImageResource(
                    R.drawable.normal
                )
            }

            "WARNING" -> {

                binding.imgWater.setImageResource(
                    R.drawable.warning
                )
            }

            "DANGER" -> {

                binding.imgWater.setImageResource(
                    R.drawable.danger
                )
            }
        }
    }
    private fun setupMapTouch() {

        binding.imgMap.setOnTouchListener { _, event ->

            if (event.action == MotionEvent.ACTION_DOWN) {

                showSensorDialog(
                    event.x,
                    event.y
                )
            }

            true
        }
    }
    private fun showSensorDialog(
        x: Float,
        y: Float
    ) {

        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL

        layout.setPadding(
            40,
            10,
            40,
            10
        )

        val nameInput = EditText(this)

        nameInput.hint = "센서 이름"

        val idInput = EditText(this)

        idInput.hint = "STM32 센서 ID"

        layout.addView(nameInput)
        layout.addView(idInput)

        AlertDialog.Builder(this)
            .setTitle("센서 추가")
            .setView(layout)
            .setPositiveButton("추가") { _, _ ->

                val name =
                    nameInput.text.toString().trim()

                val id =
                    idInput.text.toString().trim()

                if (
                    name.isNotEmpty() &&
                    id.isNotEmpty()
                ) {

                    addSensorMarker(
                        name,
                        id,
                        x,
                        y
                    )
                }
            }
            .setNegativeButton(
                "취소",
                null
            )
            .show()
    }
    private fun addSensorMarker(
        name: String,
        id: String,
        x: Float,
        y: Float
    ) {

        val marker = ImageView(this)

        marker.setImageResource(
            R.drawable.sensor_normal
        )

// 센서 ID와 이미지 연결
        sensorMarkers[id] = marker

        val size = 70

        val params =
            FrameLayout.LayoutParams(
                size,
                size
            )

        params.leftMargin =
            x.toInt() - size / 2

        params.topMargin =
            y.toInt() - size / 2

        binding.mapContainer.addView(
            marker,
            params
        )
        saveSensor(
            name,
            id,
            x / binding.imgMap.width,
            y / binding.imgMap.height
        )

        marker.setOnClickListener {

            val intent = Intent(
                this,
                ControlActivity::class.java
            )

            intent.putExtra(
                "sensor_name",
                name
            )

            intent.putExtra(
                "sensor_id",
                id
            )

            intent.putExtra(
                "status",
                "NORMAL"
            )

            startActivity(intent)

        }
    }
    private fun saveSensor(
        name: String,
        id: String,
        x: Float,
        y: Float
    ) {

        val prefs = getSharedPreferences(
            "app_pref",
            MODE_PRIVATE
        )

        val sensors =
            prefs.getStringSet(
                "sensors",
                mutableSetOf()
            )?.toMutableSet()
                ?: mutableSetOf()

        val sensorData =
            "$name|$id|$x|$y"

        sensors.add(sensorData)

        prefs.edit()
            .putStringSet(
                "sensors",
                sensors
            )
            .apply()
    }
    private fun loadSensors() {

        val prefs = getSharedPreferences(
            "app_pref",
            MODE_PRIVATE
        )

        val sensors =
            prefs.getStringSet(
                "sensors",
                emptySet()
            ) ?: emptySet()

        for (sensor in sensors) {

            val data =
                sensor.split("|")

            if (data.size != 4) {
                continue
            }

            val name = data[0]
            val id = data[1]
            val x = data[2].toFloatOrNull()
            val y = data[3].toFloatOrNull()

            if (x != null && y != null) {

                addSensorMarkerWithoutSave(
                    name,
                    id,
                    x,
                    y
                )
            }
        }


    }
private fun addSensorMarkerWithoutSave(
    name: String,
    id: String,
    x: Float,
    y: Float
) {

    val marker = ImageView(this)

    marker.setImageResource(
        R.drawable.normal
    )

    val size = 70

    val params =
        FrameLayout.LayoutParams(
            size,
            size
        )

    params.leftMargin =
        (x * binding.imgMap.width).toInt() -
                size / 2

    params.topMargin =
        (y * binding.imgMap.height).toInt() -
                size / 2

    binding.mapContainer.addView(
        marker,
        params
    )

    marker.setOnClickListener {

        val intent = Intent(
            this,
            ControlActivity::class.java
        )

        intent.putExtra(
            "sensor_name",
            name
        )

        intent.putExtra(
            "sensor_id",
            id
        )

        intent.putExtra(
            "status",
            "NORMAL"
        )

        startActivity(intent)
    }
}
}
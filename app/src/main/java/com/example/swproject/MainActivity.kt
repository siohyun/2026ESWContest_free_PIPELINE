package com.example.swproject

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.swproject.databinding.ActivityMainBinding
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    // 전체 수로 상태
    private var currentStatus = "NORMAL"

    // 최근 CRITICAL 발생 시간
    private var lastCriticalTime = "없음"

    // 마지막 정보 수신 시간
    private var lastReceivedTime = "없음"

    // 센서 ID와 센서 이미지 연결
    private val sensorMarkers =
        mutableMapOf<String, ImageView>()

    // 센서 ID와 현재 상태 연결
    private val sensorStatuses =
        mutableMapOf<String, String>()

    // 백엔드 서버 주소
    // Android Emulator에서 로컬 Spring Boot를 사용할 경우
    private val serverBaseUrl =
        "http://10.0.2.2:8080"

    // ==================================================
    // SSE 실시간 통신
    // ==================================================

    private val sseClient =
        OkHttpClient.Builder()
            .build()

    private var eventSource: EventSource? = null

    companion object {

        const val CHANNEL_ID = "critical_channel"

        const val NOTIFICATION_ID = 100

        const val NOTIFICATION_PERMISSION_CODE = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(binding.root)

        // ==================================================
        // FCM 토큰 등록
        // ==================================================

        FirebaseMessaging.getInstance()
            .token
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {

                    val token = task.result

                    Log.d(
                        "FCM",
                        "FCM TOKEN = $token"
                    )

                    sendFcmTokenToServer(token)

                } else {

                    Log.e(
                        "FCM",
                        "FCM 토큰 가져오기 실패",
                        task.exception
                    )
                }
            }

        // ==================================================
        // STM32 / ESP32 연결 전 테스트
        // ==================================================


        // ==================================================
        // 저장된 설정 가져오기
        // ==================================================

        val prefs =
            getSharedPreferences(
                "app_pref",
                MODE_PRIVATE
            )

        // ==================================================
        // 지도 업로드 여부 확인
        // ==================================================

        val mapUploaded =
            prefs.getBoolean(
                "map_uploaded",
                false
            )

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

        // ==================================================
        // 저장된 지도 불러오기
        // ==================================================

        val mapUri =
            prefs.getString(
                "map_uri",
                null
            )

        if (mapUri != null) {

            binding.imgMap.setImageURI(
                Uri.parse(mapUri)
            )

            // 저장된 센서 표시
            loadSensors()
        }

        // ==================================================
        // 알림 설정
        // ==================================================

        createNotificationChannel()

        requestNotificationPermission()

        // ==================================================
        // 지도 터치
        // ==================================================

        setupMapTouch()

        // ==================================================
        // 백엔드에서 센서 상태 가져오기
        // ==================================================

        loadSensorsFromServer()

        // ==================================================
        // SSE 실시간 센서 상태 연결
        // ==================================================

        connectToSensorSse()

        // ==================================================
        // 조치 완료 후 돌아온 경우
        // ==================================================

        handleActionComplete(intent)
    }

    // ==================================================
    // SSE 실시간 센서 상태 연결
    //
    // 팀원 백엔드:
    // GET /api/sensors/stream
    //
    // 예:
    // event: sensor-update
    // data: {"id":"NODE_1", ...}
    // ==================================================

    private fun connectToSensorSse() {

        // 기존 SSE 연결이 있으면 종료
        eventSource?.cancel()

        val request =
            Request.Builder()
                .url(
                    "$serverBaseUrl/api/sensors/stream"
                )
                .header(
                    "Accept",
                    "text/event-stream"
                )
                .build()

        val listener =
            object : EventSourceListener() {

                override fun onOpen(
                    eventSource: EventSource,
                    response: Response
                ) {

                    Log.d(
                        "SSE",
                        "센서 SSE 연결 성공"
                    )
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {

                    Log.d(
                        "SSE",
                        "센서 데이터 수신: $data"
                    )

                    // 팀원 백엔드가
                    // sensor-update 이벤트를 보내는 경우
                    if (
                        type == "sensor-update" ||
                        type == null
                    ) {

                        handleSseSensorData(data)
                    }
                }

                override fun onClosed(
                    eventSource: EventSource
                ) {

                    Log.d(
                        "SSE",
                        "센서 SSE 연결 종료"
                    )
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {

                    Log.e(
                        "SSE",
                        "센서 SSE 연결 실패",
                        t
                    )

                    // 연결이 끊어지면
                    // 3초 후 재연결
                    window.decorView.postDelayed(
                        {

                            if (
                                !isFinishing &&
                                !isDestroyed
                            ) {

                                connectToSensorSse()
                            }

                        },
                        3000
                    )
                }
            }

        eventSource =
            EventSources
                .createFactory(
                    sseClient
                )
                .newEventSource(
                    request,
                    listener
                )
    }

    // ==================================================
    // SSE 센서 데이터 처리
    // ==================================================

    private fun handleSseSensorData(
        data: String
    ) {

        try {

            val sensor =
                JSONObject(data)

            val backendId =
                sensor.optString(
                    "id"
                )

            val name =
                sensor.optString(
                    "name",
                    "센서"
                )

            val status =
                sensor.optString(
                    "status",
                    "NORMAL"
                ).uppercase()

            val receivedTime =
                sensor.optString(
                    "lastReceivedTime",
                    ""
                )

            // NODE_1 → SENSOR_01
            val androidSensorId =
                findAndroidSensorId(
                    backendId
                )

            // 센서별 상태 저장
            sensorStatuses[
                androidSensorId
            ] = status

            // 마지막 정보 수신 시간
            lastReceivedTime =
                receivedTime.ifEmpty {
                    getCurrentTime()
                }

            // CRITICAL 발생 시간
            if (status == "CRITICAL") {

                lastCriticalTime =
                    receivedTime.ifEmpty {
                        getCurrentTime()
                    }
            }

            // 센서 아이콘 변경
            changeSensorStatus(
                androidSensorId,
                status
            )

            // 전체 수로 상태 계산
            updateOverallStatus()

            Log.d(
                "SSE",
                "센서 업데이트: $name / $backendId / $status"
            )

        } catch (e: Exception) {

            Log.e(
                "SSE",
                "SSE 센서 데이터 처리 실패: $data",
                e
            )
        }
    }

    // ==================================================
    // 조치 완료 처리
    // ==================================================

    private fun handleActionComplete(
        intent: Intent
    ) {

        if (
            intent.getBooleanExtra(
                "action_complete",
                false
            )
        ) {

            val sensorId =
                intent.getStringExtra(
                    "sensor_id"
                )

            if (sensorId != null) {

                sensorStatuses[sensorId] =
                    "NORMAL"

                changeSensorStatus(
                    sensorId,
                    "NORMAL"
                )
            }

            updateOverallStatus()

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager.cancel(
                NOTIFICATION_ID
            )
        }
    }

    // ==================================================
    // 현재 시간
    // ==================================================

    private fun getCurrentTime(): String {

        val formatter =
            SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            )

        return formatter.format(
            Date()
        )
    }

    // ==================================================
    // 센서 ID → 백엔드 센서 ID 변환
    //
    // 예:
    // SENSOR_01 → NODE_1
    // SENSOR_02 → NODE_2
    //
    // NODE_1은 그대로 사용
    // ==================================================

    private fun convertToBackendSensorId(
        sensorId: String
    ): String {

        if (
            sensorId.startsWith(
                "NODE_",
                ignoreCase = true
            )
        ) {

            return sensorId
        }

        val number =
            sensorId
                .filter { it.isDigit() }
                .toIntOrNull()

        return if (number != null) {

            "NODE_$number"

        } else {

            sensorId
        }
    }

    // ==================================================
    // 백엔드 센서 목록 조회
    //
    // GET /api/sensors
    // ==================================================

    private fun loadSensorsFromServer() {

        Thread {

            try {

                val url =
                    URL(
                        "$serverBaseUrl/api/sensors"
                    )

                val connection =
                    url.openConnection()
                            as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    5000

                connection.readTimeout =
                    5000

                val responseCode =
                    connection.responseCode

                if (
                    responseCode ==
                    HttpURLConnection.HTTP_OK
                ) {

                    val response =
                        connection.inputStream
                            .bufferedReader()
                            .use {
                                it.readText()
                            }

                    Log.d(
                        "SENSOR_API",
                        "센서 조회 성공: $response"
                    )

                    runOnUiThread {

                        applyServerSensorData(
                            response
                        )
                    }

                } else {

                    Log.e(
                        "SENSOR_API",
                        "센서 조회 실패: $responseCode"
                    )
                }

                connection.disconnect()

            } catch (e: Exception) {

                Log.e(
                    "SENSOR_API",
                    "백엔드 센서 조회 오류",
                    e
                )
            }

        }.start()
    }

    // ==================================================
    // 백엔드 센서 데이터 적용
    // ==================================================

    private fun applyServerSensorData(
        response: String
    ) {

        try {

            val jsonArray =
                JSONArray(response)

            for (
            i in 0 until jsonArray.length()
            ) {

                val sensor =
                    jsonArray.getJSONObject(i)

                val backendId =
                    sensor.optString(
                        "id"
                    )

                val name =
                    sensor.optString(
                        "name",
                        "센서"
                    )

                val status =
                    sensor.optString(
                        "status",
                        "NORMAL"
                    ).uppercase()

                val receivedTime =
                    sensor.optString(
                        "lastReceivedTime",
                        ""
                    )

                // 백엔드 NODE_1과
                // Android SENSOR_01 등을 연결
                val androidSensorId =
                    findAndroidSensorId(
                        backendId
                    )

                sensorStatuses[
                    androidSensorId
                ] = status

                if (
                    receivedTime.isNotEmpty()
                ) {

                    lastReceivedTime =
                        receivedTime
                }

                if (
                    status == "CRITICAL"
                ) {

                    if (
                        lastCriticalTime ==
                        "없음"
                    ) {

                        lastCriticalTime =
                            receivedTime.ifEmpty {
                                getCurrentTime()
                            }
                    }
                }

                changeSensorStatus(
                    androidSensorId,
                    status
                )

                Log.d(
                    "SENSOR_API",
                    "센서: $name / $backendId / $status"
                )
            }

            updateOverallStatus()

        } catch (e: Exception) {

            Log.e(
                "SENSOR_API",
                "센서 JSON 처리 실패",
                e
            )
        }
    }

    // ==================================================
    // Android 센서 ID 찾기
    // ==================================================

    private fun findAndroidSensorId(
        backendId: String
    ): String {

        // 정확히 같은 ID가 있으면 사용
        if (
            sensorMarkers.containsKey(
                backendId
            )
        ) {

            return backendId
        }

        // NODE_1 → SENSOR_01
        val number =
            backendId
                .filter { it.isDigit() }
                .toIntOrNull()

        if (number != null) {

            val sensorId =
                String.format(
                    Locale.US,
                    "SENSOR_%02d",
                    number
                )

            if (
                sensorMarkers.containsKey(
                    sensorId
                )
            ) {

                return sensorId
            }
        }

        return backendId
    }

    // ==================================================
    // 센서 아이콘 상태 변경
    // ==================================================

    private fun changeSensorStatus(
        sensorId: String,
        status: String
    ) {

        val marker =
            sensorMarkers[sensorId]
                ?: return

        runOnUiThread {

            when (
                status.uppercase()
            ) {

                "NORMAL" -> {

                    marker.setImageResource(
                        R.drawable.sensor_normal
                    )
                }

                "WARM" -> {

                    marker.setImageResource(
                        R.drawable.sensor_warning
                    )
                }

                "CRITICAL" -> {

                    marker.setImageResource(
                        R.drawable.sensor_critical
                    )
                }
            }
        }
    }

    // ==================================================
    // STM32 / ESP32에서 받은 데이터 처리
    //
    // 예:
    // SENSOR_01|NORMAL
    // SENSOR_01|WARM
    // SENSOR_01|CRITICAL
    // ==================================================

    private fun changeImage(
        signal: String
    ) {

        val data =
            signal
                .trim()
                .split("|")

        if (
            data.size != 2
        ) {

            return
        }

        val sensorId =
            data[0]

        val status =
            data[1]
                .uppercase()

        // 마지막 정보 수신 시간
        lastReceivedTime =
            getCurrentTime()

        // CRITICAL 발생 시간
        if (
            status == "CRITICAL"
        ) {

            lastCriticalTime =
                lastReceivedTime
        }

        // 센서별 상태 저장
        sensorStatuses[sensorId] =
            status

        // 센서 아이콘 변경
        changeSensorStatus(
            sensorId,
            status
        )

        runOnUiThread {

            when (status) {

                "NORMAL" -> {
                    // 정상
                }

                "WARM" -> {

                    if (
                        currentStatus != "WARM"
                    ) {

                        showNotification(
                            "수위 경고",
                            "$sensorId 수위가 상승했습니다.",
                            "WARM"
                        )
                    }
                }

                "CRITICAL" -> {

                    if (
                        currentStatus != "CRITICAL"
                    ) {

                        showNotification(
                            "위험 발생",
                            "$sensorId 위험 수위입니다.",
                            "CRITICAL"
                        )
                    }
                }
            }

            updateOverallStatus()
        }
    }

    // ==================================================
    // 전체 수로 상태 계산
    //
    // CRITICAL 하나라도 있으면
    // 전체 = CRITICAL
    //
    // CRITICAL이 없고 WARM이 있으면
    // 전체 = WARM
    //
    // 모두 정상이라면
    // 전체 = NORMAL
    // ==================================================

    private fun updateOverallStatus() {

        var overallStatus =
            "NORMAL"

        for (
        status in sensorStatuses.values
        ) {

            val normalizedStatus =
                status.uppercase()

            if (
                normalizedStatus ==
                "CRITICAL"
            ) {

                overallStatus =
                    "CRITICAL"

                break
            }

            if (
                normalizedStatus ==
                "WARM"
            ) {

                overallStatus =
                    "WARM"
            }
        }

        currentStatus =
            overallStatus
    }

    // ==================================================
    // Bluetooth 데이터 수신
    // ==================================================

    private fun receiveData(
        socket: BluetoothSocket
    ) {

        Thread {

            try {

                val reader =
                    socket.inputStream
                        .bufferedReader()

                while (true) {

                    val message =
                        reader.readLine()

                    if (
                        message != null
                    ) {

                        changeImage(
                            message
                        )

                    } else {

                        break
                    }
                }

            } catch (
                e: Exception
            ) {

                Log.e(
                    "BLUETOOTH",
                    "데이터 수신 오류",
                    e
                )
            }

        }.start()
    }

    // ==================================================
    // Android 8 이상
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

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager
                .createNotificationChannel(
                    channel
                )
        }
    }

    // ==================================================
    // Android 13 이상
    // 알림 권한
    // ==================================================

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
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

    // ==================================================
    // 알림 표시
    // ==================================================

    @SuppressLint("MissingPermission")
    private fun showNotification(
        title: String,
        message: String,
        status: String
    ) {

        val intent =
            Intent(
                this,
                ControlActivity::class.java
            ).apply {

                putExtra(
                    "status",
                    status
                )

                putExtra(
                    "last_critical_time",
                    lastCriticalTime
                )

                putExtra(
                    "last_received_time",
                    lastReceivedTime
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

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) !=
            PackageManager.PERMISSION_GRANTED
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

    // ==================================================
    // 메뉴 생성
    // ==================================================

    override fun onCreateOptionsMenu(
        menu: Menu?
    ): Boolean {

        menuInflater.inflate(
            R.menu.menu_test,
            menu
        )

        return true
    }

    // ==================================================
    // 메뉴 클릭
    // ==================================================

    override fun onOptionsItemSelected(
        item: MenuItem
    ): Boolean {

        return when (
            item.itemId
        ) {

            R.id.controller -> {

                val intent =
                    Intent(
                        this,
                        ControlActivity::class.java
                    )

                intent.putExtra(
                    "status",
                    currentStatus
                )

                intent.putExtra(
                    "last_critical_time",
                    lastCriticalTime
                )

                intent.putExtra(
                    "last_received_time",
                    lastReceivedTime
                )

                startActivity(
                    intent
                )

                true
            }

            else ->
                super.onOptionsItemSelected(
                    item
                )
        }
    }

    // ==================================================
    // 지도 터치
    // ==================================================

    private fun setupMapTouch() {

        binding.imgMap.setOnTouchListener { _, event ->

            if (
                event.action ==
                MotionEvent.ACTION_DOWN
            ) {

                showSensorDialog(
                    event.x,
                    event.y
                )
            }

            true
        }
    }

    // ==================================================
    // 센서 추가 다이얼로그
    // ==================================================

    private fun showSensorDialog(
        x: Float,
        y: Float
    ) {

        val layout =
            LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            40,
            10,
            40,
            10
        )

        val nameInput =
            EditText(this)

        nameInput.hint =
            "센서 이름"

        val idInput =
            EditText(this)

        idInput.hint =
            "STM32 센서 ID"

        layout.addView(
            nameInput
        )

        layout.addView(
            idInput
        )

        AlertDialog.Builder(this)
            .setTitle(
                "센서 추가"
            )
            .setView(
                layout
            )
            .setPositiveButton(
                "추가"
            ) { _, _ ->

                val name =
                    nameInput.text
                        .toString()
                        .trim()

                val id =
                    idInput.text
                        .toString()
                        .trim()

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

    // ==================================================
    // 새 센서 추가
    // ==================================================

    private fun addSensorMarker(
        name: String,
        id: String,
        x: Float,
        y: Float
    ) {

        val marker =
            ImageView(this)

        marker.setImageResource(
            R.drawable.sensor_normal
        )

        sensorMarkers[id] =
            marker

        sensorStatuses[id] =
            "NORMAL"

        val size =
            70

        val params =
            FrameLayout.LayoutParams(
                size,
                size
            )

        params.leftMargin =
            x.toInt() -
                    size / 2

        params.topMargin =
            y.toInt() -
                    size / 2

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

            val intent =
                Intent(
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

            val status =
                sensorStatuses[id]
                    ?: "NORMAL"

            intent.putExtra(
                "status",
                status
            )

            intent.putExtra(
                "last_critical_time",
                lastCriticalTime
            )

            intent.putExtra(
                "last_received_time",
                lastReceivedTime
            )

            startActivity(
                intent
            )
        }
    }

    // ==================================================
    // 센서 저장
    // ==================================================

    private fun saveSensor(
        name: String,
        id: String,
        x: Float,
        y: Float
    ) {

        val prefs =
            getSharedPreferences(
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

        sensors.add(
            sensorData
        )

        prefs.edit()
            .putStringSet(
                "sensors",
                sensors
            )
            .apply()
    }

    // ==================================================
    // 저장된 센서 불러오기
    // ==================================================

    private fun loadSensors() {

        val prefs =
            getSharedPreferences(
                "app_pref",
                MODE_PRIVATE
            )

        val sensors =
            prefs.getStringSet(
                "sensors",
                emptySet()
            ) ?: emptySet()

        for (
        sensor in sensors
        ) {

            val data =
                sensor.split("|")

            if (
                data.size != 4
            ) {

                continue
            }

            val name =
                data[0]

            val id =
                data[1]

            val x =
                data[2].toFloatOrNull()

            val y =
                data[3].toFloatOrNull()

            if (
                x != null &&
                y != null
            ) {

                addSensorMarkerWithoutSave(
                    name,
                    id,
                    x,
                    y
                )
            }
        }
    }

    // ==================================================
    // 저장된 센서 표시
    // ==================================================

    private fun addSensorMarkerWithoutSave(
        name: String,
        id: String,
        x: Float,
        y: Float
    ) {

        val marker =
            ImageView(this)

        marker.setImageResource(
            R.drawable.sensor_normal
        )

        sensorMarkers[id] =
            marker

        sensorStatuses[id] =
            "NORMAL"

        val size =
            70

        val params =
            FrameLayout.LayoutParams(
                size,
                size
            )

        params.leftMargin =
            (
                    x *
                            binding.imgMap.width
                    ).toInt() -
                    size / 2

        params.topMargin =
            (
                    y *
                            binding.imgMap.height
                    ).toInt() -
                    size / 2

        binding.mapContainer.addView(
            marker,
            params
        )

        marker.setOnClickListener {

            val intent =
                Intent(
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

            val status =
                sensorStatuses[id]
                    ?: "NORMAL"

            intent.putExtra(
                "status",
                status
            )

            intent.putExtra(
                "last_critical_time",
                lastCriticalTime
            )

            intent.putExtra(
                "last_received_time",
                lastReceivedTime
            )

            startActivity(
                intent
            )
        }
    }

    // ==================================================
    // 새로운 Intent 처리
    // ==================================================

    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(
            intent
        )

        handleActionComplete(
            intent
        )
    }

    // ==================================================
    // FCM 토큰 서버 전송
    // ==================================================

    private fun sendFcmTokenToServer(
        token: String
    ) {

        Thread {

            try {

                val url =
                    URL(
                        "$serverBaseUrl/api/devices/token"
                    )

                val connection =
                    url.openConnection()
                            as HttpURLConnection

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

                connection.outputStream
                    .use { output ->

                        output.write(
                            json.toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }

                val responseCode =
                    connection.responseCode

                Log.d(
                    "FCM",
                    "서버 토큰 등록 응답: $responseCode"
                )

                connection.disconnect()

            } catch (
                e: Exception
            ) {

                Log.e(
                    "FCM",
                    "서버 토큰 등록 실패",
                    e
                )
            }

        }.start()
    }

    // ==================================================
    // Activity 종료 시 SSE 연결 종료
    // ==================================================

    override fun onDestroy() {

        eventSource?.cancel()

        sseClient.dispatcher.executorService.shutdown()

        super.onDestroy()
    }
}
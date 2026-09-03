package com.example.a24imbeded_sw

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

enum class SensorStatus { NORMAL, WARM, CRITICAL }

// 센서 데이터 모델 (id = ESP32 하드웨어 ID)
data class SensorNode(
    val id: String,              // ESP32 하드웨어 ID (예: "S01")
    var name: String,            // 사람이 알아보기 쉬운 센서 이름
    val xPercent: Float,
    val yPercent: Float,
    var status: SensorStatus,
    var lastReceivedTime: String,
    var lastAlertTime: String = "기록 없음"
)

class MainActivity : ComponentActivity() {

    private val sensorList = mutableStateListOf<SensorNode>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                // 기본 도면 경로 정의
                val defaultBlueprintUri = Uri.parse("android.resource://$packageName/${R.drawable.pipe_blueprint}")

                // ★ [수정 핵심 1] 초기값을 null로 설정하여 무조건 업로드/선택 화면부터 시작하도록 변경
                var mapUri by remember { mutableStateOf<Uri?>(null) }

                var selectedSensor by remember { mutableStateOf<SensorNode?>(null) }
                var showEmergencyGuide by remember { mutableStateOf(false) }
                var isAddMode by remember { mutableStateOf(false) }
                var pendingCoordinates by remember { mutableStateOf<Pair<Float, Float>?>(null) }

                // 팝업 배너 알림 메시지 상태
                var bannerMessage by remember { mutableStateOf<String?>(null) }

                Box(modifier = Modifier.fillMaxSize()) {
                    // mapUri가 null이면 업로드/선택 화면을 띄우고, Uri가 들어오면 관제 화면을 띄움
                    if (mapUri == null) {
                        MapUploadScreen(
                            onMapSelected = { uri ->
                                mapUri = uri
                                Toast.makeText(this@MainActivity, "도면이 업로드되었습니다.", Toast.LENGTH_SHORT).show()
                            },
                            onUseDefaultMap = {
                                mapUri = defaultBlueprintUri
                                Toast.makeText(this@MainActivity, "기본 도면으로 시작합니다.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        MainControlScreen(
                            mapUri = mapUri,
                            sensors = sensorList,
                            isAddMode = isAddMode,
                            onToggleAddMode = { isAddMode = !isAddMode },
                            onChangeMap = { mapUri = null },
                            onSensorClick = { sensor ->
                                if (!isAddMode) {
                                    selectedSensor = sensor
                                    if (sensor.status == SensorStatus.CRITICAL) {
                                        showEmergencyGuide = true
                                    }
                                }
                            },
                            onMapClick = { xPct, yPct ->
                                if (isAddMode) {
                                    pendingCoordinates = Pair(xPct, yPct)
                                }
                            }
                        )
                    }

                    // 상단 팝업 알림 배너
                    bannerMessage?.let { message ->
                        NotificationBanner(
                            message = message,
                            onDismiss = { bannerMessage = null }
                        )
                    }
                }

                // 센서 추가 팝업
                pendingCoordinates?.let { (xPct, yPct) ->
                    AddSensorDialog(
                        xPercent = xPct,
                        yPercent = yPct,
                        onDismiss = { pendingCoordinates = null },
                        onConfirm = { hardwareId, sensorName, initialStatus ->
                            val currentTime = "방금 전"
                            val alertTime = if (initialStatus != SensorStatus.NORMAL) currentTime else "기록 없음"

                            val newSensor = SensorNode(
                                id = hardwareId,
                                name = sensorName,
                                xPercent = xPct,
                                yPercent = yPct,
                                status = initialStatus,
                                lastReceivedTime = currentTime,
                                lastAlertTime = alertTime
                            )
                            sensorList.add(newSensor)
                            pendingCoordinates = null
                            isAddMode = false

                            if (initialStatus == SensorStatus.CRITICAL) {
                                selectedSensor = newSensor
                                showEmergencyGuide = true
                                bannerMessage = "⚠️ [위험] ${sensorName}($hardwareId) 센서 긴급 이상 발생!"
                            } else if (initialStatus == SensorStatus.WARM) {
                                bannerMessage = "⚡ [주의] ${sensorName}($hardwareId) 센서 점검 필요."
                            } else {
                                Toast.makeText(this@MainActivity, "'$hardwareId' 센서가 등록되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // 센서 상세 정보 바텀시트
                selectedSensor?.let { sensor ->
                    if (!showEmergencyGuide) {
                        SensorDetailBottomSheet(
                            sensor = sensor,
                            onDismiss = { selectedSensor = null },
                            onNameChange = { newName ->
                                val index = sensorList.indexOfFirst { it.id == sensor.id }
                                if (index != -1) {
                                    sensorList[index] = sensorList[index].copy(name = newName)
                                }
                                Toast.makeText(this, "센서명이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                            },
                            onOpenGuide = { showEmergencyGuide = true }
                        )
                    }
                }

                // 긴급 조치 가이드 팝업
                if (showEmergencyGuide && selectedSensor != null) {
                    EmergencyGuideDialog(
                        sensor = selectedSensor!!,
                        onDismiss = {
                            showEmergencyGuide = false
                            selectedSensor = null
                        },
                        onCompleteAction = {
                            val target = selectedSensor!!
                            val index = sensorList.indexOfFirst { it.id == target.id }

                            if (index != -1) {
                                sensorList[index] = sensorList[index].copy(
                                    status = SensorStatus.NORMAL
                                )
                            }

                            showEmergencyGuide = false
                            selectedSensor = null
                            bannerMessage = "✅ [완료] ${target.name}(${target.id}) 센서 조치가 완료되었습니다."
                        }
                    )
                }
            }
        }
    }
}

// 상단 팝업 알림 배너 UI
@Composable
fun NotificationBanner(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF323232)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "닫기",
                    color = Color(0xFFFFCC00),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

// ★ [수정 핵심 2] 지도/도면 업로드 및 기본 지도로 시작 선택 화면
@Composable
fun MapUploadScreen(
    onMapSelected: (Uri) -> Unit,
    onUseDefaultMap: () -> Unit // 기본 도면 선택 콜백 함수 추가
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onMapSelected(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = android.R.drawable.ic_menu_gallery),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("배수도 관제 시스템", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("관제를 시작하려면 도면을 새로 업로드하거나 기본 지도를 선택하세요.", color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(32.dp))

        // 옵션 1: 사용자의 직접 파일 선택
        Button(
            onClick = { launcher.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("📁 도면 이미지 파일 선택", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 옵션 2: 기본 이미지로 시작
        OutlinedButton(
            onClick = onUseDefaultMap,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("🗺️ 기본 지도로 시작하기", fontSize = 16.sp)
        }
    }
}

// 메인 관제 화면
@Composable
fun MainControlScreen(
    mapUri: Uri?,
    sensors: List<SensorNode>,
    isAddMode: Boolean,
    onToggleAddMode: () -> Unit,
    onChangeMap: () -> Unit,
    onSensorClick: (SensorNode) -> Unit,
    onMapClick: (Float, Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isAddMode) "📍 위치를 터치해 센서를 등록하세요" else "1층 배수도 메인 관제",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAddMode) Color(0xFFD32F2F) else Color.Black
                )
                Text(
                    text = "지도 재업로드",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.clickable { onChangeMap() }
                )
            }

            Button(
                onClick = onToggleAddMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAddMode) Color.Gray else Color(0xFF2196F3)
                )
            ) {
                Text(if (isAddMode) "취소" else "+ 센서 추가")
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFF0F0F0))
                .pointerInput(isAddMode) {
                    detectTapGestures { offset ->
                        if (isAddMode) {
                            val xPct = offset.x / size.width
                            val yPct = offset.y / size.height
                            onMapClick(xPct, yPct)
                        }
                    }
                }
        ) {
            val width = maxWidth
            val height = maxHeight

            AsyncImage(
                model = mapUri,
                contentDescription = "배관 도면",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            sensors.forEach { sensor ->
                val pinColor = when (sensor.status) {
                    SensorStatus.NORMAL -> Color(0xFF4CAF50)
                    SensorStatus.WARM -> Color(0xFFFFC107)
                    SensorStatus.CRITICAL -> Color(0xFFF44336)
                }

                Box(
                    modifier = Modifier
                        .offset(x = width * sensor.xPercent, y = height * sensor.yPercent)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(pinColor)
                        .clickable { onSensorClick(sensor) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 신규 센서 등록 팝업 컴포넌트
@Composable
fun AddSensorDialog(
    xPercent: Float,
    yPercent: Float,
    onDismiss: () -> Unit,
    onConfirm: (hardwareId: String, sensorName: String, initialStatus: SensorStatus) -> Unit
) {
    var hardwareId by remember { mutableStateOf("S01") }
    var sensorName by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(SensorStatus.NORMAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("신규 센서 위치 및 기기 등록", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("선택 위치: X(${String.format("%.2f", xPercent)}), Y(${String.format("%.2f", yPercent)})")
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = hardwareId,
                    onValueChange = { hardwareId = it },
                    label = { Text("ESP32 기기 ID (예: S01)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = sensorName,
                    onValueChange = { sensorName = it },
                    label = { Text("센서 표기 이름 (예: 1층 메인관)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("초기 테스트 상태 선택:")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SensorStatus.values().forEach { status ->
                        FilterChip(
                            selected = (selectedStatus == status),
                            onClick = { selectedStatus = status },
                            label = { Text(status.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (hardwareId.isNotBlank() && sensorName.isNotBlank()) {
                        onConfirm(hardwareId, sensorName, selectedStatus)
                    }
                }
            ) {
                Text("등록 완료")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

// 센서 상세 정보 바텀시트
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailBottomSheet(
    sensor: SensorNode,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onOpenGuide: () -> Unit
) {
    var nameText by remember { mutableStateOf(sensor.name) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Text("센서 상세 정보 [${sensor.id}]", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("센서명 변경") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onNameChange(nameText) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("수정 저장")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("현재 상태: ${sensor.status.name}")
            Text("최근 수신 시각: ${sensor.lastReceivedTime}")

            Text(
                text = "최근 이상(Critical/Warning) 발생 시각: ${sensor.lastAlertTime}",
                color = if (sensor.lastAlertTime != "기록 없음") Color(0xFFD32F2F) else Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (sensor.status == SensorStatus.CRITICAL) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onOpenGuide,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("긴급 조치 매뉴얼 보기", color = Color.White)
                }
            }
        }
    }
}

// 긴급 조치 가이드 팝업
@Composable
fun EmergencyGuideDialog(
    sensor: SensorNode,
    onDismiss: () -> Unit,
    onCompleteAction: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⚠️ 위험 조치 가이드",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F)
            )
        },
        text = {
            Column {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "• 센서 이름: ${sensor.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "• ESP32 ID: ${sensor.id}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD32F2F),
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("1. 해당 위치의 차단 밸브를 즉시 잠급니다.")
                Text("2. 배관 누수 및 센서 작동 상태를 현장 검토합니다.")
                Text("3. 점검 완료 후 아래 버튼을 누르면 서버로 조치 로그가 전송됩니다.")
            }
        },
        confirmButton = {
            Button(
                onClick = onCompleteAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("조치 완료 (시스템 정상화)", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
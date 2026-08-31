package com.example.swproject

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.swproject.databinding.ActivityControlBinding

class ControlActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityControlBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        // ==================================================
        // 센서 정보 받기
        // ==================================================

        // 센서 이름
        val sensorName =
            intent.getStringExtra(
                "sensor_name"
            ) ?: "센서"

        // 센서 ID
        val sensorId =
            intent.getStringExtra(
                "sensor_id"
            ) ?: ""

        // 현재 센서 상태
        // 백엔드와 동일하게
        // NORMAL / WARM / CRITICAL 사용
        val status =
            (
                    intent.getStringExtra(
                        "status"
                    ) ?: "NORMAL"
                    ).uppercase()

        // 최근 CRITICAL 발생 시간
        val lastCriticalTime =
            intent.getStringExtra(
                "last_critical_time"
            ) ?: "없음"

        // 마지막 정보 수신 시간
        val lastReceivedTime =
            intent.getStringExtra(
                "last_received_time"
            ) ?: "없음"

        // ==================================================
        // 제목
        // ==================================================

        binding.txtTitle.text =
            "$sensorName 상태"

        // ==================================================
        // 수로 상태
        // ==================================================

        binding.txtWaterStatus.text =
            "수로 상태 : $status"

        // ==================================================
        // 최근 CRITICAL 발생 시간
        // ==================================================

        binding.txtCriticalTime.text =
            "최근 CRITICAL 발생 : $lastCriticalTime"

        // ==================================================
        // 마지막 정보 수신 시간
        // ==================================================

        binding.txtLastReceived.text =
            "마지막 정보 수신 : $lastReceivedTime"

        // ==================================================
        // 상태별 조치 가이드
        // ==================================================

        when (status) {

            // --------------------------------------------------
            // 경고
            // --------------------------------------------------

            "WARM" -> {

                binding.txtGuide.text =
                    """
                    수위가 상승하고 있습니다.

                    [조치 방법]

                    1. 현장 수로의 상태를 확인해주세요.
                    2. 배수 시설의 상태를 확인해주세요.
                    3. 수위가 계속 상승하는지 확인해주세요.
                    4. 상황이 악화되면 안전한 장소로 이동해주세요.
                    """.trimIndent()
            }

            // --------------------------------------------------
            // 위험
            // --------------------------------------------------

            "CRITICAL" -> {

                binding.txtGuide.text =
                    """
                    위험 수위입니다.

                    즉시 현장 접근을 중지하고
                    주변 사람을 안전한 장소로 대피시켜주세요.

                    [조치 방법]

                    1. 수로 주변에 접근하지 마세요.
                    2. 주변 사람을 안전한 곳으로 이동시켜주세요.
                    3. 배수 시설 및 주변 침수 여부를 확인해주세요.
                    4. 위험 상황이 지속되면 관계 기관에 신고해주세요.
                    5. 안전이 확보될 때까지 현장에 접근하지 마세요.
                    """.trimIndent()
            }

            // --------------------------------------------------
            // 정상
            // --------------------------------------------------

            else -> {

                binding.txtGuide.text =
                    """
                    현재 수위는 정상입니다.

                    별도의 조치가 필요하지 않습니다.
                    """.trimIndent()
            }
        }

        // ==================================================
        // 조치 완료 버튼
        // ==================================================

        binding.btnComplete.setOnClickListener {

            val intent =
                Intent(
                    this,
                    MainActivity::class.java
                )

            // 조치 완료
            intent.putExtra(
                "action_complete",
                true
            )

            // 조치 완료한 센서 ID
            intent.putExtra(
                "sensor_id",
                sensorId
            )

            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

            startActivity(
                intent
            )

            finish()
        }
    }
}


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

        val status = intent.getStringExtra("status") ?: "NORMAL"

        when (status) {

            "WARNING" -> {

                binding.imgStatus.setImageResource(
                    R.drawable.warning
                )

                binding.txtTitle.text =
                    "수위 경고"

                binding.txtGuide.text =
                    """
                    수위가 상승했습니다.

                    배수 상태를 확인하고
                    필요한 조치를 진행해주세요.
                    """.trimIndent()
            }


            "DANGER" -> {

                binding.imgStatus.setImageResource(
                    R.drawable.danger
                )

                binding.txtTitle.text =
                    "위험 발생"

                binding.txtGuide.text =
                    """
                    위험 수위입니다.

                    즉시 현장 상태를 확인하고
                    안전 조치를 진행해주세요.
                    """.trimIndent()
            }


            else -> {

                binding.imgStatus.setImageResource(
                    R.drawable.normal
                )

                binding.txtTitle.text =
                    "정상 상태"

                binding.txtGuide.text =
                    "현재 수위는 정상입니다."
            }
        }


        binding.btnComplete.setOnClickListener {

            val intent = Intent(
                this,
                MainActivity::class.java
            )

            intent.putExtra(
                "action_complete",
                true
            )

            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

            startActivity(intent)

            finish()
        }
    }
}
package com.example.swproject

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.swproject.databinding.ActivityMapUploadBinding

class MapUploadActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMapUploadBinding.inflate(layoutInflater)
    }

    // 사용자가 선택한 지도
    private var selectedMapUri: Uri? = null

    // 이미지 파일 선택
    private val mapPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                selectedMapUri = uri

                // 앱에서 계속 사용할 수 있도록 권한 유지
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                // 선택한 지도 미리보기
                binding.imgPreview.setImageURI(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        // 지도 선택 버튼
        binding.btnUploadMap.setOnClickListener {

            mapPicker.launch(
                arrayOf("image/*")
            )
        }

        // 지도 저장하고 시작
        binding.btnStart.setOnClickListener {

            // 지도를 선택하지 않은 경우
            if (selectedMapUri == null) {

                Toast.makeText(
                    this,
                    "먼저 지도를 선택해주세요.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            // 지도 정보 저장
            val prefs = getSharedPreferences(
                "app_pref",
                MODE_PRIVATE
            )

            prefs.edit()
                .putString(
                    "map_uri",
                    selectedMapUri.toString()
                )
                .putBoolean(
                    "map_uploaded",
                    true
                )
                .apply()

            // MainActivity로 이동
            val intent = Intent(
                this,
                MainActivity::class.java
            )

            startActivity(intent)

            finish()
        }
    }
}
package com.example.a24imbeded_sw

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT

// 스프링부트로 전송할 데이터 구조 정의
data class ActionLogRequest(
    val sensorId: String,
    val actionDetails: String
)

data class NameUpdateRequest(
    val sensorId: String,
    val newName: String
)

interface ApiService {
    // 1. 센서명 변경 서버 전송 API
    @PUT("/api/sensors/name")
    fun updateSensorName(@Body request: NameUpdateRequest): Call<Void>

    // 2. 현장 조치 완료 로그 전송 API (시스템 정상화)
    @POST("/api/sensors/resolve")
    fun resolveEmergency(@Body request: ActionLogRequest): Call<Void>
}
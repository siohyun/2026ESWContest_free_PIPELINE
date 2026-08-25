package com.example.leak_monitor_backend.device.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenRequest(
    @NotBlank(message = "FCM 토큰은 비어 있을 수 없습니다.") String token
) {
}

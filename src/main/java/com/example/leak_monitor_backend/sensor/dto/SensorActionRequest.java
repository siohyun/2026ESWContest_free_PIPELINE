package com.example.leak_monitor_backend.sensor.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Android SensorActionRequestDto와 필드명이 정확히 일치해야 합니다.
 */
public record SensorActionRequest(
    @NotBlank(message = "조치 내용 메모는 비어 있을 수 없습니다.") String note,
    String actionTakenAt
) {
}

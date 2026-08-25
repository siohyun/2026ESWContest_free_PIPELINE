package com.example.leak_monitor_backend.sensor.dto;

/**
 * Android SensorActionResponseDto와 필드명이 정확히 일치해야 합니다.
 */
public record SensorActionResponse(
    String sensorId,
    String status,
    String message
) {
}

package com.example.leak_monitor_backend.sensor.dto;

import java.util.List;

/**
 * Android SensorDto와 필드명이 정확히 일치해야 합니다.
 */
public record SensorResponse(
    String sensorId,
    String sensorName,
    float relativeX,
    float relativeY,
    String status,
    Double lastValue,
    String unit,
    String updatedAt,
    String description,
    String valveImageUrl,
    List<String> emergencyInstructions
) {
}

package com.example.leak_monitor_backend.map.dto;

import com.example.leak_monitor_backend.sensor.dto.SensorResponse;
import java.util.List;

/**
 * Android MapDetailDto와 필드명이 정확히 일치해야 합니다.
 */
public record MapDetailResponse(
    String mapId,
    String mapName,
    String mapImageUrl,
    List<SensorResponse> sensors
) {
}

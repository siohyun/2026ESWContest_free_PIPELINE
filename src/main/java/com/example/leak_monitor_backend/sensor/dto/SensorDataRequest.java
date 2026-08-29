package com.example.leak_monitor_backend.sensor.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SensorDataRequest {
    private Integer nodeId;  // 노드 ID (1, 2 등)
    private Double voltage;  // 피에조 전압 (V)
    private Integer state;   // 상태값 (0: Normal, 1: Warn, 2: Critical)
}
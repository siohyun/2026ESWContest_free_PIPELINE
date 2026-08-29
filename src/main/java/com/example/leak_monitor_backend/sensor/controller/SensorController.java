package com.example.leak_monitor_backend.sensor.controller;

import com.example.leak_monitor_backend.sensor.dto.SensorActionRequest;
import com.example.leak_monitor_backend.sensor.dto.SensorResponse;
import com.example.leak_monitor_backend.sensor.dto.SensorStatusReportRequest;
import com.example.leak_monitor_backend.sensor.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sensors")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    // 전체 센서 목록 조회 API
    @GetMapping
    public ResponseEntity<List<SensorResponse>> getAllSensors() {
        return ResponseEntity.ok(sensorService.getAllSensors());
    }

    // 센서 상태 등록/업데이트 API
    @PostMapping("/report")
    public ResponseEntity<SensorResponse> registerOrUpdateSensor(@RequestBody SensorStatusReportRequest request) {
        return ResponseEntity.ok(sensorService.registerOrUpdateSensor(request));
    }

    // 센서 액션 제어 API
    @PostMapping("/action")
    public ResponseEntity<SensorResponse> processAction(@RequestBody SensorActionRequest request) {
        return ResponseEntity.ok(sensorService.processAction(request));
    }
}
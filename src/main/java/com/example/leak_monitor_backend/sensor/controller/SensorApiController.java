package com.example.leak_monitor_backend.sensor.controller;

import com.example.leak_monitor_backend.sensor.dto.SensorDataRequest;
import com.example.leak_monitor_backend.sensor.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sensors")
@RequiredArgsConstructor
public class SensorApiController {

    private final SensorService sensorService;

    @PostMapping("/data")
    public ResponseEntity<Map<String, Object>> receiveSensorData(@RequestBody SensorDataRequest request) {
        System.out.println("====== ESP32 통합보드 데이터 수신 ======");
        System.out.println("노드 ID: " + request.getNodeId());
        System.out.println("전압 값: " + request.getVoltage() + " V");
        System.out.println("상태값 (0:Normal, 1:Warn, 2:Critical): " + request.getState());

        // 비즈니스 로직 실행 및 DB 저장
        sensorService.processSensorData(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Data received and saved successfully");

        return ResponseEntity.ok(response);
    }
}
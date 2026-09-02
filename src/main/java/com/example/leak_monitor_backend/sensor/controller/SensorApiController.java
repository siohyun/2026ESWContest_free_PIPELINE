package com.example.leak_monitor_backend.sensor.controller;

import com.example.leak_monitor_backend.sensor.dto.SensorDataRequest;
import com.example.leak_monitor_backend.sensor.service.SensorService;
import com.example.leak_monitor_backend.sensor.service.SseEmitters;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sensors")
@RequiredArgsConstructor
public class SensorApiController {

    private final SensorService sensorService;
    private final SseEmitters sseEmitters; // ⚡ SSE 서비스 주입

    // 1. ESP32 데이터 수신 엔드포인트
    @PostMapping("/data")
    public ResponseEntity<Map<String, Object>> receiveSensorData(@RequestBody SensorDataRequest request) {
        System.out.println("====== ESP32 통합보드 데이터 수신 ======");
        System.out.println("노드 ID: " + request.getNodeId());
        System.out.println("전압 값: " + request.getVoltage() + " V");
        System.out.println("상태값 (0:Normal, 1:Warn, 2:Critical): " + request.getState());

        // 비즈니스 로직 실행 및 DB 저장
        sensorService.processSensorData(request);

        // ⚡ 수신된 ESP32 데이터를 구독 중인 안드로이드 앱들에 실시간 전송
        sseEmitters.broadcast(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Data received and saved successfully");

        return ResponseEntity.ok(response);
    }

    // 2. 안드로이드 앱 SSE 구독 엔드포인트
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSensorData() {
        return sseEmitters.add();
    }
}
package com.example.leak_monitor_backend.sensor.controller;

import com.example.leak_monitor_backend.sensor.dto.SensorActionRequest;
import com.example.leak_monitor_backend.sensor.dto.SensorActionResponse;
import com.example.leak_monitor_backend.sensor.dto.SensorResponse;
import com.example.leak_monitor_backend.sensor.dto.SensorStatusReportRequest;
import com.example.leak_monitor_backend.sensor.service.SensorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sensors")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    /**
     * Android ApiService.completeSensorAction(sensorId, request) 대응.
     */
    @PostMapping("/{sensorId}/action")
    public SensorActionResponse completeAction(
        @PathVariable String sensorId,
        @Valid @RequestBody SensorActionRequest request
    ) {
        return sensorService.completeAction(sensorId, request);
    }

    /**
     * STM32 보드(게이트웨이)가 자체 판정한 상태(NORMAL/WARNING/CRITICAL)를 보고하는 엔드포인트.
     * Android 앱이 직접 호출하지는 않으며, 센서 상태 변화 -> FCM 알림의 트리거입니다.
     */
    @PostMapping("/{sensorId}/status")
    public SensorResponse reportStatus(
        @PathVariable String sensorId,
        @Valid @RequestBody SensorStatusReportRequest request
    ) {
        return sensorService.reportStatus(sensorId, request);
    }
}

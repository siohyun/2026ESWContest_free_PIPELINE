package com.example.leak_monitor_backend.sensor.service;

import com.example.leak_monitor_backend.sensor.dto.SensorDataRequest;
import com.example.leak_monitor_backend.sensor.entity.SensorActionLog;
import com.example.leak_monitor_backend.sensor.repository.SensorActionLogRepository;
import com.example.leak_monitor_backend.sensor.dto.SensorActionRequest;
import com.example.leak_monitor_backend.sensor.dto.SensorResponse;
import com.example.leak_monitor_backend.sensor.dto.SensorStatusReportRequest;
import com.example.leak_monitor_backend.sensor.entity.Sensor;
import com.example.leak_monitor_backend.sensor.entity.SensorStatus;
import com.example.leak_monitor_backend.sensor.mapper.SensorMapper;
import com.example.leak_monitor_backend.sensor.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorRepository sensorRepository;
    private final SensorActionLogRepository sensorActionLogRepository;
    private final SensorMapper sensorMapper;

    // 1. ESP32 하드웨어 데이터 수신 처리 (통합 ESP32 HTTP POST 수신용)
    @Transactional
    public void processSensorData(SensorDataRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String targetSensorId = "NODE_" + request.getNodeId();

        // ESP32 state 매핑 (0: NORMAL, 1: WARM, 2: CRITICAL)
        SensorStatus status = SensorStatus.NORMAL;
        if (request.getState() != null) {
            if (request.getState() == 1) status = SensorStatus.WARM;
            else if (request.getState() == 2) status = SensorStatus.CRITICAL;
        }

        boolean isLeak = (status == SensorStatus.CRITICAL);

        Sensor sensor = sensorRepository.findById(targetSensorId)
                .orElseGet(() -> {
                    Sensor s = new Sensor();
                    s.setId(targetSensorId);
                    s.setNodeId(request.getNodeId());
                    s.setName("센서 노드 " + request.getNodeId());
                    return s;
                });

        sensor.setVoltage(request.getVoltage());
        sensor.setStatus(status);
        sensor.setIsLeak(isLeak);
        sensor.setUpdatedAt(now);

        if (status != SensorStatus.NORMAL) {
            sensor.setLastAnomalyAt(now);
        }

        sensorRepository.save(sensor);

        // 액션 로그 기록
        SensorActionLog log = new SensorActionLog(targetSensorId, request.getVoltage(), isLeak, now);
        sensorActionLogRepository.save(log);
    }

    // 2. 전체 센서 목록 조회 (getAllSensors 에러 해결)
    @Transactional(readOnly = true)
    public List<SensorResponse> getAllSensors() {
        return sensorRepository.findAll().stream()
                .map(sensorMapper::toResponse)
                .collect(Collectors.toList());
    }

    // 3. 센서 상태 보고 및 수동 등록 (registerOrUpdateSensor 및 getSensorId 에러 해결)
    @Transactional
    public SensorResponse registerOrUpdateSensor(SensorStatusReportRequest request) {
        String targetId = request.getSensorId(); // DTO의 getSensorId() 사용

        Sensor sensor = sensorRepository.findById(targetId)
                .orElseGet(() -> {
                    Sensor s = new Sensor();
                    s.setId(targetId);
                    return s;
                });

        if (request.getName() != null) sensor.setName(request.getName());
        if (request.getXPercent() != null) sensor.setXPercent(request.getXPercent());
        if (request.getYPercent() != null) sensor.setYPercent(request.getYPercent());
        if (request.getStatus() != null) sensor.setStatus(request.getStatus());

        sensor.setUpdatedAt(LocalDateTime.now());
        Sensor saved = sensorRepository.save(sensor);

        return sensorMapper.toResponse(saved);
    }

    // 4. 센서 제어 및 액션 처리 (processAction 에러 해결)
    @Transactional
    public SensorResponse processAction(SensorActionRequest request) {
        String targetId = request.getSensorId(); // DTO의 getSensorId() 사용

        Sensor sensor = sensorRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("해당 센서를 찾을 수 없습니다: " + targetId));

        LocalDateTime now = LocalDateTime.now();
        sensor.setUpdatedAt(now);
        Sensor saved = sensorRepository.save(sensor);

        // 액션 로그 기록
        SensorActionLog log = new SensorActionLog(targetId, sensor.getVoltage(), sensor.getIsLeak(), now);
        sensorActionLogRepository.save(log);

        return sensorMapper.toResponse(saved);
    }
}
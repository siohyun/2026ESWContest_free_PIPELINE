package com.example.leak_monitor_backend.sensor.service;

import com.example.leak_monitor_backend.common.exception.ResourceNotFoundException;
import com.example.leak_monitor_backend.map.dto.MapDetailResponse;
import com.example.leak_monitor_backend.map.entity.LeakMap;
import com.example.leak_monitor_backend.map.repository.LeakMapRepository;
import com.example.leak_monitor_backend.push.FcmService;
import com.example.leak_monitor_backend.sensor.dto.SensorActionRequest;
import com.example.leak_monitor_backend.sensor.dto.SensorActionResponse;
import com.example.leak_monitor_backend.sensor.dto.SensorResponse;
import com.example.leak_monitor_backend.sensor.dto.SensorStatusReportRequest;
import com.example.leak_monitor_backend.sensor.entity.Sensor;
import com.example.leak_monitor_backend.sensor.entity.SensorActionLog;
import com.example.leak_monitor_backend.sensor.entity.SensorStatus;
import com.example.leak_monitor_backend.sensor.mapper.SensorMapper;
import com.example.leak_monitor_backend.sensor.repository.SensorActionLogRepository;
import com.example.leak_monitor_backend.sensor.repository.SensorRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SensorService {

    private final LeakMapRepository leakMapRepository;
    private final SensorRepository sensorRepository;
    private final SensorActionLogRepository sensorActionLogRepository;
    private final FcmService fcmService;

    @Transactional(readOnly = true)
    public MapDetailResponse getMapDetail(String mapId) {
        LeakMap map = leakMapRepository.findById(mapId)
            .orElseThrow(() -> new ResourceNotFoundException("지도를 찾을 수 없습니다: " + mapId));
        return SensorMapper.toResponse(map);
    }

    @Transactional
    public SensorActionResponse completeAction(String sensorId, SensorActionRequest request) {
        Sensor sensor = sensorRepository.findById(sensorId)
            .orElseThrow(() -> new ResourceNotFoundException("센서를 찾을 수 없습니다: " + sensorId));

        sensorActionLogRepository.save(SensorActionLog.builder()
            .sensorId(sensorId)
            .note(request.note())
            .clientActionTakenAt(request.actionTakenAt())
            .loggedAt(Instant.now())
            .build());

        sensor.setStatus(SensorStatus.NORMAL);
        sensor.setUpdatedAt(Instant.now());
        sensorRepository.save(sensor);

        return new SensorActionResponse(sensor.getId(), sensor.getStatus().name(), "조치가 완료되었습니다.");
    }

    /**
     * STM32 보드가 자체 판정한 상태를 보고하는 엔드포인트. 임계값 계산은 보드에서
     * 이미 끝났으므로 서버는 전달받은 status를 그대로 신뢰합니다.
     * 상태가 실제로 바뀌었고 새 상태가 WARNING/CRITICAL일 때만 FCM 알림을 보냅니다
     * (같은 상태를 반복 보고해도 알림이 중복 발송되지 않도록).
     */
    @Transactional
    public SensorResponse reportStatus(String sensorId, SensorStatusReportRequest request) {
        Sensor sensor = sensorRepository.findById(sensorId)
            .orElseThrow(() -> new ResourceNotFoundException("센서를 찾을 수 없습니다: " + sensorId));

        SensorStatus previousStatus = sensor.getStatus();
        SensorStatus newStatus = SensorStatus.fromWireValue(request.status());

        if (request.value() != null) {
            sensor.setLastValue(request.value());
        }
        if (request.unit() != null) {
            sensor.setUnit(request.unit());
        }
        sensor.setStatus(newStatus);
        sensor.setUpdatedAt(Instant.now());
        sensorRepository.save(sensor);

        boolean becameAlerting = newStatus != previousStatus
            && (newStatus == SensorStatus.WARNING || newStatus == SensorStatus.CRITICAL);
        if (becameAlerting) {
            fcmService.sendLeakAlert(sensor);
        }

        return SensorMapper.toResponse(sensor);
    }
}

package com.example.leak_monitor_backend.sensor.mapper;

import com.example.leak_monitor_backend.map.dto.MapDetailResponse;
import com.example.leak_monitor_backend.map.entity.LeakMap;
import com.example.leak_monitor_backend.sensor.dto.SensorResponse;
import com.example.leak_monitor_backend.sensor.entity.Sensor;
import java.util.ArrayList;

public final class SensorMapper {

    private SensorMapper() {
    }

    public static SensorResponse toResponse(Sensor sensor) {
        return new SensorResponse(
            sensor.getId(),
            sensor.getName(),
            sensor.getRelativeX(),
            sensor.getRelativeY(),
            sensor.getStatus().name(),
            sensor.getLastValue(),
            sensor.getUnit(),
            sensor.getUpdatedAt() != null ? sensor.getUpdatedAt().toString() : null,
            sensor.getDescription(),
            sensor.getValveImageUrl(),
            // 트랜잭션(Hibernate 세션) 안에서 즉시 복사해 초기화해야 합니다.
            // 지연 컬렉션 참조를 그대로 넘기면 Jackson 직렬화 시점(세션 종료 후)에
            // LazyInitializationException이 발생합니다.
            new ArrayList<>(sensor.getEmergencyInstructions())
        );
    }

    public static MapDetailResponse toResponse(LeakMap map) {
        return new MapDetailResponse(
            map.getId(),
            map.getName(),
            map.getImageUrl(),
            map.getSensors().stream().map(SensorMapper::toResponse).toList()
        );
    }
}

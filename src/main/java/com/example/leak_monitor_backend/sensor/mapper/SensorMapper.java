package com.example.leak_monitor_backend.sensor.mapper;

import com.example.leak_monitor_backend.sensor.dto.SensorResponse;
import com.example.leak_monitor_backend.sensor.entity.Sensor;
import org.springframework.stereotype.Component;

@Component
public class SensorMapper {
    public SensorResponse toResponse(Sensor sensor) {
        return new SensorResponse(
                sensor.getId(),
                sensor.getName(),
                sensor.getXPercent(),
                sensor.getYPercent(),
                sensor.getStatus(),
                sensor.getLastReceivedTime()
        );
    }
}
package com.example.leak_monitor_backend.sensor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "SENSOR_ACTION_LOGS")
@Getter
@Setter
@NoArgsConstructor
public class SensorActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sensorCode;
    private Double rawValue;
    private Boolean isLeak;
    private LocalDateTime loggedAt;

    // SensorService.java:48 에러 해결을 위한 생성자 추가
    public SensorActionLog(String sensorCode, Double rawValue, boolean isLeak, LocalDateTime loggedAt) {
        this.sensorCode = sensorCode;
        this.rawValue = rawValue;
        this.isLeak = isLeak;
        this.loggedAt = loggedAt;
    }
}
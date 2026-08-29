package com.example.leak_monitor_backend.sensor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "SENSORS")
@Getter
@Setter
@NoArgsConstructor
public class Sensor {

    @Id
    @Column(name = "ID")
    private String id; // 예: "NODE_1" 또는 "SENSOR_01"

    @Column(name = "NODE_ID", unique = true)
    private Integer nodeId; // ESP32 노드 ID (1, 2 등)

    private String name;
    private Float xPercent;
    private Float yPercent;

    private Double voltage; // ESP32 측정 전압

    @Enumerated(EnumType.STRING)
    private SensorStatus status; // NORMAL, WARM, CRITICAL

    private String location;
    private Boolean isLeak;

    private LocalDateTime updatedAt;
    private LocalDateTime lastAnomalyAt;

    // 1. DataInitializer.java 해결용 커스텀 생성자 (String, String, float, float, SensorStatus, String)
    public Sensor(String id, String name, float xPercent, float yPercent, SensorStatus status, String location) {
        this.id = id;
        this.name = name;
        this.xPercent = xPercent;
        this.yPercent = yPercent;
        this.status = status;
        this.location = location;
        this.isLeak = (status == SensorStatus.CRITICAL || status == SensorStatus.WARM);
        this.updatedAt = LocalDateTime.now();
    }

    // 2. SensorMapper.java 해결용 (String 반환)
    public String getLastReceivedTime() {
        if (this.updatedAt == null) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return this.updatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
package com.example.leak_monitor_backend.sensor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 긴급 조치 매뉴얼 완료 이력(감사 기록용). Android의
 * POST /sensors/{sensorId}/action 요청에 담겨오는 note를 영구 보관합니다.
 */
@Entity
@Table(name = "sensor_action_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sensorId;

    @Column(nullable = false, length = 1000)
    private String note;

    private String clientActionTakenAt;

    @Column(nullable = false)
    private Instant loggedAt;
}

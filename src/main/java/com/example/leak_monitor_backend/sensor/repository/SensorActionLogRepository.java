package com.example.leak_monitor_backend.sensor.repository;

import com.example.leak_monitor_backend.sensor.entity.SensorActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorActionLogRepository extends JpaRepository<SensorActionLog, Long> {
}
package com.example.leak_monitor_backend.sensor.repository;

import com.example.leak_monitor_backend.sensor.entity.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SensorRepository extends JpaRepository<Sensor, String> {
    Optional<Sensor> findByNodeId(Integer nodeId);
}
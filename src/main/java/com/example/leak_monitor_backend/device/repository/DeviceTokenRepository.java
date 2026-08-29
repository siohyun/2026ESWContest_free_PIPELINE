package com.example.leak_monitor_backend.device.repository;

import com.example.leak_monitor_backend.device.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);
}
package com.example.leak_monitor_backend.device.repository;

import com.example.leak_monitor_backend.device.entity.DeviceToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);

    void deleteByToken(String token);
}

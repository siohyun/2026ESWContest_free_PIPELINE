package com.example.leak_monitor_backend.device.service;

import com.example.leak_monitor_backend.device.entity.DeviceToken;
import com.example.leak_monitor_backend.device.repository.DeviceTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Transactional
    public void registerToken(String token) {
        deviceTokenRepository.findByToken(token).ifPresentOrElse(
            existing -> existing.setRegisteredAt(Instant.now()),
            () -> deviceTokenRepository.save(DeviceToken.builder()
                .token(token)
                .registeredAt(Instant.now())
                .build())
        );
    }
}

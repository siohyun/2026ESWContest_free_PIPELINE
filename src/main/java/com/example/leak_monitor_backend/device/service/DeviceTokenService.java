package com.example.leak_monitor_backend.device.service;

import com.example.leak_monitor_backend.device.entity.DeviceToken;
import com.example.leak_monitor_backend.device.repository.DeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceTokenService {
    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Transactional
    public void saveOrUpdateToken(String token) {
        if (deviceTokenRepository.findByToken(token).isEmpty()) {
            deviceTokenRepository.save(new DeviceToken(token));
        }
    }
}
package com.example.leak_monitor_backend.device.controller;

import com.example.leak_monitor_backend.device.dto.DeviceTokenRequest;
import com.example.leak_monitor_backend.device.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Android MyFirebaseMessagingService.onNewToken()에서 호출해야 하는 토큰 등록 API.
 */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(@Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.registerToken(request.token());
        return ResponseEntity.ok().build();
    }
}

package com.example.leak_monitor_backend.device.controller;

import com.example.leak_monitor_backend.device.dto.DeviceTokenRequest;
import com.example.leak_monitor_backend.device.service.DeviceTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceTokenService deviceTokenService;

    public DeviceController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(@RequestBody DeviceTokenRequest request) {
        deviceTokenService.saveOrUpdateToken(request.getToken());
        return ResponseEntity.ok().build();
    }
}
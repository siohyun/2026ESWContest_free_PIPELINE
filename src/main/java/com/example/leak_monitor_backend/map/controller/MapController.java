package com.example.leak_monitor_backend.map.controller;

import com.example.leak_monitor_backend.map.dto.MapDetailResponse;
import com.example.leak_monitor_backend.sensor.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Android ApiService.getMapDetail(mapId) 대응.
 */
@RestController
@RequestMapping("/maps")
@RequiredArgsConstructor
public class MapController {

    private final SensorService sensorService;

    @GetMapping("/{mapId}")
    public MapDetailResponse getMap(@PathVariable String mapId) {
        return sensorService.getMapDetail(mapId);
    }
}

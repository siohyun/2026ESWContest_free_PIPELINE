package com.example.leak_monitor_backend.map.controller;

import com.example.leak_monitor_backend.map.dto.MapDetailResponse;
import com.example.leak_monitor_backend.map.entity.LeakMap;
import com.example.leak_monitor_backend.map.repository.LeakMapRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maps")
public class MapController {
    private final LeakMapRepository mapRepository;

    public MapController(LeakMapRepository mapRepository) {
        this.mapRepository = mapRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<MapDetailResponse> getMapDetail(@PathVariable Long id) {
        LeakMap map = mapRepository.findById(id)
                .orElse(new LeakMap("1층 배수도 메인 관제", "https://via.placeholder.com/600"));
        return ResponseEntity.ok(new MapDetailResponse(map.getId(), map.getTitle(), map.getImageUrl()));
    }
}
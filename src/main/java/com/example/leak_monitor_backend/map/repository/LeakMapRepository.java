package com.example.leak_monitor_backend.map.repository;

import com.example.leak_monitor_backend.map.entity.LeakMap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeakMapRepository extends JpaRepository<LeakMap, Long> {
}
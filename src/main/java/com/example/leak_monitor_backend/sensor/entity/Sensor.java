package com.example.leak_monitor_backend.sensor.entity;

import com.example.leak_monitor_backend.map.entity.LeakMap;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sensors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sensor {

    @Id
    private String id;

    private String name;

    private float relativeX;

    private float relativeY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SensorStatus status;

    private Double lastValue;

    private String unit;

    private Instant updatedAt;

    @Column(length = 1000)
    private String description;

    private String valveImageUrl;

    @ElementCollection
    @CollectionTable(name = "sensor_emergency_instructions", joinColumns = @JoinColumn(name = "sensor_id"))
    @OrderColumn(name = "step_order")
    @Column(name = "instruction", length = 500)
    @Builder.Default
    private List<String> emergencyInstructions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id")
    private LeakMap leakMap;
}

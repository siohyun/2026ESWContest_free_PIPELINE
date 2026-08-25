package com.example.leak_monitor_backend.map.entity;

import com.example.leak_monitor_backend.sensor.entity.Sensor;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leak_maps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeakMap {

    @Id
    private String id;

    private String name;

    private String imageUrl;

    @OneToMany(mappedBy = "leakMap", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Sensor> sensors = new ArrayList<>();
}

package com.example.leak_monitor_backend.map.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "maps")
public class LeakMap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String imageUrl;

    public LeakMap() {}

    public LeakMap(String title, String imageUrl) {
        this.title = title;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getImageUrl() { return imageUrl; }
}
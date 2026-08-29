package com.example.leak_monitor_backend.map.dto;

public class MapDetailResponse {
    private Long id;
    private String title;
    private String imageUrl;

    public MapDetailResponse(Long id, String title, String imageUrl) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getImageUrl() { return imageUrl; }
}
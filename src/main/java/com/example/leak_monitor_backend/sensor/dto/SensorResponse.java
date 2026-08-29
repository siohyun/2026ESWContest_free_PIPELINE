package com.example.leak_monitor_backend.sensor.dto;

import com.example.leak_monitor_backend.sensor.entity.SensorStatus;

public class SensorResponse {
    private String id;
    private String name;
    private Float xPercent;
    private Float yPercent;
    private SensorStatus status;
    private String lastReceivedTime;

    public SensorResponse(String id, String name, Float xPercent, Float yPercent, SensorStatus status, String lastReceivedTime) {
        this.id = id;
        this.name = name;
        this.xPercent = xPercent;
        this.yPercent = yPercent;
        this.status = status;
        this.lastReceivedTime = lastReceivedTime;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Float getXPercent() { return xPercent; }
    public Float getYPercent() { return yPercent; }
    public SensorStatus getStatus() { return status; }
    public String getLastReceivedTime() { return lastReceivedTime; }
}
package com.example.leak_monitor_backend.sensor.dto;

import com.example.leak_monitor_backend.sensor.entity.SensorStatus;

public class SensorStatusReportRequest {
    private String sensorId;
    private String name;
    private Float xPercent;
    private Float yPercent;
    private SensorStatus status;

    public String getSensorId() { return sensorId; }
    public String getName() { return name; }
    public Float getXPercent() { return xPercent; }
    public Float getYPercent() { return yPercent; }
    public SensorStatus getStatus() { return status; }
}
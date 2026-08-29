package com.example.leak_monitor_backend.sensor.dto;

public class SensorActionResponse {
    private boolean success;
    private String message;

    public SensorActionResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
package com.example.leak_monitor_backend.sensor.dto;

public class SensorActionRequest {
    private String sensorId;
    private String actionDetails;

    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }
    public String getActionDetails() { return actionDetails; }
    public void setActionDetails(String actionDetails) { this.actionDetails = actionDetails; }
}
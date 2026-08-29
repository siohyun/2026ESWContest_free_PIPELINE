package com.example.leak_monitor_backend.device.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "device_tokens")
public class DeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    public DeviceToken() {}

    public DeviceToken(String token) {
        this.token = token;
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
package com.example.leak_monitor_backend.sensor.entity;

public enum SensorStatus {
    NORMAL,
    WARNING,
    CRITICAL;

    /**
     * STM32 보드가 UART/BLE 등으로 보내는 상태 문자열을 파싱합니다.
     * 보드 펌웨어가 축약형(WARN, CRIT 등)을 보낼 수 있어 관대하게 매칭합니다.
     *
     * @throws IllegalArgumentException 알 수 없는 상태 값일 때
     */
    public static SensorStatus fromWireValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("센서 상태 값이 비어 있습니다.");
        }
        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "NORMAL", "NORM", "OK" -> NORMAL;
            case "WARNING", "WARN" -> WARNING;
            case "CRITICAL", "CRIT", "DANGER" -> CRITICAL;
            default -> throw new IllegalArgumentException("알 수 없는 센서 상태 값입니다: " + raw);
        };
    }
}

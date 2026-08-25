package com.example.leak_monitor_backend.sensor.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * STM32 보드가 자체 판정한 상태(NORMAL/WARNING/CRITICAL)를 보고할 때 사용하는 요청.
 * 임계값 판정은 보드 펌웨어에서 이미 끝난 상태이므로, 서버는 값을 재계산하지 않고
 * 전달받은 status를 그대로 신뢰해서 저장합니다.
 * value/unit은 UI에 표시할 참고용 원시 측정값으로, 없어도 됩니다.
 */
public record SensorStatusReportRequest(
    @NotBlank(message = "센서 상태 값은 필수입니다.") String status,
    Double value,
    String unit
) {
}

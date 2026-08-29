package com.example.leak_monitor_backend.data;

import com.example.leak_monitor_backend.map.entity.LeakMap;
import com.example.leak_monitor_backend.map.repository.LeakMapRepository;
import com.example.leak_monitor_backend.sensor.entity.Sensor;
import com.example.leak_monitor_backend.sensor.entity.SensorStatus;
import com.example.leak_monitor_backend.sensor.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 로컬 데모/테스트용 초기 데이터.
 * 앱을 처음 실행했을 때 기본 도면과 샘플 센서가 자동으로 로드되도록 합니다.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final LeakMapRepository leakMapRepository;
    private final SensorRepository sensorRepository;

    @Override
    public void run(String... args) {
        // 이미 기본 지도가 존재하면 추가 생성하지 않음
        if (leakMapRepository.existsById(1L)) {
            return;
        }

        // 1. 기본 도면 생성 및 저장
        LeakMap defaultMap = new LeakMap("1층 배수도 메인 관제", "https://picsum.photos/id/1043/1200/800");
        leakMapRepository.save(defaultMap);

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // 2. 샘플 센서 데이터 생성 (정상 상태 센서)
        Sensor normalSensor = new Sensor(
                "s-1",
                "주방 배관 센서",
                0.3f,
                0.4f,
                SensorStatus.NORMAL,
                now
        );

        // 3. 샘플 센서 데이터 생성 (위험 상태 센서)
        Sensor criticalSensor = new Sensor(
                "s-2",
                "보일러실 센서",
                0.65f,
                0.7f,
                SensorStatus.CRITICAL,
                now
        );

        sensorRepository.save(normalSensor);
        sensorRepository.save(criticalSensor);
    }
}
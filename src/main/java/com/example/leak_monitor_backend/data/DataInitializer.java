package com.example.leak_monitor_backend.data;

import com.example.leak_monitor_backend.map.entity.LeakMap;
import com.example.leak_monitor_backend.map.repository.LeakMapRepository;
import com.example.leak_monitor_backend.sensor.entity.Sensor;
import com.example.leak_monitor_backend.sensor.entity.SensorStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 로컬 데모/테스트용 초기 데이터. Android MainActivity.DEFAULT_MAP_ID("default")와
 * 일치하는 mapId로 시드하여, 앱을 처음 켜자마자 지도가 바로 보이도록 합니다.
 * H2가 create-drop이라 서버를 재시작할 때마다 다시 생성됩니다.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final LeakMapRepository leakMapRepository;

    @Override
    public void run(String... args) {
        if (leakMapRepository.existsById("default")) {
            return;
        }

        LeakMap map = LeakMap.builder()
            .id("default")
            .name("1층 배관도")
            .imageUrl("https://picsum.photos/id/1043/1200/800")
            .sensors(new ArrayList<>())
            .build();

        Sensor normalSensor = Sensor.builder()
            .id("s-1")
            .name("주방 배관 센서")
            .relativeX(0.3f)
            .relativeY(0.4f)
            .status(SensorStatus.NORMAL)
            .lastValue(12.0)
            .unit("%")
            .updatedAt(Instant.now())
            .description("주방 하부 배관에 설치된 압전 센서입니다.")
            .leakMap(map)
            .build();

        Sensor criticalSensor = Sensor.builder()
            .id("s-2")
            .name("보일러실 센서")
            .relativeX(0.65f)
            .relativeY(0.7f)
            .status(SensorStatus.CRITICAL)
            .lastValue(92.5)
            .unit("%")
            .updatedAt(Instant.now())
            .description("보일러실 메인 배관 인근 센서입니다.")
            .valveImageUrl("https://picsum.photos/id/1052/800/600")
            .emergencyInstructions(List.of(
                "메인 밸브를 시계 방향으로 잠급니다.",
                "보일러 전원을 차단합니다.",
                "관리사무소에 연락합니다."
            ))
            .leakMap(map)
            .build();

        map.getSensors().add(normalSensor);
        map.getSensors().add(criticalSensor);

        leakMapRepository.save(map);
    }
}

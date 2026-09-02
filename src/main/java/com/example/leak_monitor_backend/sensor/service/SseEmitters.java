package com.example.leak_monitor_backend.sensor.service;

import com.example.leak_monitor_backend.sensor.dto.SensorDataRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseEmitters {

    // 동구현 이슈 방지를 위한 쓰레드 세이프 리스트
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter add() {
        SseEmitter emitter = new SseEmitter(60 * 1000L * 60); // 1시간 타임아웃
        this.emitters.add(emitter);

        log.info("새로운 SSE 클라이언트 연결됨. 현재 연결 수: {}", emitters.size());

        // 타임아웃 및 완료 시 리스트에서 제거
        emitter.onCompletion(() -> {
            log.info("SSE 연결 종료");
            this.emitters.remove(emitter);
        });
        emitter.onTimeout(() -> {
            log.info("SSE 연결 타임아웃");
            emitter.complete();
            this.emitters.remove(emitter);
        });

        // 최초 연결 시 503 에러 방지용 dummy 이벤트를 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected!"));
        } catch (IOException e) {
            log.error("SSE 최초 연결 메시지 전송 실패", e);
        }

        return emitter;
    }

    // 연결된 모든 안드로이드 클라이언트에게 센서 데이터 실시간 브로드캐스팅
    public void broadcast(SensorDataRequest data) {
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("sensor-data")
                        .data(data));
            } catch (IOException e) {
                log.error("SSE 데이터 전송 실패", e);
                emitters.remove(emitter);
            }
        });
    }
}
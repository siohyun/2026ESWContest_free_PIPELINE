package com.example.leak_monitor_backend.push;

import com.example.leak_monitor_backend.device.entity.DeviceToken;
import com.example.leak_monitor_backend.device.repository.DeviceTokenRepository;
import com.example.leak_monitor_backend.sensor.entity.Sensor;
import com.example.leak_monitor_backend.sensor.entity.SensorStatus;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Android MyFirebaseMessagingService가 기대하는 data-only 페이로드(mapId, sensorId,
 * status, title, body)로 발송합니다. notification 키를 절대 채우지 않아야
 * 백그라운드 상태에서도 앱의 커스텀 알림 로직(채널/진동/딥링크)이 항상 동작합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    private static final int MAX_TOKENS_PER_MULTICAST = 500;

    private final DeviceTokenRepository deviceTokenRepository;

    public void sendLeakAlert(Sensor sensor) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase가 초기화되지 않아 센서 {} 알림을 보낼 수 없습니다.", sensor.getId());
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findAll();
        if (tokens.isEmpty()) {
            log.info("등록된 디바이스 토큰이 없어 알림을 보내지 않습니다.");
            return;
        }

        Map<String, String> data = buildData(sensor);
        List<String> tokenValues = tokens.stream().map(DeviceToken::getToken).toList();

        for (List<String> batch : partition(tokenValues, MAX_TOKENS_PER_MULTICAST)) {
            sendBatch(data, batch);
        }
    }

    private Map<String, String> buildData(Sensor sensor) {
        boolean critical = sensor.getStatus() == SensorStatus.CRITICAL;
        String mapId = sensor.getLeakMap() != null ? sensor.getLeakMap().getId() : "";

        Map<String, String> data = new HashMap<>();
        data.put("mapId", mapId);
        data.put("sensorId", sensor.getId());
        data.put("status", sensor.getStatus().name());
        data.put("title", critical ? "긴급 누수 경보" : "누수 주의 알림");
        data.put("body", sensor.getName() + "에서 " + (critical ? "위험" : "주의") + " 수준이 감지되었습니다.");
        return data;
    }

    private void sendBatch(Map<String, String> data, List<String> tokens) {
        MulticastMessage message = MulticastMessage.builder()
            .putAllData(data)
            .addAllTokens(tokens)
            .build();
        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            removeInvalidTokens(tokens, response);
        } catch (FirebaseMessagingException e) {
            log.error("FCM 전송 실패", e);
        }
    }

    private void removeInvalidTokens(List<String> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException exception = sendResponse.getException();
            MessagingErrorCode code = exception != null ? exception.getMessagingErrorCode() : null;
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                deviceTokenRepository.deleteByToken(tokens.get(i));
                log.info("만료/유효하지 않은 토큰을 삭제했습니다.");
            }
        }
    }

    private static List<List<String>> partition(List<String> list, int size) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}

package com.example.leak_monitor_backend.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FcmService {

    // 등록된 모든 기기(또는 특정 토큰)로 CRITICAL 긴급 알림 전송
    public void sendEmergencyAlertToTokens(List<String> tokens, String sensorId, String sensorName) {
        if (tokens == null || tokens.isEmpty()) {
            System.out.println("⚠️ 등록된 FCM 토큰이 없어 알림을 전송하지 못했습니다.");
            return;
        }

        for (String token : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle("🚨 긴급 누수 경보 발생!")
                                .setBody("[" + sensorName + "] 구역의 센서 수치가 위험(CRITICAL) 상태입니다.")
                                .build())
                        .putData("sensorId", sensorId)
                        .putData("status", "CRITICAL")
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                System.out.println("FCM 푸시 전송 성공 (Token: " + token + ") -> " + response);
            } catch (Exception e) {
                System.err.println("FCM 푸시 전송 실패: " + e.getMessage());
            }
        }
    }
}
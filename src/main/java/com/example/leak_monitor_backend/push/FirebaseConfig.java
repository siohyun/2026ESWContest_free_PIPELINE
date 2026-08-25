package com.example.leak_monitor_backend.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * firebase.credentials-path가 설정되어 있고 실제로 파일이 존재할 때만 FirebaseApp을 초기화합니다.
 * 자격증명이 없는 로컬 개발 환경에서도 앱 전체가 죽지 않고, FCM 발송 기능만 비활성화됩니다.
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("firebase.credentials-path가 설정되지 않아 FCM 푸시가 비활성화됩니다.");
            return null;
        }

        File file = new File(credentialsPath);
        if (!file.exists()) {
            log.warn("Firebase 서비스 계정 파일을 찾을 수 없습니다: {}. FCM 푸시가 비활성화됩니다.", credentialsPath);
            return null;
        }

        try (FileInputStream serviceAccount = new FileInputStream(file)) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();
            log.info("FirebaseApp이 초기화되었습니다. FCM 푸시가 활성화됩니다.");
            return FirebaseApp.initializeApp(options);
        }
    }
}

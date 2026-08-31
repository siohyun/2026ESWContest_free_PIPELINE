# 2026ESWContest_free_

# 🌊 피에조 필름 센서 기반 누수 감지 관제 시스템

임베디드 누수 탐지 센서, 백엔드 서버, 안드로이드 모바일 앱을 연동한 종합 누수 관제 솔루션입니다.

## 🏗️ 시스템 아키텍처
[STM32 / ESP32] --(MQTT/REST)--> [Spring Boot Backend] --(FCM / REST)--> [Android App]

## 📂 프로젝트 구조
- `/firmware-stm32`: 피에조 필름 센서 신호 수집 및 임계치 판정 (STM32)
- `/firmware-esp32`: Wi-Fi 통신 및 OLED/LED 상태 표시 (ESP32)
- `/backend`: REST API 및 FCM 알림 처리 백엔드 (Spring Boot)
- `/android`: 배수도 도면 기반 실시간 관제 및 푸시 알림 앱 (Android Compose)

## 🛠️ 기술 스택
- **Embedded**: STM32, ESP32, C/C++
- **Backend**: Java 17, Spring Boot, Spring Data JPA
- **Android**: Kotlin, Jetpack Compose, Retrofit2, Firebase FCM

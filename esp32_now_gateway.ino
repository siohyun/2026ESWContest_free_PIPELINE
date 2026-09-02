#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <queue>

// ── 1. 설정 ──────────────────────────────────────────────
const char* WIFI_SSID     = "KT_GiGA_249C";
const char* WIFI_PASSWORD = "3ad64kg819";
const char* BACKEND_URL = "https://evasive-untimed-alright.ngrok-free.dev/api/sensors/data";
#define WIFI_CHANNEL 11

// 송신측과 완전히 동일한 구조체
typedef struct struct_message {
    int node_id;
    float voltage;
    int state;
} struct_message;

// HTTP 백엔드 전송용 대기열 Queue & 뮤텍스
std::queue<struct_message> httpQueue;
SemaphoreHandle_t queueMutex;

HardwareSerial SerialSTM32(2);

// ── 2. Core 0에서 독립 실행되는 백엔드 HTTP 전송 전용 태스크 ──
void HttpTask(void * pvParameters) {
    while (1) {
        struct_message msg;
        bool hasData = false;

        // 큐에서 데이터 안전하게 꺼내기
        if (xSemaphoreTake(queueMutex, portMAX_DELAY)) {
            if (!httpQueue.empty()) {
                msg = httpQueue.front();
                httpQueue.pop();
                hasData = true;
            }
            xSemaphoreGive(queueMutex);
        }

        // 백엔드 전송 (서버가 느리거나 끊겨도 Core 1 수신/STM32 전송 속도에는 0ms도 영향 안 줌)
        if (hasData) {
            if (WiFi.status() == WL_CONNECTED) {
                HTTPClient http;
                http.setTimeout(500); // 500ms 타임아웃
                http.begin(BACKEND_URL);
                http.addHeader("Content-Type", "application/json");

                StaticJsonDocument<128> doc;
                doc["nodeId"]  = msg.node_id;
                doc["voltage"] = msg.voltage;
                doc["state"]   = msg.state;

                String jsonPayload;
                serializeJson(doc, jsonPayload);

                int code = http.POST(jsonPayload);
                http.end();
            }
        }

        vTaskDelay(10 / portTICK_PERIOD_MS);
    }
}

// ── 3. ESP-NOW 수신 콜백 (Core 1에서 수신 즉시 0.001초 만에 실행!) ─
#if defined(ESP_IDF_VERSION_MAJOR) && ESP_IDF_VERSION_MAJOR >= 5
void OnDataRecv(const esp_now_recv_info *info, const uint8_t *data, int len) {
#else
void OnDataRecv(const uint8_t *mac_addr, const uint8_t *data, int len) {
#endif
    if (len == sizeof(struct_message)) {
        struct_message msg;
        memcpy(&msg, data, sizeof(msg));

        // ⚡ [0.001초 실행] 수신된 그 즉시 STM32로 UART 출력!
        SerialSTM32.printf("%d,%.2f,%d\n", msg.node_id, msg.voltage, msg.state);

        // ⚡ [0.001초 실행] 게이트웨이 시리얼 모니터로 즉시 출력!
        Serial.printf("⚡ [수신즉시] Node:%d | 전압:%.2fV | 상태:%d\n", 
                      msg.node_id, msg.voltage, msg.state);

        // HTTP 전송은 Core 0 태스크 큐로 넘겨버림 (대기시간 0ms)
        if (xSemaphoreTake(queueMutex, 0)) {
            httpQueue.push(msg);
            xSemaphoreGive(queueMutex);
        }
    }
}

void setup() {
    Serial.begin(115200);
    delay(300);

    queueMutex = xSemaphoreCreateMutex();

    // STM32 UART2 (RX: 16, TX: 17)
    SerialSTM32.begin(115200, SERIAL_8N1, 16, 17);

    Serial.println("\n================================");
    Serial.println("ESP32 초고속 허브 리시버 시작");
    Serial.println("================================");

    WiFi.mode(WIFI_STA);
    esp_wifi_set_ps(WIFI_PS_NONE); // ⚡ 핵심: 허브 무선 수신 절전 완전 해제
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    while (WiFi.status() != WL_CONNECTED) {
        delay(100);
        Serial.print(".");
    }

    esp_wifi_set_channel(WIFI_CHANNEL, WIFI_SECOND_CHAN_NONE);

    Serial.println("\n✅ Wi-Fi 연결 완!");

    if (esp_now_init() == ESP_OK) {
        Serial.println("✅ ESP-NOW 초기화 성공");
    }

    esp_now_register_recv_cb(OnDataRecv);

    // ⚡ 백엔드 통신 전용 태스크를 Core 0에 할당 (Core 1은 ESP-NOW 전담)
    xTaskCreatePinnedToCore(HttpTask, "HttpTask", 4096, NULL, 1, NULL, 0);

    Serial.println("================================");
}

void loop() {
    vTaskDelay(100 / portTICK_PERIOD_MS);
}
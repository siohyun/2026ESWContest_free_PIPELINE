#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <queue>

// ── 1. 설정 ──────────────────────────────────────────────
const char* WIFI_SSID     = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";
const char* BACKEND_URL = "https://your-backend-url.com/api/sensors/data";
#define WIFI_CHANNEL 10

// 송신측 동일 구조체
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

        if (xSemaphoreTake(queueMutex, portMAX_DELAY)) {
            if (!httpQueue.empty()) {
                msg = httpQueue.front();
                httpQueue.pop();
                hasData = true;
            }
            xSemaphoreGive(queueMutex);
        }

        // 백엔드 전송
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

        SerialSTM32.printf("%d,%.2f,%d\n", msg.node_id, msg.voltage, msg.state);

        Serial.printf("[수신즉시] Node:%d | 전압:%.2fV | 상태:%d\n", 
                      msg.node_id, msg.voltage, msg.state);

        // HTTP 전송은 Core 0 태스크 큐로 넘겨버림
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
    esp_wifi_set_ps(WIFI_PS_NONE);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    while (WiFi.status() != WL_CONNECTED) {
        delay(100);
        Serial.print(".");
    }

    esp_wifi_set_channel(WIFI_CHANNEL, WIFI_SECOND_CHAN_NONE);

    Serial.println("\nWi-Fi 연결 완!");

    if (esp_now_init() == ESP_OK) {
        Serial.println("ESP-NOW 초기화 성공");
    }

    esp_now_register_recv_cb(OnDataRecv);

    xTaskCreatePinnedToCore(HttpTask, "HttpTask", 4096, NULL, 1, NULL, 0);

    Serial.println("================================");
}

void loop() {
    vTaskDelay(100 / portTICK_PERIOD_MS);
}
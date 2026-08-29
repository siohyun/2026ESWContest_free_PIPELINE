#include <WiFi.h>
#include <esp_now.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// 1. 센서 노드와 동일한 공유기 정보 (채널 8번 동기화용)
const char* WIFI_SSID = "SK_6FBA_2.4G";
const char* WIFI_PASSWORD = "CAD22@2632";

// 백엔드 Spring Boot 서버 주소
const char* BACKEND_URL = "http://192.168.45.30:8080/api/sensors/data";

// 2. 센서 노드와 100% 동일한 구조체 정의
typedef struct struct_message {
  int node_id;
  float voltage;
  int state;
} struct_message;

struct_message incomingData;

// STM32 통신용 HardwareSerial 객체 생성 (UART2)
HardwareSerial SerialSTM32(2);

// 백엔드로 HTTP POST 전송 함수
void sendToBackend(int nodeId, float voltage, int state) {
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    http.begin(BACKEND_URL);
    http.addHeader("Content-Type", "application/json");

    // JSON 데이터 생성
    StaticJsonDocument<200> doc;
    doc["nodeId"] = nodeId;
    doc["voltage"] = voltage;
    doc["state"] = state;

    String jsonPayload;
    serializeJson(doc, jsonPayload);

    // POST 전송
    int httpResponseCode = http.POST(jsonPayload);

    if (httpResponseCode > 0) {
      Serial.print("✅ 백엔드 전송 성공! 응답 코드: ");
      Serial.println(httpResponseCode);
    } else {
      Serial.print("❌ 백엔드 전송 실패! 에러 코드: ");
      Serial.println(httpResponseCode);
    }
    http.end();
  } else {
    Serial.println("⚠️ Wi-Fi 연결 끊김: 백엔드로 전송하지 못했습니다.");
  }
}

// ESP-NOW 수신 콜백 (v3.x 규격)
void OnDataRecv(const esp_now_recv_info *info, const uint8_t *data, int len) {
  // 수신된 이진 데이터를 구조체 크기만큼 메모리 복사
  if (len == sizeof(struct_message)) {
    memcpy(&incomingData, data, sizeof(incomingData));

    Serial.println("--------------------------------");
    Serial.println("🔔 ESP-NOW 수신 성공!");
    Serial.print("노드 ID: ");
    Serial.println(incomingData.node_id);
    Serial.print("피에조 전압: ");
    Serial.print(incomingData.voltage, 2);
    Serial.println(" V");
    Serial.print("상태 (0:Normal, 1:Warn, 2:Critical): ");
    Serial.println(incomingData.state);
    Serial.println("--------------------------------");

    // [수정 포인트 1] Serial2 -> SerialSTM32 객체명으로 통일
    SerialSTM32.print(incomingData.node_id);
    SerialSTM32.print(",");
    SerialSTM32.print(incomingData.voltage, 2);
    SerialSTM32.print(",");
    SerialSTM32.println(incomingData.state);

    // 백엔드로 수신 데이터 전송
    sendToBackend(incomingData.node_id, incomingData.voltage, incomingData.state);

    Serial.println("✅ STM32 UART 및 백엔드 전송 완료");
  } else {
    Serial.print("⚠️ 데이터 크기 불일치! (수신 크기: ");
    Serial.print(len);
    Serial.println(" bytes)");
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  // [UART2 초기화] RX: GPIO 16, TX: GPIO 17
  SerialSTM32.begin(115200, SERIAL_8N1, 16, 17);

  Serial.println("\n================================");
  Serial.println("ESP-NOW Gateway 수신 테스트");
  Serial.println("================================");

  // 1. Wi-Fi 공유기 접속 (센서 노드와 채널을 동기화하기 위함)
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.print("Wi-Fi 접속 중 (채널 동기화)");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\n✅ Wi-Fi 연결 성공!");
  Serial.print("📍 Gateway MAC: ");
  Serial.println(WiFi.macAddress());
  Serial.print("📡 Gateway 현재 채널: ");
  Serial.println(WiFi.channel()); // 센서 노드와 동일한 채널인지 확인

  // 2. ESP-NOW 초기화
  if (esp_now_init() == ESP_OK) {
    Serial.println("✅ ESP-NOW 초기화 성공");
  } else {
    Serial.println("❌ ESP-NOW 초기화 실패");
    return;
  }

  // 3. 수신 콜백 등록
  if (esp_now_register_recv_cb(OnDataRecv) == ESP_OK) {
    Serial.println("✅ 수신 콜백 등록 성공");
  } else {
    Serial.println("❌ 수신 콜백 등록 실패");
  }

  Serial.println("================================");
  Serial.println("수신 대기 중...");
}

void loop() {
  // [수정 포인트 2] 수신 동작은 이벤트 콜백(OnDataRecv)에서 처리하므로 loop는 대기 상태로 유지합니다.
  // 필요한 경우 주석을 해제하여 단독 백엔드 테스트를 진행할 수 있습니다.
  Serial.println("테스트 데이터 전송 시도...");
  sendToBackend(1, 2.55, 1); // nodeId: 1, voltage: 2.55V, state: 1 (WARM)
  delay(5000);
  //delay(10);
}
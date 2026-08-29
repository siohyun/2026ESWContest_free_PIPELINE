#include <WiFi.h>
#include <esp_now.h>

// ── 1. 네트워크 및 수신기 설정 ─────────────────────────────
const char* WIFI_SSID = "SK_6FBA_2.4G";
const char* WIFI_PASSWORD = "CAD22@2632";
const char* HARDWARE_ID = "S01";

// 허브 보드(Gateway) MAC 주소 (⚠️ 센서 노드 1, 2 공통 적용)
uint8_t receiverAddress[] = {0x0C, 0x7A, 0x15, 0xD8, 0x8F, 0x79}; 

#define NODE_ID 1    // ⚠️ 노드 2번 보드 업로드 시에는 2로 변경해 주세요!
#define PIEZO_PIN 33 // ADC1_CH0 (GPIO 36 / VP 핀)

// ── 2. 배관 진동 및 단계별 승격 매개변수 ─────────────────
typedef enum {
    STATE_NORMAL = 0,
    STATE_WARNING = 1,
    STATE_DANGER = 2
} SystemState_t;

#define BASELINE_SHIFT     5     // 기준선 적응 속도 (EMA)
#define WARN_THRESHOLD    35     // ★ 노이즈 방지를 위해 임계값을 35로 살짝 보정
#define CRITICAL_COUNT    3     // ★ Warn 연속 3회 시 Critical 승격
#define RF_NOISE_REJECTION_MS 40 // ★ RF 전송 후 노이즈 무시 시간 (40ms)

// ── 3. 비동기 전송 타이밍 제어 변수 ──────────────────────
const unsigned long NORMAL_INTERVAL = 5000; // Normal 상태 전송 주기: 5초
const unsigned long EVENT_INTERVAL = 300;   // Warn/Critical 재발송 주기: 300ms
unsigned long lastSendTime = 0;              
unsigned long nextSendOffset = 0;           
unsigned long lastRfTxTime = 0;             // 마지막 RF 전송 시각
SystemState_t last_sent_state = STATE_NORMAL;

// ── 4. 전역 상태 변수 ─────────────────────────────────────
uint32_t baseline = 0;
int abnormal_count = 0;         
SystemState_t current_state = STATE_NORMAL;

// ── 5. ESP-NOW 전송용 구조체 ──────────────────────────────
typedef struct struct_message {
    int node_id;
    float voltage;
    int state;
} struct_message;

struct_message myData;
esp_now_peer_info_t peerInfo;

#if defined(ESP_IDF_VERSION_MAJOR) && ESP_IDF_VERSION_MAJOR >= 5
void OnDataSent(const wifi_tx_info_t *info, esp_now_send_status_t status) {}
#else
void OnDataSent(const uint8_t *mac_addr, esp_now_send_status_t status) {}
#endif

// ── 6. 단계별 상태 전이 알고리즘 (노이즈 방어 적용) ───────
void ProcessSample(uint16_t raw) {
    // [1단계: EMA 필터 기준선 추종]
    if (baseline == 0) {
        baseline = raw;
    } else {
        baseline += ((int32_t)raw - (int32_t)baseline) >> BASELINE_SHIFT;
    }

    // [2단계: 진동 편차(Dev) 절대값 계산]
    int32_t dev = (int32_t)raw - (int32_t)baseline;
    uint32_t dev_abs = (dev < 0) ? (uint32_t)(-dev) : (uint32_t)dev;

    // [3단계: 단계별 상태 승격/복귀 로직]
    if (dev_abs >= WARN_THRESHOLD) {
        if (abnormal_count < CRITICAL_COUNT) abnormal_count++;
    } else {
        if (abnormal_count > 0) abnormal_count--;
    }

    // [4단계: 누적 카운트에 따른 최종 상태 결정]
    if (abnormal_count >= CRITICAL_COUNT) {
        current_state = STATE_DANGER;   // 2
    } else if (abnormal_count > 0) {
        current_state = STATE_WARNING;  // 1
    } else {
        current_state = STATE_NORMAL;   // 0
    }

    // [5단계: 시리얼 모니터/플로터 출력]
    uint32_t state_plot_val = (current_state == STATE_DANGER) ? 100 :
                              (current_state == STATE_WARNING) ? 50 : 0;

    const char* state_str = (current_state == STATE_DANGER) ? "CRITICAL" :
                            (current_state == STATE_WARNING) ? "WARN" : "NORMAL";

    Serial.print("Raw:"); Serial.print(raw);
    Serial.print(",Base:"); Serial.print(baseline);
    Serial.print(",Dev:"); Serial.print(dev_abs);
    Serial.print(",Cnt:"); Serial.print(abnormal_count);
    Serial.print(",State_Num:"); Serial.print(state_plot_val);
    Serial.print(",Status:"); Serial.println(state_str);
}

void setup() {
    Serial.begin(115200);

    Serial.print("현재 기기의 설정된 하드웨어 ID: ");
    Serial.println(HARDWARE_ID);

    analogReadResolution(12);
    analogSetPinAttenuation(PIEZO_PIN, ADC_11db);
    pinMode(PIEZO_PIN, INPUT);

    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    
    while (WiFi.status() != WL_CONNECTED) {
        delay(100);
    }

    if (esp_now_init() != ESP_OK) return;
    esp_now_register_send_cb(OnDataSent);

    memcpy(peerInfo.peer_addr, receiverAddress, 6);
    peerInfo.channel = WiFi.channel(); 
    peerInfo.encrypt = false;

    if (esp_now_add_peer(&peerInfo) != ESP_OK) return;
}

unsigned long lastSampleTime = 0;

void loop() {
    unsigned long currentMillis = millis();

    // [핵심 1] ESP-NOW 전송 후 40ms 동안은 RF 전원 노이즈 잔여 구간이므로 센서 샘플링을 안 함
    if (currentMillis - lastRfTxTime >= RF_NOISE_REJECTION_MS) {
        if (micros() - lastSampleTime >= 1000) { // 1ms 간격 샘플링
            lastSampleTime = micros();
            uint16_t raw_adc = analogRead(PIEZO_PIN);
            ProcessSample(raw_adc);
        }
    }

    // 하이브리드 전송 조건 검사
    bool shouldSend = false;

    if (current_state != STATE_NORMAL) {
        if (current_state != last_sent_state || (currentMillis - lastSendTime >= EVENT_INTERVAL)) {
            shouldSend = true;
        }
    } 
    else if (current_state == STATE_NORMAL && last_sent_state != STATE_NORMAL) {
        shouldSend = true;
    }
    else if (currentMillis - lastSendTime >= (NORMAL_INTERVAL + nextSendOffset)) {
        shouldSend = true;
    }

    // 전송 조건 충족 시 패킷 발송
    if (shouldSend) {
        uint16_t current_raw = analogRead(PIEZO_PIN);
        
        myData.node_id = NODE_ID;
        myData.voltage = (current_raw / 4095.0) * 3.3; 
        myData.state = (int)current_state;          

        // ESP-NOW 데이터 전송
        esp_now_send(receiverAddress, (uint8_t *) &myData, sizeof(myData));

        // [핵심 2] 무선 전송 시점을 기록하여 직후 40ms 간 샘플링 노이즈 차단
        lastRfTxTime = currentMillis; 
        lastSendTime = currentMillis;
        last_sent_state = current_state;
        nextSendOffset = random(0, 150);
    }
}
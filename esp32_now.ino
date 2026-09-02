#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>

// ── 1. 설정 ──────────────────────────────────────────────
const char* HARDWARE_ID = "S01";
#define NODE_ID 1            // 노드 2번 설정
#define PIEZO_PIN 32         // 센서 핀 (ADC1_CH0 / GPIO 33)

const char* WIFI_SSID     = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// 게이트웨이(허브 보드) MAC 주소
uint8_t receiverAddress[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};  

// ── 2. 매개변수 및 상태 변수 ──────────────────────────────
typedef enum { STATE_NORMAL = 0, STATE_WARNING = 1, STATE_DANGER = 2 } SystemState_t;

// ── 2. 배관 진동 및 단계별 승격 매개변수 ─────────────────
#define BASELINE_SHIFT         5     // 노이즈 후 기준선 복귀 속도 향상 (4~5)
#define WARN_THRESHOLD        80    // 전기적 스파이크(50~80)를 넘기기 위해 100으로 상향
#define CRITICAL_COUNT         3     // WARN 신호 지속 발생 시 승격
#define RF_NOISE_REJECTION_MS 40     // RF 전송 후 노이즈 무시 시간 (40ms)

// ── 3. 비동기 전송 타이밍 제어 변수 ──────────────────────
const unsigned long NORMAL_INTERVAL = 5000; // Normal 상태 전송 주기: 5초
const unsigned long EVENT_INTERVAL  = 300;  // Warn/Critical 재발송 주기: 300ms
unsigned long lastSendTime   = 0;             
unsigned long nextSendOffset = 0;           
unsigned long lastRfTxTime   = 0;           // 마지막 RF 전송 시각
SystemState_t last_sent_state = STATE_NORMAL;

// ── 4. 전역 상태 변수 ─────────────────────────────────────
uint32_t baseline = 0;
int abnormal_count = 0;         
SystemState_t current_state = STATE_NORMAL;

// ── 5. ESP-NOW 전송용 구조체 (메모리 패킹 필수 적용) ──────
typedef struct struct_message {
    int node_id;
    float voltage;
    int state;
} __attribute__((packed)) struct_message;

struct_message myData;
esp_now_peer_info_t peerInfo;

#if defined(ESP_IDF_VERSION_MAJOR) && ESP_IDF_VERSION_MAJOR >= 5
void OnDataSent(const wifi_tx_info_t *info, esp_now_send_status_t status) {}
#else
void OnDataSent(const uint8_t *mac_addr, esp_now_send_status_t status) {}
#endif

// ── 6. 단계별 상태 전이 알고리즘 ───────
void ProcessSample(uint16_t raw) {
    if (baseline == 0) {
        baseline = raw;
    } else {
        baseline += ((int32_t)raw - (int32_t)baseline) >> BASELINE_SHIFT;
    }

    int32_t dev = (int32_t)raw - (int32_t)baseline;
    uint32_t dev_abs = (dev < 0) ? (uint32_t)(-dev) : (uint32_t)dev;

    if (dev_abs >= WARN_THRESHOLD) {
        if (abnormal_count < CRITICAL_COUNT) abnormal_count++;
    } else {
        if (abnormal_count > 0) abnormal_count--;
    }

    if (abnormal_count >= CRITICAL_COUNT) {
        current_state = STATE_DANGER;   
    } else if (abnormal_count > 0) {
        current_state = STATE_WARNING;  
    } else {
        current_state = STATE_NORMAL;   
    }

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
    delay(300);

    Serial.print("현재 기기의 설정된 하드웨어 ID: ");
    Serial.println(HARDWARE_ID);

    analogReadResolution(12);
    analogSetPinAttenuation(PIEZO_PIN, ADC_11db);
    pinMode(PIEZO_PIN, INPUT);

    WiFi.mode(WIFI_STA);
    esp_wifi_set_ps(WIFI_PS_NONE); // 절전 모드 해제

    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    
    while (WiFi.status() != WL_CONNECTED) {
        delay(100);
    }

    esp_wifi_set_channel(10, WIFI_SECOND_CHAN_NONE);

    if (esp_now_init() != ESP_OK) return;
    esp_now_register_send_cb(OnDataSent);

    memset(&peerInfo, 0, sizeof(peerInfo));
    memcpy(peerInfo.peer_addr, receiverAddress, 6);
    peerInfo.channel = 10; 
    peerInfo.encrypt = false;

    if (esp_now_add_peer(&peerInfo) != ESP_OK) return;
}

unsigned long lastSampleTime = 0;

void loop() {
    unsigned long currentMillis = millis();
    unsigned long currentMicros = micros();

    // 1ms(1000us) 스케줄러 유지
    if (currentMicros - lastSampleTime >= 1000) {
        lastSampleTime = currentMicros;

        // RF 전송 후 40ms 노이즈 차단
        if (currentMillis - lastRfTxTime >= RF_NOISE_REJECTION_MS) {
            uint16_t raw_adc = analogRead(PIEZO_PIN);
            ProcessSample(raw_adc);
        }
    }

    // 전송 조건 검사
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

    // ESP-NOW 패킷 발송
    if (shouldSend) {
        uint16_t current_raw = analogRead(PIEZO_PIN);
        
        myData.node_id = NODE_ID;
        myData.voltage = (current_raw / 4095.0) * 3.3; 
        myData.state   = (int)current_state;          

esp_now_send(receiverAddress, (uint8_t *) &myData, sizeof(myData));

        lastRfTxTime    = currentMillis; 
        lastSendTime    = currentMillis;
        last_sent_state = current_state;
        nextSendOffset  = random(0, 150);
    }
}
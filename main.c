/* USER CODE BEGIN Header */
/**
  ******************************************************************************
  * @file           : main.c
  * @brief          : 관제 STM32 - ESP32 CSV 프로토콜 연동 (LED + 부저 경보)
  *                   - 수신 포맷: "node_id,voltage,state\r\n" (예: "1,2.55,1\r\n")
  *                   - Normal (0): Green LED ON / 무음
  *                   - Warning (1): Orange LED ON / 1.2kHz 비프
  *                   - Danger (2): Red LED ON / 3.8kHz 비프
  *
  * PIN MAP
  *  OLED : 128x64 SPI
  *    D0  = PA5  -> SPI1_SCK
  *    D1  = PA7  -> SPI1_MOSI
  *    RES = PB8
  *    DC  = PC7
  *    CS  = PB6
  *
  *  BUZZER = D7 = PA8   (TIM1_CH1 PWM, 가변 주파수)
  *  BUTTON = A3 = PB0   (EXTI0)
  *
  *  ESP32 Gateway
  *    D2 = PA10 = USART1_RX
  *
  *  LED (Active-Low 기준: LOW 점등 / HIGH 소등)
  *    RED    = A2 = PA4
  *    ORANGE = A1 = PA1
  *    GREEN  = A0 = PA0
  ******************************************************************************
  * @attention
  *
  * Copyright (c) 2026 STMicroelectronics.
  * All rights reserved.
  ******************************************************************************
  */
/* USER CODE END Header */
/* Includes ------------------------------------------------------------------*/
#include "main.h"

/* Private includes ----------------------------------------------------------*/
/* USER CODE BEGIN Includes */
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "u8g2.h"
/* USER CODE END Includes */

/* Private typedef -----------------------------------------------------------*/
/* USER CODE BEGIN PTD */
typedef enum {
    STATE_NORMAL = 0,
    STATE_WARN = 1,
    STATE_CRITICAL = 2
} SystemState_t;
/* USER CODE END PTD */

/* Private define ------------------------------------------------------------*/
/* USER CODE BEGIN PD */
#define RX_BUFFER_SIZE 64

/* LED PIN DEFINES */
#define LED_GREEN_GPIO_Port   GPIOA
#define LED_GREEN_Pin_D       GPIO_PIN_0

#define LED_ORANGE_GPIO_Port  GPIOA
#define LED_ORANGE_Pin_D      GPIO_PIN_1

#define LED_RED_GPIO_Port_D   GPIOA
#define LED_RED_Pin_D         GPIO_PIN_4

/* BUTTON */
#define BUTTON_GPIO_Port      GPIOB
#define BUTTON_Pin_D          GPIO_PIN_0

/* OLED */
#define OLED_RES_GPIO_Port_D  GPIOB
#define OLED_RES_Pin_D        GPIO_PIN_8

#define OLED_DC_GPIO_Port_D   GPIOC
#define OLED_DC_Pin_D         GPIO_PIN_7

#define OLED_CS_GPIO_Port_D   GPIOB
#define OLED_CS_Pin_D         GPIO_PIN_6
/* USER CODE END PD */

/* Private macro -------------------------------------------------------------*/
/* USER CODE BEGIN PM */

/* USER CODE END PM */

/* Private variables ---------------------------------------------------------*/
SPI_HandleTypeDef hspi1;

TIM_HandleTypeDef htim1;

UART_HandleTypeDef huart1;
UART_HandleTypeDef huart2;

/* USER CODE BEGIN PV */
extern TIM_HandleTypeDef htim1;
extern UART_HandleTypeDef huart1;
extern UART_HandleTypeDef huart2;

u8g2_t u8g2; // u8g2 디스플레이 객체

volatile SystemState_t current_state = STATE_NORMAL;
volatile uint8_t calibrate_flag = 0;

// 개별 노드(S01, S02) 최신 상태 저장 변수 (0: NORMAL, 1: WARNING, 2: DANGER)
volatile int s01_state = 0;
volatile int s02_state = 0;

// UART 수신 버퍼 및 파싱 변수
uint8_t rx_data = 0;
char rx_buffer[RX_BUFFER_SIZE];
volatile uint8_t rx_index = 0;
volatile uint8_t packet_ready_flag = 0;

// 파싱된 센서 데이터 모니터링 변수
volatile int parsed_node_id = 0;
volatile float parsed_voltage = 0.0f;
volatile int parsed_state = 0;

// Non-blocking 부저 제어 변수
uint32_t last_buzzer_tick = 0;
uint8_t buzzer_state = 0;
SystemState_t prev_buzzer_state = STATE_NORMAL;
uint32_t critical_hold_until = 0; // CRITICAL 최소 지속 시간 보장용
/* USER CODE END PV */

/* Private function prototypes -----------------------------------------------*/
void SystemClock_Config(void);
static void MX_GPIO_Init(void);
static void MX_SPI1_Init(void);
static void MX_USART1_UART_Init(void);
static void MX_USART2_UART_Init(void);
static void MX_TIM1_Init(void);
/* USER CODE BEGIN PFP */
void Buzzer_On(uint32_t freq);
void Buzzer_Off(void);
void Set_LED_State(SystemState_t state);
void Process_Buzzer_Alarm(void);
void Process_UART_Packet(void);

// u8g2 STM32 연동 및 디스플레이 제어 함수
uint8_t u8x8_byte_4wire_hw_spi(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr);
uint8_t u8x8_stm32_gpio_and_delay(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr);
void OLED_Init(void);
void Update_OLED_Display(void);
/* USER CODE END PFP */

/* Private user code ---------------------------------------------------------*/
/* USER CODE BEGIN 0 */

/* USER CODE END 0 */

/**
  * @brief  The application entry point.
  * @retval int
  */
int main(void)
{

  /* USER CODE BEGIN 1 */

  /* USER CODE END 1 */

  /* MCU Configuration--------------------------------------------------------*/

  /* Reset of all peripherals, Initializes the Flash interface and the Systick. */
  HAL_Init();

  /* USER CODE BEGIN Init */

  /* USER CODE END Init */

  /* Configure the system clock */
  SystemClock_Config();

  /* USER CODE BEGIN SysInit */

  /* USER CODE END SysInit */

  /* Initialize all configured peripherals */
  MX_GPIO_Init();
  MX_SPI1_Init();
  MX_USART1_UART_Init();
  MX_USART2_UART_Init();
  MX_TIM1_Init();
  /* USER CODE BEGIN 2 */
  // OLED 디스플레이 초기화 및 기본 화면 출력
  OLED_Init();
  Update_OLED_Display();

  // 시작 비프음 (2000Hz, 100ms)
  Buzzer_On(2000);
  HAL_Delay(100);
  Buzzer_Off();

  // 초기 상태(NORMAL: 초록색 LED) 설정
  Set_LED_State(STATE_NORMAL);

  // ESP32 게이트웨이(USART1)로부터 1바이트 수신 대기 시작
  HAL_UART_Receive_IT(&huart1, &rx_data, 1);
  /* USER CODE END 2 */

  /* Infinite loop */
  /* USER CODE BEGIN WHILE */
  while (1)
  {
    // 1. 완성된 패킷이 있으면 파싱 및 상태 갱신
    if (packet_ready_flag)
    {
      packet_ready_flag = 0;
      Process_UART_Packet();
      Update_OLED_Display(); // 수신 데이터로 디스플레이 갱신
    }

    // 2. 현재 상태에 따른 비프음 처리 (Non-blocking)
    Process_Buzzer_Alarm();

    // 3. 버튼(PB0) 수동 조작 테스트
    if (calibrate_flag)
    {
      calibrate_flag = 0;
      current_state = (SystemState_t)((current_state + 1) % 3);
      Set_LED_State(current_state);
      Update_OLED_Display(); // 수동 변경된 상태 디스플레이 갱신
    }

    /* USER CODE END WHILE */

    /* USER CODE BEGIN 3 */
  }
  /* USER CODE END 3 */
}

/**
  * @brief System Clock Configuration
  * @retval None
  */
void SystemClock_Config(void)
{
  RCC_OscInitTypeDef RCC_OscInitStruct = {0};
  RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};

  /** Configure the main internal regulator output voltage
  */
  __HAL_RCC_PWR_CLK_ENABLE();
  __HAL_PWR_VOLTAGESCALING_CONFIG(PWR_REGULATOR_VOLTAGE_SCALE1);

  /** Initializes the RCC Oscillators according to the specified parameters
  * in the RCC_OscInitTypeDef structure.
  */
  RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSI;
  RCC_OscInitStruct.HSIState = RCC_HSI_ON;
  RCC_OscInitStruct.HSICalibrationValue = RCC_HSICALIBRATION_DEFAULT;
  RCC_OscInitStruct.PLL.PLLState = RCC_PLL_ON;
  RCC_OscInitStruct.PLL.PLLSource = RCC_PLLSOURCE_HSI;
  RCC_OscInitStruct.PLL.PLLM = 16;
  RCC_OscInitStruct.PLL.PLLN = 336;
  RCC_OscInitStruct.PLL.PLLP = RCC_PLLP_DIV4;
  RCC_OscInitStruct.PLL.PLLQ = 4;
  if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK)
  {
    Error_Handler();
  }

  /** Initializes the CPU, AHB and APB buses clocks
  */
  RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK|RCC_CLOCKTYPE_SYSCLK
                              |RCC_CLOCKTYPE_PCLK1|RCC_CLOCKTYPE_PCLK2;
  RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_PLLCLK;
  RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
  RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV2;
  RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV1;

  if (HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_2) != HAL_OK)
  {
    Error_Handler();
  }
}

/**
  * @brief SPI1 Initialization Function
  * @param None
  * @retval None
  */
static void MX_SPI1_Init(void)
{

  /* USER CODE BEGIN SPI1_Init 0 */

  /* USER CODE END SPI1_Init 0 */

  /* USER CODE BEGIN SPI1_Init 1 */

  /* USER CODE END SPI1_Init 1 */
  /* SPI1 parameter configuration*/
  hspi1.Instance = SPI1;
  hspi1.Init.Mode = SPI_MODE_MASTER;
  hspi1.Init.Direction = SPI_DIRECTION_2LINES;
  hspi1.Init.DataSize = SPI_DATASIZE_8BIT;
  hspi1.Init.CLKPolarity = SPI_POLARITY_LOW;
  hspi1.Init.CLKPhase = SPI_PHASE_1EDGE;
  hspi1.Init.NSS = SPI_NSS_SOFT;
  hspi1.Init.BaudRatePrescaler = SPI_BAUDRATEPRESCALER_8;
  hspi1.Init.FirstBit = SPI_FIRSTBIT_MSB;
  hspi1.Init.TIMode = SPI_TIMODE_DISABLE;
  hspi1.Init.CRCCalculation = SPI_CRCCALCULATION_DISABLE;
  hspi1.Init.CRCPolynomial = 10;
  if (HAL_SPI_Init(&hspi1) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN SPI1_Init 2 */

  /* USER CODE END SPI1_Init 2 */

}

/**
  * @brief TIM1 Initialization Function
  * @param None
  * @retval None
  */
static void MX_TIM1_Init(void)
{

  /* USER CODE BEGIN TIM1_Init 0 */

  /* USER CODE END TIM1_Init 0 */

  TIM_ClockConfigTypeDef sClockSourceConfig = {0};
  TIM_MasterConfigTypeDef sMasterConfig = {0};
  TIM_OC_InitTypeDef sConfigOC = {0};
  TIM_BreakDeadTimeConfigTypeDef sBreakDeadTimeConfig = {0};

  /* USER CODE BEGIN TIM1_Init 1 */

  /* USER CODE END TIM1_Init 1 */
  htim1.Instance = TIM1;
  htim1.Init.Prescaler = 83;
  htim1.Init.CounterMode = TIM_COUNTERMODE_UP;
  htim1.Init.Period = 65535;
  htim1.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
  htim1.Init.RepetitionCounter = 0;
  htim1.Init.AutoReloadPreload = TIM_AUTORELOAD_PRELOAD_DISABLE;
  if (HAL_TIM_Base_Init(&htim1) != HAL_OK)
  {
    Error_Handler();
  }
  sClockSourceConfig.ClockSource = TIM_CLOCKSOURCE_INTERNAL;
  if (HAL_TIM_ConfigClockSource(&htim1, &sClockSourceConfig) != HAL_OK)
  {
    Error_Handler();
  }
  if (HAL_TIM_PWM_Init(&htim1) != HAL_OK)
  {
    Error_Handler();
  }
  sMasterConfig.MasterOutputTrigger = TIM_TRGO_RESET;
  sMasterConfig.MasterSlaveMode = TIM_MASTERSLAVEMODE_DISABLE;
  if (HAL_TIMEx_MasterConfigSynchronization(&htim1, &sMasterConfig) != HAL_OK)
  {
    Error_Handler();
  }
  sConfigOC.OCMode = TIM_OCMODE_PWM1;
  sConfigOC.Pulse = 0;
  sConfigOC.OCPolarity = TIM_OCPOLARITY_HIGH;
  sConfigOC.OCNPolarity = TIM_OCNPOLARITY_HIGH;
  sConfigOC.OCFastMode = TIM_OCFAST_DISABLE;
  sConfigOC.OCIdleState = TIM_OCIDLESTATE_RESET;
  sConfigOC.OCNIdleState = TIM_OCNIDLESTATE_RESET;
  if (HAL_TIM_PWM_ConfigChannel(&htim1, &sConfigOC, TIM_CHANNEL_1) != HAL_OK)
  {
    Error_Handler();
  }
  sBreakDeadTimeConfig.OffStateRunMode = TIM_OSSR_DISABLE;
  sBreakDeadTimeConfig.OffStateIDLEMode = TIM_OSSI_DISABLE;
  sBreakDeadTimeConfig.LockLevel = TIM_LOCKLEVEL_OFF;
  sBreakDeadTimeConfig.DeadTime = 0;
  sBreakDeadTimeConfig.BreakState = TIM_BREAK_DISABLE;
  sBreakDeadTimeConfig.BreakPolarity = TIM_BREAKPOLARITY_HIGH;
  sBreakDeadTimeConfig.AutomaticOutput = TIM_AUTOMATICOUTPUT_DISABLE;
  if (HAL_TIMEx_ConfigBreakDeadTime(&htim1, &sBreakDeadTimeConfig) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN TIM1_Init 2 */

  /* USER CODE END TIM1_Init 2 */
  HAL_TIM_MspPostInit(&htim1);

}

/**
  * @brief USART1 Initialization Function
  * @param None
  * @retval None
  */
static void MX_USART1_UART_Init(void)
{

  /* USER CODE BEGIN USART1_Init 0 */

  /* USER CODE END USART1_Init 0 */

  /* USER CODE BEGIN USART1_Init 1 */

  /* USER CODE END USART1_Init 1 */
  huart1.Instance = USART1;
  huart1.Init.BaudRate = 115200;
  huart1.Init.WordLength = UART_WORDLENGTH_8B;
  huart1.Init.StopBits = UART_STOPBITS_1;
  huart1.Init.Parity = UART_PARITY_NONE;
  huart1.Init.Mode = UART_MODE_TX_RX;
  huart1.Init.HwFlowCtl = UART_HWCONTROL_NONE;
  huart1.Init.OverSampling = UART_OVERSAMPLING_16;
  if (HAL_UART_Init(&huart1) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN USART1_Init 2 */

  /* USER CODE END USART1_Init 2 */

}

/**
  * @brief USART2 Initialization Function
  * @param None
  * @retval None
  */
static void MX_USART2_UART_Init(void)
{

  /* USER CODE BEGIN USART2_Init 0 */

  /* USER CODE END USART2_Init 0 */

  /* USER CODE BEGIN USART2_Init 1 */

  /* USER CODE END USART2_Init 1 */
  huart2.Instance = USART2;
  huart2.Init.BaudRate = 115200;
  huart2.Init.WordLength = UART_WORDLENGTH_8B;
  huart2.Init.StopBits = UART_STOPBITS_1;
  huart2.Init.Parity = UART_PARITY_NONE;
  huart2.Init.Mode = UART_MODE_TX_RX;
  huart2.Init.HwFlowCtl = UART_HWCONTROL_NONE;
  huart2.Init.OverSampling = UART_OVERSAMPLING_16;
  if (HAL_UART_Init(&huart2) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN USART2_Init 2 */

  /* USER CODE END USART2_Init 2 */

}

/**
  * @brief GPIO Initialization Function
  * @param None
  * @retval None
  */
static void MX_GPIO_Init(void)
{
  GPIO_InitTypeDef GPIO_InitStruct = {0};
  /* USER CODE BEGIN MX_GPIO_Init_1 */

  /* USER CODE END MX_GPIO_Init_1 */

  /* GPIO Ports Clock Enable */
  __HAL_RCC_GPIOC_CLK_ENABLE();
  __HAL_RCC_GPIOH_CLK_ENABLE();
  __HAL_RCC_GPIOA_CLK_ENABLE();
  __HAL_RCC_GPIOB_CLK_ENABLE();

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(GPIOA, GREEN_LED_Pin|ORANGE_LED_Pin|RED_LED_Pin, GPIO_PIN_RESET);

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(OLED_DC_GPIO_Port, OLED_DC_Pin, GPIO_PIN_RESET);

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(GPIOB, OLED_CS_Pin|OLED_RES_Pin, GPIO_PIN_SET);

  /*Configure GPIO pin : B1_Pin */
  GPIO_InitStruct.Pin = B1_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_IT_FALLING;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  HAL_GPIO_Init(B1_GPIO_Port, &GPIO_InitStruct);

  /*Configure GPIO pins : GREEN_LED_Pin ORANGE_LED_Pin RED_LED_Pin */
  GPIO_InitStruct.Pin = GREEN_LED_Pin|ORANGE_LED_Pin|RED_LED_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

  /*Configure GPIO pin : Button_Pin */
  GPIO_InitStruct.Pin = Button_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_INPUT;
  GPIO_InitStruct.Pull = GPIO_PULLUP;
  HAL_GPIO_Init(Button_GPIO_Port, &GPIO_InitStruct);

  /*Configure GPIO pin : OLED_DC_Pin */
  GPIO_InitStruct.Pin = OLED_DC_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(OLED_DC_GPIO_Port, &GPIO_InitStruct);

  /*Configure GPIO pins : OLED_CS_Pin OLED_RES_Pin */
  GPIO_InitStruct.Pin = OLED_CS_Pin|OLED_RES_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(GPIOB, &GPIO_InitStruct);

  /* USER CODE BEGIN MX_GPIO_Init_2 */

  /* USER CODE END MX_GPIO_Init_2 */
}

/* USER CODE BEGIN 4 */

// 상태별 LED 배타적 점등 함수 (Active-Low 기준: SET=소등, RESET=점등)
void Set_LED_State(SystemState_t state)
{
    // 1. 모든 LED 소등 (HIGH 레벨 출력)
    HAL_GPIO_WritePin(GPIOA, LED_GREEN_Pin_D, GPIO_PIN_SET);
    HAL_GPIO_WritePin(GPIOA, LED_ORANGE_Pin_D, GPIO_PIN_SET);
    HAL_GPIO_WritePin(GPIOA, LED_RED_Pin_D, GPIO_PIN_SET);

    // 2. 해당 상태의 LED만 점등 (LOW 레벨 출력)
    switch (state)
    {
        case STATE_NORMAL:   // 0: Normal -> 초록색 ON
            HAL_GPIO_WritePin(GPIOA, LED_GREEN_Pin_D, GPIO_PIN_RESET);
            break;

        case STATE_WARN:     // 1: Warning -> 주황색 ON
            HAL_GPIO_WritePin(GPIOA, LED_ORANGE_Pin_D, GPIO_PIN_RESET);
            break;

        case STATE_CRITICAL: // 2: Danger -> 빨간색 ON
            HAL_GPIO_WritePin(GPIOA, LED_RED_Pin_D, GPIO_PIN_RESET);
            break;

        default:
            break;
    }
}

// 상태별 부저 비프음 처리 (Non-blocking: 초고속 70ms 비프, 동일 빈도 / CRITICAL 우선권 보장 + 500ms 래치 적용)
void Process_Buzzer_Alarm(void)
{
    uint32_t now = HAL_GetTick();
    SystemState_t active_state = current_state;

    // 1. CRITICAL이 단 한 번이라도 들어오면 500ms 동안 CRITICAL 상태 유지
    if (current_state == STATE_CRITICAL)
    {
        critical_hold_until = now + 500; // 약 3~4회 비프 보장
    }

    if (now < critical_hold_until)
    {
        active_state = STATE_CRITICAL;
    }

    // 2. 상태가 전환된 순간(특히 WARN -> CRITICAL) 즉시 가로채어 발음 시작
    if (active_state != prev_buzzer_state)
    {
        prev_buzzer_state = active_state;
        Buzzer_Off();
        buzzer_state = 0;
        last_buzzer_tick = 0; // 대기 시간 리셋하여 아래 조건문에서 즉시 ON되도록 유도
    }

    // 3. 상태별 비프 출력
    switch (active_state)
    {
        case STATE_NORMAL:
            if (buzzer_state)
            {
                Buzzer_Off();
                buzzer_state = 0;
            }
            break;

        case STATE_WARN:
        case STATE_CRITICAL:
        {
            // 확연한 주파수 대비 (WARN: 1200Hz 저음 / CRITICAL: 3800Hz 날카로운 고음)
            uint32_t freq = (active_state == STATE_CRITICAL) ? 3800 : 1200;

            if (buzzer_state == 0 && (now - last_buzzer_tick >= 70))
            {
                Buzzer_On(freq);
                buzzer_state = 1;
                last_buzzer_tick = now;
            }
            else if (buzzer_state == 1 && (now - last_buzzer_tick >= 70))
            {
                Buzzer_Off();
                buzzer_state = 0;
                last_buzzer_tick = now;
            }
            break;
        }

        default:
            break;
    }
}

// ESP32 CSV 문자열("node_id,voltage,state\r\n") 파싱 및 2개 센서(S01, S02) 종합 상태 판정
void Process_UART_Packet(void)
{
    int node_id = 0;
    float voltage = 0.0f;
    int state_val = 0;

    // CSV 포맷 파싱
    if (sscanf(rx_buffer, "%d,%f,%d", &node_id, &voltage, &state_val) >= 3)
    {
        parsed_node_id = node_id;
        parsed_voltage = voltage;
        parsed_state = state_val;

        // 1. 들어온 노드 ID에 따라 개별 센서 상태 갱신
        if (node_id == 1)      // S01 센서
        {
            s01_state = state_val;
        }
        else if (node_id == 2) // S02 센서
        {
            s02_state = state_val;
        }

        // 2. 둘 중 더 위험한 상태(더 큰 값)를 전체 시스템 상태로 채택
        int overall_state = s01_state;
        if (s02_state > overall_state)
        {
            overall_state = s02_state;
        }

        // 3. 통합 상태 갱신 및 LED 반영
        current_state = (SystemState_t)overall_state;
        Set_LED_State(current_state);
    }
}

// USART1 수신 인터럽트 콜백
void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart)
{
    if (huart->Instance == USART1)
    {
        // 줄바꿈 문자(\n) 수신 시 한 줄 패킷 수신 완료
        if (rx_data == '\n')
        {
            rx_buffer[rx_index] = '\0';
            rx_index = 0;
            packet_ready_flag = 1;
        }
        else if (rx_data != '\r') // '\r'은 무시
        {
            if (rx_index < RX_BUFFER_SIZE - 1)
            {
                rx_buffer[rx_index++] = (char)rx_data;
            }
            else
            {
                rx_index = 0; // 버퍼 오버플로우 방지
            }
        }

        // 다음 1바이트 수신 대기 재등록
        HAL_UART_Receive_IT(&huart1, &rx_data, 1);
    }
}

// 스위치 인터럽트 콜백 (BUTTON = PB0, 디바운싱 적용)
void HAL_GPIO_EXTI_Callback(uint16_t GPIO_Pin)
{
    static uint32_t last_exti_tick = 0;
    uint32_t now = HAL_GetTick();

    if (GPIO_Pin == BUTTON_Pin_D)
    {
        // 200ms 이내의 중복 입력(채터링) 무시
        if (now - last_exti_tick > 200)
        {
            calibrate_flag = 1;
            last_exti_tick = now;
        }
    }
}

void Buzzer_On(uint32_t freq)
{
    if (freq == 0) return;

    // 1. 주파수에 따른 ARR 및 50% 듀티비 계산 (TIM1 카운터 클록 1MHz 기준)
    uint32_t arr = (1000000 / freq) - 1;

    // 2. 타이머 주기 및 듀티비 갱신
    __HAL_TIM_SET_AUTORELOAD(&htim1, arr);
    __HAL_TIM_SET_COMPARE(&htim1, TIM_CHANNEL_1, (arr + 1) / 2);

    // 3. 카운터를 0으로 리셋하여 즉시 새 주기 반영
    __HAL_TIM_SET_COUNTER(&htim1, 0);

    // 4. PWM 시작 및 TIM1 Main Output(MOE) 강제 활성화
    HAL_TIM_PWM_Start(&htim1, TIM_CHANNEL_1);
    __HAL_TIM_MOE_ENABLE(&htim1);
}

void Buzzer_Off(void)
{
    // PWM 출력 정지 및 MOE 비활성화
    __HAL_TIM_MOE_DISABLE(&htim1);
    HAL_TIM_PWM_Stop(&htim1, TIM_CHANNEL_1);
    __HAL_TIM_SET_COMPARE(&htim1, TIM_CHANNEL_1, 0);
}

// u8g2 하드웨어 SPI 통신 바이트 콜백
uint8_t u8x8_byte_4wire_hw_spi(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr)
{
    switch (msg)
    {
        case U8X8_MSG_BYTE_SEND:
            HAL_SPI_Transmit(&hspi1, (uint8_t *)arg_ptr, arg_int, 1000);
            break;
        case U8X8_MSG_BYTE_INIT:
            break;
        case U8X8_MSG_BYTE_SET_DC:
            HAL_GPIO_WritePin(OLED_DC_GPIO_Port_D, OLED_DC_Pin_D, arg_int ? GPIO_PIN_SET : GPIO_PIN_RESET);
            break;
        case U8X8_MSG_BYTE_START_TRANSFER:
            HAL_GPIO_WritePin(OLED_CS_GPIO_Port_D, OLED_CS_Pin_D, GPIO_PIN_RESET);
            break;
        case U8X8_MSG_BYTE_END_TRANSFER:
            HAL_GPIO_WritePin(OLED_CS_GPIO_Port_D, OLED_CS_Pin_D, GPIO_PIN_SET);
            break;
        default:
            return 0;
    }
    return 1;
}

// u8g2 GPIO 및 지연시간 제어 콜백
uint8_t u8x8_stm32_gpio_and_delay(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr)
{
    switch (msg)
    {
        case U8X8_MSG_DELAY_MILLI:
            HAL_Delay(arg_int);
            break;
        case U8X8_MSG_DELAY_10MICRO:
            for (volatile uint32_t i = 0; i < arg_int * 10; i++) { __NOP(); }
            break;
        case U8X8_MSG_GPIO_RESET:
            HAL_GPIO_WritePin(OLED_RES_GPIO_Port_D, OLED_RES_Pin_D, arg_int ? GPIO_PIN_SET : GPIO_PIN_RESET);
            break;
        default:
            return 0;
    }
    return 1;
}

// OLED 초기화 함수
void OLED_Init(void)
{
    u8g2_Setup_ssd1306_128x64_noname_f(&u8g2, U8G2_R0, u8x8_byte_4wire_hw_spi, u8x8_stm32_gpio_and_delay);
    u8g2_InitDisplay(&u8g2);
    u8g2_SetPowerSave(&u8g2, 0);
}

// OLED 화면 갱신 함수 (Normal: 공백 / Warn, Critical: 상태 및 원인 센서 출력)
void Update_OLED_Display(void)
{
    u8g2_ClearBuffer(&u8g2);

    // 1. Normal 상태일 때는 화면에 아무것도 띄우지 않음 (공백 유지)
    if (current_state == STATE_NORMAL)
    {
        u8g2_SendBuffer(&u8g2);
        return;
    }

    // 2. 현재 시스템 상태(current_state)와 동일한 위험 레벨을 준 센서 필터링
    char sensor_list[32] = "";
    uint8_t s01_match = (s01_state == current_state);
    uint8_t s02_match = (s02_state == current_state);

    if (s01_match && s02_match)
    {
        strcpy(sensor_list, "S01, S02"); // 둘 다 해당 위험 상태인 경우
    }
    else if (s01_match)
    {
        strcpy(sensor_list, "S01");      // S01만 해당 위험 상태인 경우
    }
    else if (s02_match)
    {
        strcpy(sensor_list, "S02");      // S02만 해당 위험 상태인 경우
    }
    else
    {
        // 버튼 수동 조작 테스트 등 일치 센서가 없을 경우 최근 수신 노드 표시
        snprintf(sensor_list, sizeof(sensor_list), "S%02d", parsed_node_id);
    }

    // 3. 상태(STATE) 출력 (가독성 높은 굵은 폰트 적용)
    u8g2_SetFont(&u8g2, u8g2_font_7x14B_tf);

    if (current_state == STATE_WARN)
    {
        u8g2_DrawStr(&u8g2, 8, 26, "STATE : WARN");
    }
    else if (current_state == STATE_CRITICAL)
    {
        u8g2_DrawStr(&u8g2, 8, 26, "STATE : CRITICAL");
    }

    // 4. 해당 시그널을 준 센서(SENSOR) 출력 (버퍼 크기 48바이트로 확장 및 snprintf 적용)
    char sensor_display[48];
    snprintf(sensor_display, sizeof(sensor_display), "SENSOR: %s", sensor_list);
    u8g2_DrawStr(&u8g2, 8, 48, sensor_display);

    u8g2_SendBuffer(&u8g2);
}

/* USER CODE END 4 */

/**
  * @brief  This function is executed in case of error occurrence.
  * @retval None
  */
void Error_Handler(void)
{
  /* USER CODE BEGIN Error_Handler_Debug */
  __disable_irq();
  while (1)
  {
  }
  /* USER CODE END Error_Handler_Debug */
}
#ifdef USE_FULL_ASSERT
/**
  * @brief  Reports the name of the source file and the source line number
  *         where the assert_param error has occurred.
  * @param  file: pointer to the source file name
  * @param  line: assert_param error line source number
  * @retval None
  */
void assert_failed(uint8_t *file, uint32_t line)
{
  /* USER CODE BEGIN 6 */
  /* USER CODE END 6 */
}
#endif /* USE_FULL_ASSERT */

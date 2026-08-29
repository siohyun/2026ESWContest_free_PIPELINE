/* USER CODE BEGIN Header */
/**
  ******************************************************************************
  * @file           : main.c
  * @brief          : 관제 STM32 - ESP32 CSV 프로토콜 연동 (LED + 부저 경보)
  *                   - 수신 포맷: "node_id,voltage,state\r\n" (예: "1,2.55,1\r\n")
  *                   - Normal (0): Green LED ON / 무음
  *                   - Warning (1): Yellow LED ON / 1kHz 간헐 비프
  *                   - Danger (2): Red LED ON / 3.5kHz 긴급 비프
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
/* USER CODE END PD */

/* Private macro -------------------------------------------------------------*/
/* USER CODE BEGIN PM */

/* USER CODE END PM */

/* Private variables ---------------------------------------------------------*/
SPI_HandleTypeDef hspi1;

TIM_HandleTypeDef htim3;

UART_HandleTypeDef huart1;
UART_HandleTypeDef huart2;

/* USER CODE BEGIN PV */
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
/* USER CODE END PV */

/* Private function prototypes -----------------------------------------------*/
void SystemClock_Config(void);
static void MX_GPIO_Init(void);
static void MX_USART2_UART_Init(void);
static void MX_TIM3_Init(void);
static void MX_SPI1_Init(void);
static void MX_USART1_UART_Init(void);
/* USER CODE BEGIN PFP */
void Buzzer_On(uint32_t freq);
void Buzzer_Off(void);
void Set_LED_State(SystemState_t state);
void Process_Buzzer_Alarm(void);
void Process_UART_Packet(void);
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
  MX_USART2_UART_Init();
  MX_TIM3_Init();
  MX_SPI1_Init();
  MX_USART1_UART_Init();

  /* USER CODE BEGIN 2 */
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
    }

    // 2. 현재 상태에 따른 비프음 처리 (HAL_Delay 없이 동작)
    Process_Buzzer_Alarm();

    // 3. 버튼(PB0) 수동 조작 테스트
    if (calibrate_flag)
    {
      calibrate_flag = 0;
      current_state = (current_state + 1) % 3;
      Set_LED_State(current_state);
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

  __HAL_RCC_PWR_CLK_ENABLE();
  __HAL_PWR_VOLTAGESCALING_CONFIG(PWR_REGULATOR_VOLTAGE_SCALE1);

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
  hspi1.Instance = SPI1;
  hspi1.Init.Mode = SPI_MODE_MASTER;
  hspi1.Init.Direction = SPI_DIRECTION_2LINES;
  hspi1.Init.DataSize = SPI_DATASIZE_8BIT;
  hspi1.Init.CLKPolarity = SPI_POLARITY_LOW;
  hspi1.Init.CLKPhase = SPI_PHASE_1EDGE;
  hspi1.Init.NSS = SPI_NSS_SOFT;
  hspi1.Init.BaudRatePrescaler = SPI_BAUDRATEPRESCALER_16;
  hspi1.Init.FirstBit = SPI_FIRSTBIT_MSB;
  hspi1.Init.TIMode = SPI_TIMODE_DISABLE;
  hspi1.Init.CRCCalculation = SPI_CRCCALCULATION_DISABLE;
  hspi1.Init.CRCPolynomial = 10;
  if (HAL_SPI_Init(&hspi1) != HAL_OK)
  {
    Error_Handler();
  }
}

/**
  * @brief TIM3 Initialization Function
  * @param None
  * @retval None
  */
static void MX_TIM3_Init(void)
{
  TIM_MasterConfigTypeDef sMasterConfig = {0};
  TIM_OC_InitTypeDef sConfigOC = {0};

  htim3.Instance = TIM3;
  htim3.Init.Prescaler = 83;
  htim3.Init.CounterMode = TIM_COUNTERMODE_UP;
  htim3.Init.Period = 499;
  htim3.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
  htim3.Init.AutoReloadPreload = TIM_AUTORELOAD_PRELOAD_DISABLE;
  if (HAL_TIM_PWM_Init(&htim3) != HAL_OK)
  {
    Error_Handler();
  }
  sMasterConfig.MasterOutputTrigger = TIM_TRGO_RESET;
  sMasterConfig.MasterSlaveMode = TIM_MASTERSLAVEMODE_DISABLE;

  if (HAL_TIMEx_MasterConfigSynchronization(&htim3, &sMasterConfig) != HAL_OK)
  {
    Error_Handler();
  }

  sConfigOC.OCMode = TIM_OCMODE_PWM1;
  sConfigOC.Pulse = 250;
  sConfigOC.OCPolarity = TIM_OCPOLARITY_HIGH;
  sConfigOC.OCFastMode = TIM_OCFAST_DISABLE;
  if (HAL_TIM_PWM_ConfigChannel(&htim3, &sConfigOC, TIM_CHANNEL_1) != HAL_OK)
  {
    Error_Handler();
  }
  HAL_TIM_MspPostInit(&htim3);
}

/**
  * @brief USART1 Initialization Function
  * @param None
  * @retval None
  */
static void MX_USART1_UART_Init(void)
{
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
}

/**
  * @brief USART2 Initialization Function
  * @param None
  * @retval None
  */
static void MX_USART2_UART_Init(void)
{
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
}

/**
  * @brief GPIO Initialization Function
  * @param None
  * @retval None
  */
static void MX_GPIO_Init(void)
{
  GPIO_InitTypeDef GPIO_InitStruct = {0};

  /* GPIO Ports Clock Enable */
  __HAL_RCC_GPIOC_CLK_ENABLE();
  __HAL_RCC_GPIOA_CLK_ENABLE();
  __HAL_RCC_GPIOB_CLK_ENABLE();

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(GPIOA, LED_GREEN_Pin|OLED_RES_Pin|GPIO_PIN_5, GPIO_PIN_RESET);

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(OLED_DC_GPIO_Port, OLED_DC_Pin, GPIO_PIN_RESET);

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(GPIOB, LED_RED_Pin|OLED_CS_Pin, GPIO_PIN_RESET);

  /*Configure GPIO pin : B1_Pin */
  GPIO_InitStruct.Pin = B1_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_IT_FALLING;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  HAL_GPIO_Init(B1_GPIO_Port, &GPIO_InitStruct);

  /*Configure GPIO pins : LED_GREEN_Pin OLED_RES_Pin PA5(Yellow LED) */
  GPIO_InitStruct.Pin = LED_GREEN_Pin|OLED_RES_Pin|GPIO_PIN_5;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

  /*Configure GPIO pin : S_Pin */
  GPIO_InitStruct.Pin = S_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_IT_FALLING;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  HAL_GPIO_Init(S_GPIO_Port, &GPIO_InitStruct);

  /*Configure GPIO pin : OLED_DC_Pin */
  GPIO_InitStruct.Pin = OLED_DC_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(OLED_DC_GPIO_Port, &GPIO_InitStruct);

  /*Configure GPIO pins : LED_RED_Pin OLED_CS_Pin */
  GPIO_InitStruct.Pin = LED_RED_Pin|OLED_CS_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(GPIOB, &GPIO_InitStruct);

  /* EXTI interrupt init*/
  HAL_NVIC_SetPriority(EXTI0_IRQn, 0, 0);
  HAL_NVIC_EnableIRQ(EXTI0_IRQn);
}

/* USER CODE BEGIN 4 */

// 상태별 LED 배타적 점등 함수
void Set_LED_State(SystemState_t state)
{
    // 1. 모든 LED 소등 (Green: PA0, Yellow: PA5, Red: PB4)
    HAL_GPIO_WritePin(GPIOA, LED_GREEN_Pin, GPIO_PIN_RESET);
    HAL_GPIO_WritePin(GPIOA, GPIO_PIN_5, GPIO_PIN_RESET);
    HAL_GPIO_WritePin(GPIOB, LED_RED_Pin, GPIO_PIN_RESET);

    // 2. 해당 상태의 LED만 점등
    switch (state)
    {
        case STATE_NORMAL:   // 0: Normal -> 초록색 ON
            HAL_GPIO_WritePin(GPIOA, LED_GREEN_Pin, GPIO_PIN_SET);
            break;

        case STATE_WARN:     // 1: Warning -> 주황색 ON
            HAL_GPIO_WritePin(GPIOA, GPIO_PIN_5, GPIO_PIN_SET);
            break;

        case STATE_CRITICAL: // 2: Danger -> 빨간색 ON
            HAL_GPIO_WritePin(GPIOB, LED_RED_Pin, GPIO_PIN_SET);
            break;

        default:
            break;
    }
}

// 상태별 부저 비프음 처리 (Non-blocking 주기 제어)
void Process_Buzzer_Alarm(void)
{
    uint32_t now = HAL_GetTick();

    switch (current_state)
    {
        case STATE_NORMAL:
            if (buzzer_state)
            {
                Buzzer_Off();
                buzzer_state = 0;
            }
            break;

        case STATE_WARN:
            // 1000Hz 저주파수: 200ms ON / 800ms OFF (느린 비프)
            if (buzzer_state == 0 && (now - last_buzzer_tick >= 800))
            {
                Buzzer_On(1000);
                buzzer_state = 1;
                last_buzzer_tick = now;
            }
            else if (buzzer_state == 1 && (now - last_buzzer_tick >= 200))
            {
                Buzzer_Off();
                buzzer_state = 0;
                last_buzzer_tick = now;
            }
            break;

        case STATE_CRITICAL:
            // 3500Hz 고주파수: 100ms ON / 100ms OFF (빠른 긴급 비프)
            if (buzzer_state == 0 && (now - last_buzzer_tick >= 100))
            {
                Buzzer_On(3500);
                buzzer_state = 1;
                last_buzzer_tick = now;
            }
            else if (buzzer_state == 1 && (now - last_buzzer_tick >= 100))
            {
                Buzzer_Off();
                buzzer_state = 0;
                last_buzzer_tick = now;
            }
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

// 스위치 인터럽트 콜백
void HAL_GPIO_EXTI_Callback(uint16_t GPIO_Pin)
{
    if (GPIO_Pin == GPIO_PIN_0)
    {
        calibrate_flag = 1;
    }
}

// 부저 PWM 가변 주파수 제어 함수
void Buzzer_On(uint32_t freq)
{
    if (freq == 0) return;
    uint32_t arr = 1000000 / freq - 1;
    __HAL_TIM_SET_AUTORELOAD(&htim3, arr);
    __HAL_TIM_SET_COMPARE(&htim3, TIM_CHANNEL_1, arr / 2);
    HAL_TIM_PWM_Start(&htim3, TIM_CHANNEL_1);
}

void Buzzer_Off(void)
{
    HAL_TIM_PWM_Stop(&htim3, TIM_CHANNEL_1);
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

#ifdef  USE_FULL_ASSERT
void assert_failed(uint8_t *file, uint32_t line)
{
  /* USER CODE BEGIN 6 */
  /* USER CODE END 6 */
}
#endif /* USE_FULL_ASSERT */

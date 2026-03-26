# GS805Serial Android SDK

GS805 커피머신 시리얼 통신을 위한 Android Native SDK (Kotlin).

## 설치

### JitPack (권장)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.anyeats:anyeats-mip-sdk-java:1.0.0")
}
```

## 빠른 시작

```kotlin
// 1. 인스턴스 생성
val gs805 = GS805Serial(context)

// 2. 디바이스 검색 및 연결
val devices = gs805.listDevices()
gs805.connect(devices.first())

// 3. 음료 제조
gs805.makeDrink(DrinkNumber.HOT_DRINK_1)

// 4. 상태 조회
val status = gs805.getMachineStatus()
val balance = gs805.getBalance()
val errorInfo = gs805.getErrorInfo()

// 5. 이벤트 관찰 (Flow)
gs805.eventFlow.collect { event ->
    when (event.type) {
        MachineEventType.DRINK_COMPLETE -> println("음료 완성!")
        MachineEventType.CUP_DROP_SUCCESS -> println("컵 낙하 성공")
        else -> {}
    }
}

// 6. 연결 해제
gs805.disconnect()
gs805.dispose()
```

## API 레퍼런스

### GS805Serial

| 메서드 | 설명 |
|---|---|
| `listDevices()` | USB 시리얼 디바이스 목록 |
| `connect(device, config?)` | 디바이스 연결 |
| `connectToFirstDevice(config?)` | 첫 번째 디바이스에 연결 |
| `connectByVidPid(vid, pid, config?)` | VID/PID로 연결 |
| `disconnect()` | 연결 해제 |
| `makeDrink(drink, useLocalBalance?, timeout?)` | 음료 제조 |
| `setHotTemperature(upper, lower)` | 온수 온도 설정 (60-99°C) |
| `setColdTemperature(upper, lower)` | 냉수 온도 설정 (2-40°C) |
| `getSalesCount(drink)` | 판매 수량 조회 |
| `getMachineStatus()` | 머신 상태 조회 |
| `getErrorCode()` | 에러 코드 조회 |
| `getErrorInfo()` | 에러 상세 정보 (복구 가이드 포함) |
| `getBalance()` | 잔액 조회 |
| `setCupDropMode(mode)` | 컵 낙하 모드 설정 |
| `testCupDrop()` | 컵 낙하 테스트 |
| `autoInspection()` | 자동 점검 |
| `cleanAllPipes()` | 전체 파이프 세척 |
| `cleanSpecificPipe(number)` | 특정 파이프 세척 |
| `returnChange()` | 거스름돈 반환 |
| `setDrinkPrice(drink, price)` | 음료 가격 설정 (0-99) |
| `setDrinkRecipeProcess(drink, steps)` | 커스텀 음료 레시피 정의 (R시리즈) |
| `executeChannel(channel, waterType, ...)` | 단일 채널 즉시 실행 (R시리즈) |
| `unitFunctionTest(testCmd, data1, data2, data3)` | 단위 기능 테스트 (Series 3/R) |
| `lockDoor(lockNumber?)` | 전자 잠금 장치 잠금 (Series 3/R) |
| `unlockDoor(lockNumber?)` | 전자 잠금 장치 해제 (Series 3/R) |
| `getLockStatus(lockNumber?)` | 잠금 장치 상태 조회 (Series 3/R) |
| `waterRefill()` | 물 보충 작업 (Series 3/R) |
| `getControllerStatus()` | 메인 컨트롤러 32비트 상세 상태 조회 (Series 3/R) |
| `getDrinkStatus()` | 음료 제작 상태 조회 (진행률/실패코드) (Series 3/R) |
| `getObjectException(objectType)` | 객체별 상세 에러 조회 (Series 3/R) |
| `forceStopDrinkProcess()` | 음료 제조 강제 종료 (Series 3/R) |
| `forceStopCupDelivery()` | 컵 전달 강제 종료 (Series 3/R) |
| `cupDelivery(waitTimeSeconds)` | 컵 전달 (문 개방 + 대기시간) (Series 3/R) |

### Event Flows

| Flow | 타입 | 설명 |
|---|---|---|
| `messageFlow` | `SharedFlow<ResponseMessage>` | 모든 응답 메시지 |
| `eventFlow` | `Flow<MachineEvent>` | 머신 이벤트 (능동 보고) |
| `connectionStateFlow` | `StateFlow<Boolean>` | 연결 상태 |
| `reconnectEventFlow` | `SharedFlow<ReconnectEvent>` | 재연결 이벤트 |

### 아키텍처

```
GS805Serial (Public API)
├── SerialManager
│   ├── SerialConnection (interface)
│   │   └── UsbSerialConnection (USB 구현)
│   ├── MessageParser (바이트 → ResponseMessage)
│   └── ReconnectManager (자동 재연결)
├── CommandQueue (순차 명령 큐)
├── GS805Logger (로깅)
└── MdbCashless (MDB 카드리더 - 별도 포트)
```

## MDB 카드 결제 연동

SDK에 포함된 `MdbCashless` 클래스를 통해 MDB-RS232 브릿지 기반 캐시리스 카드 리더를 제어할 수 있습니다.
GS805 머신과는 **별도의 시리얼 포트**를 사용합니다.

```kotlin
// MDB 카드리더 인스턴스 (GS805Serial과 별도)
val mdb = MdbCashless()

// 1. MDB 브릿지 연결
val mdbDevices = mdb.listDevices()
mdb.connect(mdbDevices.first())

// 2. 초기 설정 (앱 시작 시 1회)
mdb.setup(maxPrice = 0xFFFF, minPrice = 0x0000)
mdb.enable()

// 3. 결제 이벤트 수신
mdb.eventFlow.collect { event ->
    when (event.type) {
        CashlessEventType.CARD_DETECTED -> {
            mdb.requestVend(price = 1000, itemNumber = 1)
        }
        CashlessEventType.VEND_APPROVED -> {
            gs805.makeDrink(DrinkNumber.HOT_DRINK_1)  // 음료 제조
            mdb.vendSuccess(itemNumber = 1)
            mdb.sessionComplete()
        }
        CashlessEventType.VEND_DENIED -> {
            mdb.sessionComplete()
        }
        else -> {}
    }
}
```

### MDB API 요약

| 메서드 | 설명 |
|---|---|
| `listDevices()` | MDB 브릿지 디바이스 목록 |
| `connect(device)` | MDB-RS232 브릿지 연결 |
| `disconnect()` | 연결 해제 |
| `setup(maxPrice?, minPrice?)` | 카드 리더 초기화 |
| `enable()` / `disable()` | 카드 리더 활성화/비활성화 |
| `requestVend(price, itemNumber?)` | 결제 요청 |
| `vendSuccess(itemNumber?)` | 배출 성공 알림 |
| `vendCancel()` | 벤딩 취소 |
| `cashSale(price, itemNumber?)` | 현금 판매 보고 |
| `sessionComplete()` | 세션 종료 |

> 자세한 내용은 [ShakeBox_MDB_Payment_Guide](ShakeBox_MDB_Payment_Guide.html) 참조

## 커스텀 음료 제조 (R시리즈)

### 방법 1: 레시피 정의 후 실행

```kotlin
// 레시피 정의: 컵 배출 → Powder1 + 물 200mL → Powder2
val steps = listOf(
    RecipeStep.cupDispense(dispenser = 1),
    RecipeStep.instantChannel(
        channel = 0, waterType = WaterType.HOT,
        materialDuration = 1000, waterAmount = 2000,
        materialSpeed = 50, mixSpeed = 30,
    ),
    RecipeStep.instantChannel(
        channel = 1, waterType = WaterType.HOT,
        materialDuration = 500, waterAmount = 0,
        materialSpeed = 50, mixSpeed = 30,
    ),
)
gs805.setDrinkRecipeProcess(DrinkNumber.HOT_DRINK_1, steps)
gs805.makeDrink(DrinkNumber.HOT_DRINK_1)
```

### 방법 2: 채널 즉시 실행

```kotlin
gs805.testCupDrop()
gs805.executeChannel(channel = 0, waterType = WaterType.HOT,
    materialDuration = 1000, waterAmount = 2000, materialSpeed = 50)
gs805.executeChannel(channel = 1, waterType = WaterType.HOT,
    materialDuration = 500, waterAmount = 0, materialSpeed = 50)
```

> 자세한 내용은 [make_recipe.md](../make_recipe.md) 참조

## 기술 스택

- **언어**: Kotlin
- **비동기**: Coroutines + Flow
- **USB 시리얼**: usb-serial-for-android v3.7.3
- **최소 SDK**: API 21 (Android 5.0)
- **빌드**: Gradle KTS, AGP 8.2

## 라이선스

Proprietary - AnyEats Co., Ltd.

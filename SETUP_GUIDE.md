# GS805Serial Android SDK 설정 가이드

## 1. USB 시리얼 설정

### AndroidManifest.xml

```xml
<!-- USB Host 기능 선언 -->
<uses-feature android:name="android.hardware.usb.host" android:required="true" />

<!-- Activity에 USB 디바이스 자동 연결 설정 -->
<activity android:name=".MainActivity">
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
    </intent-filter>
    <meta-data
        android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
        android:resource="@xml/device_filter" />
</activity>
```

### res/xml/device_filter.xml

모든 USB 시리얼 디바이스를 허용하려면:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <usb-device />
</resources>
```

특정 VID/PID만 허용하려면:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <usb-device vendor-id="1234" product-id="5678" />
</resources>
```

## 2. USB 권한 처리

SDK가 자동으로 USB 권한을 요청합니다. `UsbSerialConnection.connect()` 호출 시:

1. `UsbManager.hasPermission()` 확인
2. 권한이 없으면 `UsbManager.requestPermission()` 호출
3. BroadcastReceiver로 결과 수신
4. 권한 거부 시 `ConnectionException` throw

수동으로 권한을 처리하고 싶다면 `connect()` 호출 전에 직접 권한을 요청하세요.

## 3. 시리얼 통신 설정

GS805 기본 설정 (자동 적용):

| 항목 | 값 |
|---|---|
| Baud Rate | 9600 |
| Data Bits | 8 |
| Stop Bits | 1 |
| Parity | None |
| DTR | true |
| RTS | true |

커스텀 설정:

```kotlin
val config = SerialConfig(
    baudRate = 9600,
    dataBits = 8,
    stopBits = 1,
    parity = 0
)
gs805.connect(device, config)
```

## 4. 코루틴 사용

모든 I/O 메서드는 `suspend` 함수입니다. Activity에서 사용:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // lifecycleScope 사용
        lifecycleScope.launch {
            gs805.connect(device)
            gs805.makeDrink(DrinkNumber.HOT_DRINK_1)
        }

        // Flow 수집
        gs805.eventFlow
            .onEach { event -> handleEvent(event) }
            .launchIn(lifecycleScope)
    }
}
```

## 5. 자동 재연결 설정

```kotlin
val gs805 = GS805Serial(
    context = this,
    reconnectConfig = ReconnectConfig(
        strategy = ReconnectStrategy.EXPONENTIAL_BACKOFF,
        maxAttempts = 5,
        initialDelayMs = 500,
        maxDelayMs = 30000
    )
)

// 재연결 이벤트 관찰
gs805.reconnectEventFlow.collect { event ->
    when (event.state) {
        ReconnectState.CONNECTING -> log("재연결 시도 ${event.attempt}...")
        ReconnectState.CONNECTED -> log("재연결 성공!")
        ReconnectState.FAILED -> log("재연결 실패")
        else -> {}
    }
}
```

## 6. 명령 큐 사용

여러 명령을 순차적으로 실행해야 할 때:

```kotlin
val gs805 = GS805Serial(
    context = this,
    enableCommandQueue = true
)

// 명령이 자동으로 큐에 추가되어 순차 실행
gs805.makeDrink(DrinkNumber.HOT_DRINK_1)
gs805.makeDrink(DrinkNumber.HOT_DRINK_2)

// 큐 상태 관찰
gs805.queueEventFlow.collect { event ->
    log("Queue: ${event.type} - ${event.message}")
}
```

## 7. 로깅

```kotlin
val gs805 = GS805Serial(
    context = this,
    enableLogging = true
)

// 로그 레벨 설정
gs805.setLogLevel(LogLevel.DEBUG)

// 로그 스트림 관찰
gs805.logStream.collect { entry ->
    log("[${entry.level.tag}] ${entry.source}: ${entry.message}")
}

// 로그 내보내기
val logs = gs805.exportLogs(minLevel = LogLevel.WARNING)
```

## 8. 커스텀 음료 제조 (R시리즈 전용)

### 레시피 정의 후 실행 (0x1D)

머신의 음료 번호에 커스텀 레시피를 저장한 뒤 실행합니다.

```kotlin
val steps = listOf(
    RecipeStep.cupDispense(dispenser = 1),           // 컵 배출
    RecipeStep.instantChannel(                        // Powder1 + 온수
        channel = 0,
        waterType = WaterType.HOT,
        materialDuration = 1000,                      // 분말 (0.1초 단위)
        waterAmount = 2000,                           // 물 (0.1mL 단위)
        materialSpeed = 50,
        mixSpeed = 30,
    ),
    RecipeStep.instantChannel(                        // Powder2
        channel = 1,
        waterType = WaterType.HOT,
        materialDuration = 500,
        waterAmount = 0,
        materialSpeed = 50,
        mixSpeed = 30,
    ),
)

// 레시피 저장 후 실행
gs805.setDrinkRecipeProcess(DrinkNumber.HOT_DRINK_1, steps)
gs805.makeDrink(DrinkNumber.HOT_DRINK_1)
```

### 단일 채널 즉시 실행 (0x25)

레시피 저장 없이 채널을 직접 제어합니다.

```kotlin
gs805.testCupDrop()  // 컵 배출

gs805.executeChannel(
    channel = 0,
    waterType = WaterType.HOT,
    materialDuration = 1000,
    waterAmount = 2000,
    materialSpeed = 50,
    mixSpeed = 30,
)

gs805.executeChannel(
    channel = 1,
    waterType = WaterType.HOT,
    materialDuration = 500,
    waterAmount = 0,
    materialSpeed = 50,
    mixSpeed = 30,
)
```

### 사용 가능한 레시피 단계 유형

| 팩토리 메서드 | 설명 | 모델 |
|---|---|---|
| `RecipeStep.instantChannel(...)` | 인스턴트 채널 (분말+물) | 전 모델 |
| `RecipeStep.cupDispense(...)` | 컵 디스펜서 | 전 모델 |
| `RecipeStep.grinding(...)` | 신선한 커피 분쇄 | JK88 |
| `RecipeStep.iceMaking(...)` | 제빙 | JK86 |
| `RecipeStep.lidPlacement(...)` | 뚜껑 배치 | JK86 |
| `RecipeStep.lidPressing(...)` | 뚜껑 압착 | JK86 |
| `RecipeStep.independentMixing(...)` | 독립 교반 | GS801 |

> 분말량은 g이 아닌 **시간(0.1초)** 단위입니다. 자세한 내용은 [make_recipe.md](../make_recipe.md) 참조

## 9. MDB 카드리더 (별도 포트)

```kotlin
val mdb = MdbCashless(UsbSerialConnection(context))

// 별도의 USB 포트에 연결
mdb.connect(mdbDevice)
mdb.setup()
mdb.enable()

// 카드 감지 이벤트 대기
mdb.eventFlow.collect { event ->
    when (event.type) {
        CashlessEventType.CARD_DETECTED -> {
            val funds = event.availableFunds
            mdb.requestVend(price = 100, itemNumber = 1)
        }
        CashlessEventType.VEND_APPROVED -> {
            // 음료 제조 시작
            gs805.makeDrink(DrinkNumber.HOT_DRINK_1)
            mdb.vendSuccess()
            mdb.sessionComplete()
        }
        else -> {}
    }
}
```

## 10. ProGuard 설정

SDK의 `consumer-rules.pro`가 자동 적용됩니다. 추가 설정이 필요하면:

```proguard
-keep class kr.co.anyeats.gs805serial.** { *; }
-keep class com.hoho.android.usbserial.** { *; }
```

## 11. 빌드 및 테스트

```bash
# 라이브러리 빌드
./gradlew :gs805serial:build

# 예제 앱 빌드
./gradlew :example:assembleDebug

# Maven Local에 배포 (테스트)
./gradlew :gs805serial:publishToMavenLocal
```

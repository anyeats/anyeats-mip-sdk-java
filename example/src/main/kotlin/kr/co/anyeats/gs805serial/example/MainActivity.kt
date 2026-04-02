package kr.co.anyeats.gs805serial.example

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kr.co.anyeats.gs805serial.GS805Serial
import kr.co.anyeats.gs805serial.model.*
import kr.co.anyeats.gs805serial.serial.SerialDevice
import kr.co.anyeats.gs805serial.serial.UsbSerialConnection
import kr.co.anyeats.gs805serial.serial.UartSerialConnection
import kr.co.anyeats.gs805serial.mdb.MdbCashless
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var gs805: GS805Serial
    private lateinit var mdbCashless: MdbCashless
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var svLog: ScrollView

    private var devices: List<SerialDevice> = emptyList()
    private var mdbDevices: List<SerialDevice> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gs805 = GS805Serial(this, connection = UartSerialConnection(), enableLogging = true)
        mdbCashless = MdbCashless(UartSerialConnection())

        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        svLog = tvLog.parent as ScrollView

        setupConnectionButtons()
        setupDrinkButtons()
        setupTemperatureButtons()
        setupInfoButtons()
        setupMaintenanceButtons()
        setupExtendedButtons()
        setupRecipeButtons()
        setupMdbButtons()
        setupUtilButtons()
        observeEvents()
    }

    // ========== Connection ==========

    private fun setupConnectionButtons() {
        findViewById<Button>(R.id.btnListDevices).setOnClickListener {
            lifecycleScope.launch {
                try {
                    devices = gs805.listDevices()
                    appendLog("Found ${devices.size} device(s):")
                    devices.forEachIndexed { i, d ->
                        appendLog("  [$i] ${d.name} (VID:${d.vendorId?.toString(16)} PID:${d.productId?.toString(16)})")
                    }
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (devices.isEmpty()) {
                        devices = gs805.listDevices()
                    }
                    if (devices.isEmpty()) {
                        appendLog("No devices found")
                        return@launch
                    }
                    gs805.connect(devices.first())
                    appendLog("Connected to ${devices.first().name}")
                    updateStatus()
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.disconnect()
                    appendLog("Disconnected")
                    updateStatus()
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }
    }

    // ========== Drink ==========

    private fun setupDrinkButtons() {
        findViewById<Button>(R.id.btnMakeDrink).setOnClickListener {
            lifecycleScope.launch {
                try {
                    appendLog("Making Hot Drink 1...")
                    gs805.makeDrink(DrinkNumber.HOT_DRINK_1)
                    appendLog("Drink command sent successfully")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnSetDrinkPrice).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.setDrinkPrice(DrinkNumber.HOT_DRINK_1, 50)
                    appendLog("Hot Drink 1 price set to 50")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnGetSalesCount).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val sales = gs805.getSalesCount(DrinkNumber.HOT_DRINK_1)
                    appendLog("Sales Count: $sales")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }
    }

    // ========== Temperature ==========

    private fun setupTemperatureButtons() {
        findViewById<Button>(R.id.btnSetHotTemp).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.setHotTemperature(90, 70)
                    appendLog("Hot temperature set: 70-90°C")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnSetColdTemp).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.setColdTemperature(10, 5)
                    appendLog("Cold temperature set: 5-10°C")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }
    }

    // ========== Info ==========

    private fun setupInfoButtons() {
        findViewById<Button>(R.id.btnGetStatus).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val status = gs805.getMachineStatus()
                    appendLog("Machine Status: $status")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnGetError).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val errorInfo = gs805.getErrorInfo()
                    appendLog("Error: ${errorInfo.error}")
                    appendLog("Severity: ${errorInfo.severity}")
                    appendLog("Actions: ${errorInfo.recoveryActions.joinToString(", ")}")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnGetErrorCode).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val error = gs805.getErrorCode()
                    appendLog("Raw Error Code: $error")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnGetBalance).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val balance = gs805.getBalance()
                    appendLog("Balance: $balance")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }
    }

    // ========== Maintenance ==========

    private fun setupMaintenanceButtons() {
        findViewById<Button>(R.id.btnSetCupDropMode).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.setCupDropMode(CupDropModeEnum.AUTOMATIC)
                    appendLog("Cup drop mode set to AUTOMATIC")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnTestCupDrop).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.testCupDrop()
                    appendLog("Cup drop test executed")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnAutoInspection).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.autoInspection()
                    appendLog("Auto inspection started")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnCleanAllPipes).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.cleanAllPipes()
                    appendLog("Clean all pipes started")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnCleanSpecificPipe).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.cleanSpecificPipe(1)
                    appendLog("Clean pipe 1 started")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnReturnChange).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val status = gs805.returnChange()
                    appendLog("Return change: $status")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }
    }

    // ========== Extended (Series 3/R) ==========

    private fun setupExtendedButtons() {
        findViewById<Button>(R.id.btnUnitFunctionTest).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.unitFunctionTest(testCmd = 1, data1 = 0, data2 = 0, data3 = 0)
                    appendLog("Unit function test (dispensing) sent")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnLockDoor).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val status = gs805.lockDoor(lockNumber = 1)
                    appendLog("Lock door: $status")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnUnlockDoor).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val status = gs805.unlockDoor(lockNumber = 1)
                    appendLog("Unlock door: $status")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnGetLockStatus).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val status = gs805.getLockStatus(lockNumber = 1)
                    appendLog("Lock status: $status")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnWaterRefill).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.waterRefill()
                    appendLog("Water refill command sent")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnGetControllerStatus).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val status = gs805.getControllerStatus()
                    appendLog("Controller: $status")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnGetDrinkStatus).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val status = gs805.getDrinkStatus()
                    appendLog("Drink Status: $status")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnGetObjectException).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val info = gs805.getObjectException(ObjectType.WATER_PUMP)
                    appendLog("Object Exception: $info")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnForceStopDrink).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.forceStopDrinkProcess()
                    appendLog("Force stop drink process sent")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnForceStopCup).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.forceStopCupDelivery()
                    appendLog("Force stop cup delivery sent")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnCupDelivery).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.cupDelivery(waitTimeSeconds = 30)
                    appendLog("Cup delivery command sent (30s wait)")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }
    }

    // ========== Recipe (R-Series) ==========

    private fun setupRecipeButtons() {
        findViewById<Button>(R.id.btnSetRecipe).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val steps = listOf(
                        RecipeStep.cupDispense(dispenser = 1),
                        RecipeStep.instantChannel(
                            channel = 0,
                            waterType = WaterType.HOT,
                            materialDuration = 30,
                            waterAmount = 200,
                            materialSpeed = 50
                        )
                    )
                    gs805.setDrinkRecipeProcess(DrinkNumber.HOT_DRINK_1, steps)
                    appendLog("Recipe set for Hot Drink 1 (2 steps: cup + instant)")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnExecuteChannel).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.executeChannel(
                        channel = 0,
                        waterType = WaterType.HOT,
                        materialDuration = 1000,
                        waterAmount = 2000,
                        materialSpeed = 50
                    )
                    appendLog("Execute channel 0 sent")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }
    }

    // ========== MDB Payment ==========

    private fun setupMdbButtons() {
        findViewById<Button>(R.id.btnMdbListDevices).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbDevices = mdbCashless.listDevices()
                    appendLog("MDB: Found ${mdbDevices.size} device(s):")
                    mdbDevices.forEachIndexed { i, d ->
                        appendLog("  [$i] ${d.name} (VID:${d.vendorId?.toString(16)} PID:${d.productId?.toString(16)})")
                    }
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbConnect).setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (mdbDevices.isEmpty()) {
                        mdbDevices = mdbCashless.listDevices()
                    }
                    if (mdbDevices.isEmpty()) {
                        appendLog("MDB: No devices found")
                        return@launch
                    }
                    mdbCashless.connect(mdbDevices.first())
                    appendLog("MDB: Connected to ${mdbDevices.first().name}")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbDisconnect).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbCashless.disconnect()
                    appendLog("MDB: Disconnected")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbSetup).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbCashless.setup()
                    appendLog("MDB: Setup completed")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbEnable).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbCashless.enable()
                    appendLog("MDB: Enabled")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbDisable).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbCashless.disable()
                    appendLog("MDB: Disabled")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbRequestVend).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbCashless.requestVend(price = 100, itemNumber = 1)
                    appendLog("MDB: Vend requested (price=100, item=1)")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbVendSuccess).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbCashless.vendSuccess(itemNumber = 1)
                    appendLog("MDB: Vend success sent (item=1)")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbVendCancel).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbCashless.vendCancel()
                    appendLog("MDB: Vend cancelled")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbSessionComplete).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbCashless.sessionComplete()
                    appendLog("MDB: Session completed")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbRequestId).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbCashless.requestId()
                    appendLog("MDB: Request ID sent")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnMdbCashSale).setOnClickListener {
            lifecycleScope.launch {
                try {
                    mdbCashless.cashSale(price = 100, itemNumber = 1)
                    appendLog("MDB: Cash sale sent (price=100, item=1)")
                } catch (e: Exception) {
                    appendLog("MDB ERROR: ${e.message}")
                }
            }
        }
    }

    // ========== Utility ==========

    private fun setupUtilButtons() {
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            tvLog.text = ""
        }
    }

    // ========== Events ==========

    private fun observeEvents() {
        gs805.connectionStateFlow
            .onEach { updateStatus() }
            .launchIn(lifecycleScope)

        gs805.eventFlow
            .onEach { event -> appendLog("EVENT: $event") }
            .launchIn(lifecycleScope)

        gs805.messageFlow
            .onEach { msg -> appendLog("MSG: $msg") }
            .launchIn(lifecycleScope)

        mdbCashless.eventFlow
            .onEach { event -> appendLog("MDB EVENT: $event") }
            .launchIn(lifecycleScope)

        mdbCashless.connectionStateFlow
            .onEach { connected -> appendLog("MDB Connection: $connected") }
            .launchIn(lifecycleScope)
    }

    private fun updateStatus() {
        runOnUiThread {
            tvStatus.text = if (gs805.isConnected) {
                getString(R.string.status_connected, gs805.connectedDevice?.name ?: "Unknown")
            } else {
                getString(R.string.status_disconnected)
            }
        }
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = timeFormat.format(Date())
            tvLog.append("[$timestamp] $message\n")
            svLog.post { svLog.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            gs805.dispose()
            mdbCashless.dispose()
        }
    }
}

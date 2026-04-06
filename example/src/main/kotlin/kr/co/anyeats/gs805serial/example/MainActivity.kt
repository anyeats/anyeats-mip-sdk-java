package kr.co.anyeats.gs805serial.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.anyeats.gs805serial.GS805Serial
import kr.co.anyeats.gs805serial.model.*
import kr.co.anyeats.gs805serial.serial.SerialDevice
import kr.co.anyeats.gs805serial.serial.UartSerialConnection
import kr.co.anyeats.gs805serial.mdb.MdbCashless
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var gs805: GS805Serial
    private lateinit var mdbCashless: MdbCashless

    // Connection bar
    private lateinit var spinnerDevices: Spinner
    private lateinit var btnRefreshDevices: Button
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var tvStatus: TextView

    // Tabs
    private lateinit var tabCommands: Button
    private lateinit var tabDebug: Button
    private lateinit var tabSystem: Button

    // Panels
    private lateinit var panelCommands: ScrollView
    private lateinit var panelDebug: ScrollView
    private lateinit var panelSystem: ScrollView

    // Log
    private lateinit var tvLog: TextView
    private lateinit var svLog: ScrollView

    // Debug
    private lateinit var etShellCommand: EditText
    private lateinit var tvDebugOutput: TextView
    private val debugLog = StringBuilder()

    // System
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnUpdateNow: Button
    private lateinit var progressUpdate: ProgressBar
    private lateinit var tvSystemLog: TextView

    private var devices: List<SerialDevice> = emptyList()
    private var mdbDevices: List<SerialDevice> = emptyList()
    private var selectedDeviceIndex = 0
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private var updateAvailable = false
    private var updateApkLink: String = "app-debug.apk"

    companion object {
        private const val UPDATE_SERVER_URL = "http://192.168.0.140:8000"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gs805 = GS805Serial(this, connection = UartSerialConnection(), enableLogging = true)
        mdbCashless = MdbCashless(UartSerialConnection())

        initViews()
        setupConnectionBar()
        setupTabs()
        setupChannelButtons()
        setupRecipeMenuButtons()
        setupDrinkButtons()
        setupTemperatureButtons()
        setupInfoButtons()
        setupMaintenanceButtons()
        setupExtendedButtons()
        setupRecipeButtons()
        setupMdbButtons()
        setupDebugPanel()
        setupSystemPanel()
        setupLogButtons()
        loadAppVersion()
        observeEvents()

        // Load devices on start
        refreshDevices()
    }

    // ========== Init ==========

    private fun initViews() {
        spinnerDevices = findViewById(R.id.spinnerDevices)
        btnRefreshDevices = findViewById(R.id.btnRefreshDevices)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        tvStatus = findViewById(R.id.tvStatus)

        tabCommands = findViewById(R.id.tabCommands)
        tabDebug = findViewById(R.id.tabDebug)
        tabSystem = findViewById(R.id.tabSystem)

        panelCommands = findViewById(R.id.panelCommands)
        panelDebug = findViewById(R.id.panelDebug)
        panelSystem = findViewById(R.id.panelSystem)

        tvLog = findViewById(R.id.tvLog)
        svLog = tvLog.parent as ScrollView

        etShellCommand = findViewById(R.id.etShellCommand)
        tvDebugOutput = findViewById(R.id.tvDebugOutput)

        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnUpdateNow = findViewById(R.id.btnUpdateNow)
        progressUpdate = findViewById(R.id.progressUpdate)
        tvSystemLog = findViewById(R.id.tvSystemLog)
    }

    // ========== Connection Bar ==========

    private fun setupConnectionBar() {
        btnRefreshDevices.setOnClickListener { refreshDevices() }

        btnConnect.setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (devices.isEmpty()) {
                        devices = gs805.listDevices()
                    }
                    if (devices.isEmpty()) {
                        appendLog("No devices found")
                        return@launch
                    }
                    val device = devices[selectedDeviceIndex]
                    gs805.connect(device)
                    appendLog("Connected to ${device.name}")
                    updateConnectionUI()
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        btnDisconnect.setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.disconnect()
                    appendLog("Disconnected")
                    updateConnectionUI()
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        spinnerDevices.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDeviceIndex = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun refreshDevices() {
        lifecycleScope.launch {
            try {
                devices = gs805.listDevices()
                val names = devices.map { "${it.name} (${it.vendorId?.toString(16) ?: "uart"})" }
                val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerDevices.adapter = adapter
                // Default to ttyS7
                val ttyS7Index = devices.indexOfFirst { it.name.contains("ttyS7") }
                if (ttyS7Index >= 0) {
                    spinnerDevices.setSelection(ttyS7Index)
                    selectedDeviceIndex = ttyS7Index
                }
                appendLog("Found ${devices.size} device(s)")
            } catch (e: Exception) {
                appendLog("ERROR listing devices: ${e.message}")
            }
        }
    }

    private fun updateConnectionUI() {
        runOnUiThread {
            if (gs805.isConnected) {
                btnConnect.visibility = View.GONE
                btnDisconnect.visibility = View.VISIBLE
                btnRefreshDevices.isEnabled = false
                spinnerDevices.isEnabled = false
                tvStatus.text = getString(R.string.status_connected, gs805.connectedDevice?.name ?: "Unknown")
                tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                findViewById<LinearLayout>(R.id.connectionBar).setBackgroundColor(Color.parseColor("#E8F5E9"))
            } else {
                btnConnect.visibility = View.VISIBLE
                btnDisconnect.visibility = View.GONE
                btnRefreshDevices.isEnabled = true
                spinnerDevices.isEnabled = true
                tvStatus.text = getString(R.string.status_disconnected)
                tvStatus.setTextColor(Color.parseColor("#757575"))
                findViewById<LinearLayout>(R.id.connectionBar).setBackgroundColor(Color.parseColor("#E3F2FD"))
            }
        }
    }

    // ========== Tabs ==========

    private fun setupTabs() {
        selectTab(0) // Commands by default

        tabCommands.setOnClickListener { selectTab(0) }
        tabDebug.setOnClickListener { selectTab(1) }
        tabSystem.setOnClickListener { selectTab(2) }
    }

    private fun selectTab(index: Int) {
        val tabs = listOf(tabCommands, tabDebug, tabSystem)
        val panels = listOf(panelCommands, panelDebug, panelSystem)

        tabs.forEachIndexed { i, tab ->
            if (i == index) {
                tab.setBackgroundColor(Color.parseColor("#1976D2"))
                tab.setTextColor(Color.WHITE)
                tab.typeface = Typeface.DEFAULT_BOLD
            } else {
                tab.setBackgroundColor(Color.TRANSPARENT)
                tab.setTextColor(Color.parseColor("#1976D2"))
                tab.typeface = Typeface.DEFAULT
            }
        }

        panels.forEachIndexed { i, panel ->
            panel.visibility = if (i == index) View.VISIBLE else View.GONE
        }
    }

    // ========== Channel (0x25) ==========

    private fun setupChannelButtons() {
        // Hot Make 1: ch0 only
        findViewById<Button>(R.id.btnChHotMake1).setOnClickListener {
            lifecycleScope.launch {
                try {
                    appendLog("Channel: Hot Make 1 (ch0)")
                    gs805.executeChannel(channel = 0, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 0)
                    appendLog("Channel: Hot Make 1 done")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        // Hot Make 1&2: ch0 then ch1
        findViewById<Button>(R.id.btnChHotMake12).setOnClickListener {
            lifecycleScope.launch {
                try {
                    appendLog("Channel: Hot Make 1&2 (ch0->ch1)")
                    gs805.executeChannel(channel = 0, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 0, materialSpeed = 50, mixSpeed = 0)
                    gs805.executeChannel(channel = 1, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 100)
                    appendLog("Channel: Hot Make 1&2 done")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        // Hot Make 3&2: ch2 then ch1
        findViewById<Button>(R.id.btnChHotMake32).setOnClickListener {
            lifecycleScope.launch {
                try {
                    appendLog("Channel: Hot Make 3&2 (ch2->ch1)")
                    gs805.executeChannel(channel = 2, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 0, materialSpeed = 50, mixSpeed = 0)
                    gs805.executeChannel(channel = 1, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 100)
                    appendLog("Channel: Hot Make 3&2 done")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        // Cold Make 1: ch0 only
        findViewById<Button>(R.id.btnChColdMake1).setOnClickListener {
            lifecycleScope.launch {
                try {
                    appendLog("Channel: Cold Make 1 (ch0)")
                    gs805.executeChannel(channel = 0, waterType = WaterType.COLD, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 0)
                    appendLog("Channel: Cold Make 1 done")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        // Cold Make 1&2: ch0 then ch1
        findViewById<Button>(R.id.btnChColdMake12).setOnClickListener {
            lifecycleScope.launch {
                try {
                    appendLog("Channel: Cold Make 1&2 (ch0->ch1)")
                    gs805.executeChannel(channel = 0, waterType = WaterType.COLD, materialDuration = 10, waterAmount = 0, materialSpeed = 50, mixSpeed = 0)
                    gs805.executeChannel(channel = 1, waterType = WaterType.COLD, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 100)
                    appendLog("Channel: Cold Make 1&2 done")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        // Cold Make 3&2: ch2 then ch1
        findViewById<Button>(R.id.btnChColdMake32).setOnClickListener {
            lifecycleScope.launch {
                try {
                    appendLog("Channel: Cold Make 3&2 (ch2->ch1)")
                    gs805.executeChannel(channel = 2, waterType = WaterType.COLD, materialDuration = 10, waterAmount = 0, materialSpeed = 50, mixSpeed = 0)
                    gs805.executeChannel(channel = 1, waterType = WaterType.COLD, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 100)
                    appendLog("Channel: Cold Make 3&2 done")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }
    }

    // ========== Recipe Menu (0x1D) ==========

    /** Clear recipe, set new steps, then make drink */
    private fun setRecipeAndMake(label: String, drink: DrinkNumber, steps: List<RecipeStep>) {
        lifecycleScope.launch {
            try {
                appendLog("Recipe: $label - Clear...")
                gs805.setDrinkRecipeProcess(drink, listOf(RecipeStep.clear()))
                appendLog("Recipe: $label - Set ${steps.size} step(s)...")
                gs805.setDrinkRecipeProcess(drink, steps)
                appendLog("Recipe: $label - Make...")
                gs805.makeDrink(drink)
                appendLog("Recipe: $label done")
            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
            }
        }
    }

    private fun setupRecipeMenuButtons() {
        // Hot Make 1: recipe with ch0 only
        findViewById<Button>(R.id.btnRecHotMake1).setOnClickListener {
            setRecipeAndMake("Hot Make 1 (ch0)", DrinkNumber.HOT_DRINK_1, listOf(
                RecipeStep.instantChannel(channel = 0, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 0)
            ))
        }

        // Hot Make 1&2: recipe with ch0, ch1
        findViewById<Button>(R.id.btnRecHotMake12).setOnClickListener {
            setRecipeAndMake("Hot Make 1&2 (ch0->ch1)", DrinkNumber.HOT_DRINK_1, listOf(
                RecipeStep.instantChannel(channel = 0, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 0, materialSpeed = 50, mixSpeed = 0),
                RecipeStep.instantChannel(channel = 1, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 100)
            ))
        }

        // Hot Make 3&2: recipe with ch2, ch1
        findViewById<Button>(R.id.btnRecHotMake32).setOnClickListener {
            setRecipeAndMake("Hot Make 3&2 (ch2->ch1)", DrinkNumber.HOT_DRINK_1, listOf(
                RecipeStep.instantChannel(channel = 2, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 0, materialSpeed = 50, mixSpeed = 0),
                RecipeStep.instantChannel(channel = 1, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 100)
            ))
        }

        // Cold Make 1: recipe with ch0 only
        findViewById<Button>(R.id.btnRecColdMake1).setOnClickListener {
            setRecipeAndMake("Cold Make 1 (ch0)", DrinkNumber.HOT_DRINK_1, listOf(
                RecipeStep.instantChannel(channel = 0, waterType = WaterType.COLD, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 0)
            ))
        }

        // Cold Make 1&2: recipe with ch0, ch1
        findViewById<Button>(R.id.btnRecColdMake12).setOnClickListener {
            setRecipeAndMake("Cold Make 1&2 (ch0->ch1)", DrinkNumber.HOT_DRINK_1, listOf(
                RecipeStep.instantChannel(channel = 0, waterType = WaterType.COLD, materialDuration = 10, waterAmount = 0, materialSpeed = 50, mixSpeed = 0),
                RecipeStep.instantChannel(channel = 1, waterType = WaterType.COLD, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 100)
            ))
        }

        // Cold Make 3&2: recipe with ch2, ch1
        findViewById<Button>(R.id.btnRecColdMake32).setOnClickListener {
            setRecipeAndMake("Cold Make 3&2 (ch2->ch1)", DrinkNumber.HOT_DRINK_1, listOf(
                RecipeStep.instantChannel(channel = 2, waterType = WaterType.COLD, materialDuration = 10, waterAmount = 0, materialSpeed = 50, mixSpeed = 0),
                RecipeStep.instantChannel(channel = 1, waterType = WaterType.COLD, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 100)
            ))
        }

        // Clear hotDrink1: send clear (NONE) step
        findViewById<Button>(R.id.btnRecClearHot1).setOnClickListener {
            lifecycleScope.launch {
                try {
                    appendLog("Recipe: Clear HOT_DRINK_1...")
                    gs805.setDrinkRecipeProcess(DrinkNumber.HOT_DRINK_1, listOf(RecipeStep.clear()))
                    appendLog("Recipe: Clear HOT_DRINK_1 done")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        // Clear -> Set -> Make: full flow test
        findViewById<Button>(R.id.btnRecClearSetMake).setOnClickListener {
            setRecipeAndMake("Clear->Set->Make", DrinkNumber.HOT_DRINK_1, listOf(
                RecipeStep.instantChannel(channel = 0, waterType = WaterType.HOT, materialDuration = 10, waterAmount = 2000, materialSpeed = 50, mixSpeed = 0)
            ))
        }

        // Clear then Make (verify): clear then immediately makeDrink - expect 0x6 error if clear worked
        findViewById<Button>(R.id.btnRecClearMakeVerify).setOnClickListener {
            lifecycleScope.launch {
                try {
                    appendLog("Recipe: Clear then Make (verify)...")
                    gs805.setDrinkRecipeProcess(DrinkNumber.HOT_DRINK_1, listOf(RecipeStep.clear()))
                    appendLog("Recipe: Clear done, now makeDrink (expect 0x6 error if clear worked)...")
                    gs805.makeDrink(DrinkNumber.HOT_DRINK_1)
                    appendLog("Recipe: makeDrink returned OK (recipe was NOT cleared?)")
                } catch (e: Exception) {
                    appendLog("Recipe verify: ${e.message} (0x6 = clear worked)")
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
                    gs805.setHotTemperature(65, 60)
                    appendLog("Hot temperature set: 60-65C")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnSetColdTemp).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.setColdTemperature(10, 5)
                    appendLog("Cold temperature set: 5-10C")
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
        // Pickup Door (cup delivery)
        findViewById<Button>(R.id.btnDoorOpen30).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.cupDelivery(30)
                    appendLog("Door opened (30s wait)")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnDoorOpen60).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.cupDelivery(60)
                    appendLog("Door opened (60s wait)")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

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

        // Front Door Open
        findViewById<Button>(R.id.btnLockDoor).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.unitFunctionTest(3, 1, 0, 0)
                    appendLog("Door test: open (3,1,0,0)")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        // Front Door Close
        findViewById<Button>(R.id.btnUnlockDoor).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.unitFunctionTest(3, 0, 0, 0)
                    appendLog("Door test: close (3,0,0,0)")
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message}")
                }
            }
        }

        // Front Door Test
        findViewById<Button>(R.id.btnGetLockStatusMaint).setOnClickListener {
            lifecycleScope.launch {
                try {
                    gs805.unitFunctionTest(3, 2, 0, 0)
                    appendLog("Door test: (3,2,0,0)")
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

    // ========== Debug Panel ==========

    private fun setupDebugPanel() {
        val presetCommands = mapOf(
            R.id.btnPreset1 to "su 0 am force-stop com.yj.coffeemachines",
            R.id.btnPreset2 to "su 0 sh -c \"timeout 15 cat /dev/ttyS7 > /data/local/tmp/ttyS7.bin &\"",
            R.id.btnPreset3 to "su 0 am start -n com.yj.coffeemachines/.MainActivity",
            R.id.btnPreset4 to "su 0 xxd /data/local/tmp/ttyS7.bin",
            R.id.btnPreset5 to "su 0 sh -c \"echo AA55020B0C | xxd -r -p > /dev/ttyS7 & timeout 1 cat /dev/ttyS7 | xxd\"",
            R.id.btnPreset6 to "su 0 sh -c \"echo AA55121D01010D010000320032 0000FF00000000A1 | xxd -r -p > /dev/ttyS7 & timeout 1 cat /dev/ttyS7 | xxd\""
        )

        presetCommands.forEach { (id, cmd) ->
            findViewById<Button>(id).setOnClickListener {
                etShellCommand.setText(cmd)
                runShellCommand(cmd)
            }
        }

        findViewById<Button>(R.id.btnRunShell).setOnClickListener {
            val cmd = etShellCommand.text.toString().trim()
            if (cmd.isNotEmpty()) {
                runShellCommand(cmd)
            }
        }

        findViewById<Button>(R.id.btnCopyDebug).setOnClickListener {
            copyToClipboard(debugLog.toString())
            showToast("Debug output copied")
        }

        findViewById<Button>(R.id.btnClearDebug).setOnClickListener {
            debugLog.clear()
            tvDebugOutput.text = ""
        }
    }

    private fun runShellCommand(command: String) {
        lifecycleScope.launch {
            appendDebug("$ $command")
            try {
                val result = withContext(Dispatchers.IO) {
                    executeShellCommand(command)
                }
                appendDebug(result)
            } catch (e: Exception) {
                appendDebug("ERROR: ${e.message}")
            }
        }
    }

    private fun executeShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            process.waitFor()

            buildString {
                if (stdout.isNotEmpty()) append(stdout)
                if (stderr.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("[err] $stderr")
                }
                if (isEmpty()) append("(no output)")
            }
        } catch (e: Exception) {
            "EXEC ERROR: ${e.message}"
        }
    }

    private fun appendDebug(text: String) {
        runOnUiThread {
            debugLog.append(text).append("\n")
            tvDebugOutput.text = debugLog.toString()
        }
    }

    // ========== System Panel ==========

    private fun setupSystemPanel() {
        btnCheckUpdate.setOnClickListener { checkForUpdate() }
        btnUpdateNow.setOnClickListener { downloadAndInstall() }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            progressUpdate.visibility = View.VISIBLE
            btnCheckUpdate.isEnabled = false
            tvSystemLog.text = "Checking for update..."

            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URL("$UPDATE_SERVER_URL/")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"
                    try {
                        val body = conn.inputStream.bufferedReader().readText()
                        body
                    } finally {
                        conn.disconnect()
                    }
                }

                if (result.contains("app-debug.apk")) {
                    updateAvailable = true
                    // Extract href link if present
                    val hrefRegex = Regex("""href="([^"]*app-debug\.apk[^"]*)")""")
                    val match = hrefRegex.find(result)
                    updateApkLink = match?.groupValues?.get(1) ?: "app-debug.apk"
                    btnUpdateNow.visibility = View.VISIBLE
                    tvSystemLog.text = "Update available: $updateApkLink"
                } else {
                    updateAvailable = false
                    btnUpdateNow.visibility = View.GONE
                    tvSystemLog.text = "No update found on server\n\nResponse:\n$result"
                }
            } catch (e: Exception) {
                updateAvailable = false
                btnUpdateNow.visibility = View.GONE
                tvSystemLog.text = "Server unreachable: ${e.message}"
            } finally {
                progressUpdate.visibility = View.GONE
                btnCheckUpdate.isEnabled = true
            }
        }
    }

    private fun downloadAndInstall() {
        lifecycleScope.launch {
            progressUpdate.visibility = View.VISIBLE
            btnUpdateNow.isEnabled = false
            tvSystemLog.text = "Downloading APK..."

            try {
                // Download using HttpURLConnection
                val downloadPath = "/sdcard/Download/update.apk"
                withContext(Dispatchers.IO) {
                    val url = URL("$UPDATE_SERVER_URL/$updateApkLink")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 30000
                    conn.requestMethod = "GET"
                    try {
                        val inputStream = conn.inputStream
                        val file = File(downloadPath)
                        FileOutputStream(file).use { fos ->
                            inputStream.copyTo(fos)
                        }
                    } finally {
                        conn.disconnect()
                    }
                }

                tvSystemLog.text = "Download complete. Ready to install."

                // Show dialog before install
                runOnUiThread {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Update Downloaded")
                        .setMessage("Install and restart now?\n\nThe app will close and reopen automatically.")
                        .setNegativeButton("Later") { dialog, _ -> dialog.dismiss() }
                        .setPositiveButton("Install & Restart") { _, _ ->
                            lifecycleScope.launch {
                                tvSystemLog.text = "Installing..."
                                // 1. Write restart script
                                withContext(Dispatchers.IO) {
                                    executeShellCommand("su 0 sh -c \"echo '#!/system/bin/sh\nsleep 10\nam start -n kr.co.anyeats.gs805serial.example/.MainActivity' > /data/local/tmp/restart.sh && chmod 755 /data/local/tmp/restart.sh\"")
                                }
                                // 2. Launch as independent daemon
                                withContext(Dispatchers.IO) {
                                    executeShellCommand("su 0 setsid sh /data/local/tmp/restart.sh < /dev/null > /dev/null 2>&1 &")
                                }
                                kotlinx.coroutines.delay(1000)
                                // 3. Install (kills the app)
                                withContext(Dispatchers.IO) {
                                    executeShellCommand("su 0 pm install -r $downloadPath 2>&1")
                                }
                            }
                        }
                        .setCancelable(false)
                        .show()
                }
            } catch (e: Exception) {
                tvSystemLog.text = "Update failed: ${e.message}"
            } finally {
                progressUpdate.visibility = View.GONE
                btnUpdateNow.isEnabled = true
            }
        }
    }

    // ========== Log ==========

    private fun setupLogButtons() {
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            tvLog.text = ""
        }

        findViewById<Button>(R.id.btnCopyLog).setOnClickListener {
            copyToClipboard(tvLog.text.toString())
            showToast("Log copied")
        }

        // Long-click on log to copy
        tvLog.setOnLongClickListener {
            copyToClipboard(tvLog.text.toString())
            showToast("Log copied")
            true
        }
    }

    // ========== Version ==========

    private fun loadAppVersion() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = pInfo.versionName ?: ""
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            title = "GS805 Coffee Machine  v$versionName ($versionCode)"
        } catch (_: Exception) {
            title = "GS805 Coffee Machine"
        }
    }

    // ========== Events ==========

    private fun observeEvents() {
        gs805.connectionStateFlow
            .onEach { updateConnectionUI() }
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

    // ========== Utilities ==========

    private fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = timeFormat.format(Date())
            tvLog.append("[$timestamp] $message\n")
            svLog.post { svLog.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("GS805 Log", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            gs805.dispose()
            mdbCashless.dispose()
        }
    }
}

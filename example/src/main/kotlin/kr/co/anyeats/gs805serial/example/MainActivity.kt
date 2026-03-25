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
import kr.co.anyeats.gs805serial.model.DrinkNumber
import kr.co.anyeats.gs805serial.serial.SerialDevice
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var gs805: GS805Serial
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var svLog: ScrollView

    private var devices: List<SerialDevice> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gs805 = GS805Serial(this, enableLogging = true)

        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        svLog = tvLog.parent as ScrollView

        setupButtons()
        observeEvents()
    }

    private fun setupButtons() {
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

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            tvLog.text = ""
        }
    }

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
        lifecycleScope.launch { gs805.dispose() }
    }
}

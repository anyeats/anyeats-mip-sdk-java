package kr.co.anyeats.gs805serial.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kr.co.anyeats.gs805serial.exception.ConnectionException
import kr.co.anyeats.gs805serial.exception.NotConnectedException
import kr.co.anyeats.gs805serial.exception.SerialPortException
import java.util.concurrent.Executors

/** USB Serial connection implementation using usb-serial-for-android */
class UsbSerialConnection(private val context: Context) : SerialConnection {

    private var _port: UsbSerialPort? = null
    private var _ioManager: SerialInputOutputManager? = null
    private var _device: SerialDevice? = null
    private var _config: SerialConfig? = null

    private val _inputFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val _connectionStateFlow = MutableStateFlow(false)

    companion object {
        private const val ACTION_USB_PERMISSION = "kr.co.anyeats.gs805serial.USB_PERMISSION"
    }

    override val inputFlow: Flow<ByteArray> = _inputFlow.asSharedFlow()
    override val connectionStateFlow: Flow<Boolean> = _connectionStateFlow.asStateFlow()

    override suspend fun listDevices(): List<SerialDevice> = withContext(Dispatchers.IO) {
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            availableDrivers.map { driver ->
                val usbDevice = driver.device
                SerialDevice(
                    id = usbDevice.deviceId.toString(),
                    name = usbDevice.productName ?: "USB Serial Device",
                    vendorId = usbDevice.vendorId,
                    productId = usbDevice.productId,
                    metadata = mapOf(
                        "manufacturer" to usbDevice.manufacturerName,
                        "serialNumber" to usbDevice.serialNumber,
                        "deviceName" to usbDevice.deviceName
                    )
                )
            }
        } catch (e: Exception) {
            throw SerialPortException("Failed to list USB devices", cause = e)
        }
    }

    override suspend fun connect(device: SerialDevice, config: SerialConfig?) = withContext(Dispatchers.IO) {
        if (isConnected) throw ConnectionException("Already connected to a device")

        val serialConfig = config ?: SerialConfig.GS805

        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val driver = availableDrivers.firstOrNull { it.device.deviceId.toString() == device.id }
                ?: throw ConnectionException("Device not found: ${device.name}", portName = device.id)

            val usbDevice = driver.device

            // Request USB permission if not granted
            if (!usbManager.hasPermission(usbDevice)) {
                requestPermission(usbManager, usbDevice)
            }

            val connection = usbManager.openDevice(usbDevice)
                ?: throw ConnectionException("Failed to open USB device: ${device.name}", portName = device.id)

            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(serialConfig.baudRate, serialConfig.dataBits, serialConfig.stopBits, serialConfig.parity)
            port.dtr = true
            port.rts = true

            _port = port
            _device = device
            _config = serialConfig

            // Start I/O manager for reading
            val ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    _inputFlow.tryEmit(data.copyOf())
                }

                override fun onRunError(e: Exception) {
                    _connectionStateFlow.tryEmit(false)
                    cleanup()
                }
            })
            _ioManager = ioManager
            Executors.newSingleThreadExecutor().submit(ioManager)

            _connectionStateFlow.emit(true)
        } catch (e: Exception) {
            cleanup()
            if (e is kr.co.anyeats.gs805serial.exception.GS805Exception) throw e
            throw ConnectionException("Failed to connect to ${device.name}", portName = device.id, cause = e)
        }
    }

    private suspend fun requestPermission(usbManager: UsbManager, usbDevice: UsbDevice) {
        val deferred = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    deferred.complete(granted)
                    context.unregisterReceiver(this)
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        usbManager.requestPermission(usbDevice, permissionIntent)

        val granted = deferred.await()
        if (!granted) {
            throw ConnectionException("USB permission denied")
        }
    }

    override suspend fun disconnect() {
        if (!isConnected) return
        try {
            _ioManager?.listener = null
            _ioManager?.stop()
            _ioManager = null
            _port?.close()
            _port = null
            _device = null
            _config = null
            _connectionStateFlow.emit(false)
        } catch (e: Exception) {
            throw SerialPortException("Error disconnecting from device", cause = e)
        }
    }

    private fun cleanup() {
        try {
            _ioManager?.listener = null
            _ioManager?.stop()
        } catch (_: Exception) {}
        try {
            _port?.close()
        } catch (_: Exception) {}
        _ioManager = null
        _port = null
        _device = null
        _config = null
    }

    override val isConnected: Boolean get() = _port != null && _device != null

    override val connectedDevice: SerialDevice? get() = _device

    override val currentConfig: SerialConfig? get() = _config

    override suspend fun write(data: ByteArray): Int = withContext(Dispatchers.IO) {
        val port = _port ?: throw NotConnectedException("Cannot write: not connected to device")
        try {
            port.write(data, 1000)
            data.size
        } catch (e: Exception) {
            throw SerialPortException("Error writing to USB port", portName = _device?.id, cause = e)
        }
    }

    override suspend fun dispose() {
        disconnect()
    }
}

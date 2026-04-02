package kr.co.anyeats.gs805serial.serial

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.anyeats.gs805serial.exception.ConnectionException
import kr.co.anyeats.gs805serial.exception.NotConnectedException
import kr.co.anyeats.gs805serial.exception.SerialPortException
import java.io.File

/**
 * UART serial connection implementation for embedded Android devices.
 *
 * Opens hardware UART ports (e.g., /dev/ttyS*, /dev/ttyHS*, /dev/ttyMT*,
 * /dev/ttyAMA*) directly using JNI and termios.
 *
 * This is intended for embedded Android devices that have physical UART
 * ports exposed as device files, unlike USB serial devices which require
 * the USB host API.
 */
class UartSerialConnection : SerialConnection {

    private var _serialPort: SerialPort? = null
    private var _device: SerialDevice? = null
    private var _config: SerialConfig? = null
    private var _readerJob: Job? = null
    private var _scope: CoroutineScope? = null

    private val _inputFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val _connectionStateFlow = MutableStateFlow(false)

    override val inputFlow: Flow<ByteArray> = _inputFlow.asSharedFlow()
    override val connectionStateFlow: Flow<Boolean> = _connectionStateFlow.asStateFlow()

    override val isConnected: Boolean get() = _serialPort?.isOpen == true
    override val connectedDevice: SerialDevice? get() = _device
    override val currentConfig: SerialConfig? get() = _config

    companion object {
        /** UART device path patterns to scan for available ports */
        private val UART_PATTERNS = listOf(
            "/dev/ttyS",
            "/dev/ttyHS",
            "/dev/ttyMT",
            "/dev/ttyAMA"
        )
        private const val READ_BUFFER_SIZE = 64
    }

    override suspend fun listDevices(): List<SerialDevice> = withContext(Dispatchers.IO) {
        try {
            val devices = mutableListOf<SerialDevice>()

            val patterns = listOf(
                Pair("/dev/ttyS", 0..31),
                Pair("/dev/ttyHS", 0..15),
                Pair("/dev/ttyMT", 0..15),
                Pair("/dev/ttyAMA", 0..15),
                Pair("/dev/ttyUSB", 0..15),
                Pair("/dev/ttyACM", 0..15),
            )

            for ((prefix, range) in patterns) {
                for (i in range) {
                    val file = File("$prefix$i")
                    if (file.exists()) {
                        devices.add(
                            SerialDevice(
                                id = file.absolutePath,
                                name = file.name,
                                vendorId = null,
                                productId = null,
                                metadata = mapOf(
                                    "type" to "uart",
                                    "path" to file.absolutePath,
                                    "readable" to file.canRead(),
                                    "writable" to file.canWrite()
                                )
                            )
                        )
                    }
                }
            }

            devices.sortBy { it.id }
            devices
        } catch (e: Exception) {
            throw SerialPortException("Failed to list UART devices", cause = e)
        }
    }

    override suspend fun connect(device: SerialDevice, config: SerialConfig?) =
        withContext(Dispatchers.IO) {
            if (isConnected) {
                throw ConnectionException("Already connected to a device")
            }

            val serialConfig = config ?: SerialConfig.GS805
            val devicePath = device.id

            try {
                val serialPort = SerialPort(devicePath)
                serialPort.open(
                    baudRate = serialConfig.baudRate,
                    dataBits = serialConfig.dataBits,
                    stopBits = serialConfig.stopBits,
                    parity = serialConfig.parity
                )

                _serialPort = serialPort
                _device = device
                _config = serialConfig
                _connectionStateFlow.emit(true)

                // Start the reader coroutine
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                _scope = scope
                _readerJob = scope.launch {
                    readLoop(serialPort)
                }
            } catch (e: Exception) {
                cleanup()
                if (e is kr.co.anyeats.gs805serial.exception.GS805Exception) throw e
                throw ConnectionException(
                    "Failed to connect to UART device: ${device.name}",
                    portName = devicePath,
                    cause = e
                )
            }
        }

    private suspend fun readLoop(serialPort: SerialPort) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        val inputStream = serialPort.inputStream ?: return

        try {
            while (_scope?.isActive == true && serialPort.isOpen) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        _inputFlow.tryEmit(data)
                    }
                } catch (e: Exception) {
                    if (_scope?.isActive == true && serialPort.isOpen) {
                        // Connection error - update state and stop
                        _connectionStateFlow.tryEmit(false)
                        cleanup()
                        return
                    }
                }
            }
        } catch (_: Exception) {
            // Reader loop terminated
        }
    }

    override suspend fun disconnect() {
        if (!isConnected) return
        try {
            _readerJob?.cancel()
            _readerJob = null
            _scope?.cancel()
            _scope = null
            _serialPort?.close()
            _serialPort = null
            _device = null
            _config = null
            _connectionStateFlow.emit(false)
        } catch (e: Exception) {
            throw SerialPortException("Error disconnecting from UART device", cause = e)
        }
    }

    private fun cleanup() {
        try {
            _readerJob?.cancel()
        } catch (_: Exception) {}
        try {
            _scope?.cancel()
        } catch (_: Exception) {}
        try {
            _serialPort?.close()
        } catch (_: Exception) {}
        _readerJob = null
        _scope = null
        _serialPort = null
        _device = null
        _config = null
    }

    override suspend fun write(data: ByteArray): Int = withContext(Dispatchers.IO) {
        val serialPort = _serialPort
            ?: throw NotConnectedException("Cannot write: not connected to UART device")
        val outputStream = serialPort.outputStream
            ?: throw NotConnectedException("Cannot write: output stream not available")

        try {
            outputStream.write(data)
            outputStream.flush()
            data.size
        } catch (e: Exception) {
            throw SerialPortException(
                "Error writing to UART port",
                portName = _device?.id,
                cause = e
            )
        }
    }

    override suspend fun dispose() {
        disconnect()
    }
}

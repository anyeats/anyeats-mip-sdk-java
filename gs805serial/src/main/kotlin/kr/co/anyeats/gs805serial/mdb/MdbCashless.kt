package kr.co.anyeats.gs805serial.mdb

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kr.co.anyeats.gs805serial.serial.SerialConfig
import kr.co.anyeats.gs805serial.serial.SerialConnection
import kr.co.anyeats.gs805serial.serial.SerialDevice

/**
 * MDB Cashless card reader controller.
 *
 * Manages communication with MDB cashless reader via MDB-RS232 bridge.
 * The bridge communicates over a separate serial port from the GS805 machine.
 */
class MdbCashless(private val serialImpl: SerialConnection) {

    private var connection: SerialConnection? = null
    private var inputJob: Job? = null
    private var connectionMonitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var _state = CashlessState.INACTIVE
    private var _readerConfig: ReaderConfig? = null
    private var _pendingVend: VendRequest? = null
    private var _isConnected = false
    private var _connectedDevice: SerialDevice? = null

    private val receiveBuffer = StringBuilder()

    private val _eventFlow = MutableSharedFlow<CashlessEvent>(extraBufferCapacity = 64)
    private val _connectionStateFlow = MutableStateFlow(false)

    val eventFlow: Flow<CashlessEvent> = _eventFlow.asSharedFlow()
    val connectionStateFlow: StateFlow<Boolean> = _connectionStateFlow.asStateFlow()

    val isConnected: Boolean get() = _isConnected
    val connectedDevice: SerialDevice? get() = _connectedDevice
    val state: CashlessState get() = _state
    val readerConfig: ReaderConfig? get() = _readerConfig
    val pendingVend: VendRequest? get() = _pendingVend

    suspend fun listDevices(): List<SerialDevice> = serialImpl.listDevices()

    suspend fun connect(device: SerialDevice) {
        val config = SerialConfig(
            baudRate = MdbConfig.BAUD_RATE,
            dataBits = MdbConfig.DATA_BITS,
            stopBits = MdbConfig.STOP_BITS,
            parity = MdbConfig.PARITY
        )

        serialImpl.connect(device, config)
        connection = serialImpl
        _connectedDevice = device
        _isConnected = true

        inputJob = scope.launch {
            serialImpl.inputFlow.collect { rawData ->
                onDataReceived(rawData)
            }
        }

        connectionMonitorJob = scope.launch {
            serialImpl.connectionStateFlow.collect { connected ->
                _isConnected = connected
                _connectionStateFlow.emit(connected)
                if (!connected) {
                    _state = CashlessState.INACTIVE
                    emitEvent(CashlessEventType.STATE_CHANGED)
                }
            }
        }

        _connectionStateFlow.emit(true)
        emitEvent(CashlessEventType.STATE_CHANGED)
    }

    suspend fun disconnect() {
        inputJob?.cancel()
        connectionMonitorJob?.cancel()
        inputJob = null
        connectionMonitorJob = null

        connection?.disconnect()
        connection = null
        _connectedDevice = null
        _isConnected = false
        _state = CashlessState.INACTIVE
        _pendingVend = null
        receiveBuffer.clear()

        _connectionStateFlow.emit(false)
    }

    // ========== Cashless Commands ==========

    suspend fun setup(maxPrice: Int = 0xFFFF, minPrice: Int = 0x0000) {
        ensureConnected()
        sendHex(intArrayOf(0x11, 0x00, 0x01, 0x00, 0x00, 0x00))
        delay(500)
        sendHex(intArrayOf(
            0x11, 0x01,
            (maxPrice shr 8) and 0xFF, maxPrice and 0xFF,
            (minPrice shr 8) and 0xFF, minPrice and 0xFF
        ))
        _state = CashlessState.DISABLED
        emitEvent(CashlessEventType.STATE_CHANGED)
    }

    suspend fun enable() {
        ensureConnected()
        sendHex(intArrayOf(0x14, 0x01))
        _state = CashlessState.ENABLED
        emitEvent(CashlessEventType.STATE_CHANGED)
    }

    suspend fun disable() {
        ensureConnected()
        sendHex(intArrayOf(0x14, 0x00))
        _state = CashlessState.DISABLED
        emitEvent(CashlessEventType.STATE_CHANGED)
    }

    suspend fun cancel() {
        ensureConnected()
        sendHex(intArrayOf(0x14, 0x02))
    }

    suspend fun requestVend(price: Int, itemNumber: Int = 1) {
        ensureConnected()
        check(_state == CashlessState.SESSION_IDLE) {
            "Cannot request vend in state: ${_state.displayName}. Must be in Session Idle state."
        }

        _pendingVend = VendRequest(price = price, itemNumber = itemNumber)
        sendHex(intArrayOf(
            0x13, 0x00,
            (price shr 8) and 0xFF, price and 0xFF,
            (itemNumber shr 8) and 0xFF, itemNumber and 0xFF
        ))
        _state = CashlessState.VEND_REQUESTED
        emitEvent(CashlessEventType.STATE_CHANGED)
    }

    suspend fun vendSuccess(itemNumber: Int = 1) {
        ensureConnected()
        sendHex(intArrayOf(0x13, 0x02, (itemNumber shr 8) and 0xFF, itemNumber and 0xFF))
        _state = CashlessState.ENABLED
        _pendingVend = null
    }

    suspend fun vendCancel() {
        ensureConnected()
        sendHex(intArrayOf(0x13, 0x01))
        _state = CashlessState.ENABLED
        _pendingVend = null
        emitEvent(CashlessEventType.STATE_CHANGED)
    }

    suspend fun cashSale(price: Int, itemNumber: Int = 1) {
        ensureConnected()
        sendHex(intArrayOf(
            0x13, 0x05,
            (price shr 8) and 0xFF, price and 0xFF,
            (itemNumber shr 8) and 0xFF, itemNumber and 0xFF
        ))
    }

    suspend fun sessionComplete() {
        ensureConnected()
        sendHex(intArrayOf(0x13, 0x04))
        _state = CashlessState.ENABLED
        emitEvent(CashlessEventType.SESSION_COMPLETED)
        emitEvent(CashlessEventType.STATE_CHANGED)
    }

    suspend fun requestId() {
        ensureConnected()
        sendHex(intArrayOf(0x17, 0x00))
    }

    // ========== Internal ==========

    private fun ensureConnected() {
        check(_isConnected && connection != null) { "Not connected to MDB-RS232 bridge" }
    }

    private suspend fun sendHex(bytes: IntArray) {
        val data = ByteArray(bytes.size) { bytes[it].toByte() }
        connection!!.write(data)
        emitEvent(CashlessEventType.COMMAND_SENT, mapOf("hex" to bytesToHex(data)))
    }

    private fun onDataReceived(rawData: ByteArray) {
        val asciiStr = String(rawData).trim()
        if (asciiStr.isEmpty()) return

        receiveBuffer.append(asciiStr)
        val buffered = receiveBuffer.toString()
        val cleaned = buffered.replace(" ", "").replace("\r", "").replace("\n", "")
        if (cleaned.length < 2) return

        val bytes = mutableListOf<Int>()
        var i = 0
        while (i < cleaned.length - 1) {
            val hexPair = cleaned.substring(i, i + 2)
            val value = hexPair.toIntOrNull(16) ?: run {
                receiveBuffer.clear()
                return
            }
            bytes.add(value)
            i += 2
        }

        receiveBuffer.clear()
        emitEvent(CashlessEventType.RAW_DATA, mapOf("hex" to bytesToHex(ByteArray(bytes.size) { bytes[it].toByte() }), "bytes" to bytes))
        parseResponse(bytes)
    }

    private fun parseResponse(bytes: List<Int>) {
        if (bytes.isEmpty()) return
        val firstByte = bytes[0]

        if (MdbDeviceId.isCashless(firstByte) && bytes.size > 1) {
            parseCashlessData(bytes.subList(1, bytes.size))
            return
        }
        parseCashlessData(bytes)
    }

    private fun parseCashlessData(bytes: List<Int>) {
        if (bytes.isEmpty()) return
        val responseCode = bytes[0]

        when (responseCode) {
            CashlessResponse.ACK -> emitEvent(CashlessEventType.ACK_RECEIVED)

            CashlessResponse.CONFIG_DATA -> {
                _readerConfig = ReaderConfig.fromBytes(bytes.drop(1))
                emitEvent(CashlessEventType.CONFIG_RECEIVED, mapOf("config" to _readerConfig.toString()))
            }

            CashlessResponse.BEGIN_SESSION -> {
                val funds = if (bytes.size >= 3) (bytes[1] shl 8) or bytes[2] else null
                _state = CashlessState.SESSION_IDLE
                val data = mutableMapOf<String, Any?>()
                funds?.let { data["funds"] = it }
                emitEvent(CashlessEventType.CARD_DETECTED, data)
                emitEvent(CashlessEventType.STATE_CHANGED)
            }

            CashlessResponse.VEND_APPROVED -> {
                val amount = if (bytes.size >= 3) (bytes[1] shl 8) or bytes[2] else null
                _state = CashlessState.VENDING
                val data = mutableMapOf<String, Any?>()
                amount?.let { data["amount"] = it }
                emitEvent(CashlessEventType.VEND_APPROVED, data)
                emitEvent(CashlessEventType.STATE_CHANGED)
            }

            CashlessResponse.VEND_DENIED -> {
                _state = CashlessState.SESSION_IDLE
                _pendingVend = null
                emitEvent(CashlessEventType.VEND_DENIED)
                emitEvent(CashlessEventType.STATE_CHANGED)
            }

            CashlessResponse.END_SESSION, CashlessResponse.SESSION_CANCEL_REQUEST -> {
                _state = CashlessState.ENABLED
                _pendingVend = null
                emitEvent(CashlessEventType.SESSION_CANCELLED)
                emitEvent(CashlessEventType.STATE_CHANGED)
            }

            CashlessResponse.PERIPHERAL_ID -> {
                val idBytes = bytes.drop(1)
                emitEvent(CashlessEventType.READER_ID_RECEIVED,
                    mapOf("id" to bytesToHex(ByteArray(idBytes.size) { idBytes[it].toByte() })))
            }
        }
    }

    private fun emitEvent(type: CashlessEventType, data: Map<String, Any?> = emptyMap()) {
        _eventFlow.tryEmit(CashlessEvent(type = type, state = _state, data = data))
    }

    suspend fun dispose() {
        disconnect()
        scope.cancel()
    }

    companion object {
        private fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
    }
}

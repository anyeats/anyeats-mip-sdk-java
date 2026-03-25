package kr.co.anyeats.gs805serial.serial

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kr.co.anyeats.gs805serial.exception.*
import kr.co.anyeats.gs805serial.protocol.CommandMessage
import kr.co.anyeats.gs805serial.protocol.ResponseMessage

/** High-level serial communication manager with message parsing */
class SerialManager(
    private val connection: SerialConnection,
    reconnectConfig: ReconnectConfig? = null
) {
    private var parser: MessageParser? = null
    private var inputJob: Job? = null
    private var connectionMonitorJob: Job? = null
    private var reconnectManager: ReconnectManager? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _messageFlow = MutableSharedFlow<ResponseMessage>(extraBufferCapacity = 64)
    private val _connectionStateFlow = MutableStateFlow(false)
    private val _reconnectEventFlow = MutableSharedFlow<ReconnectEvent>(extraBufferCapacity = 16)

    val messageFlow: SharedFlow<ResponseMessage> = _messageFlow.asSharedFlow()
    val connectionStateFlow: StateFlow<Boolean> = _connectionStateFlow.asStateFlow()
    val reconnectEventFlow: SharedFlow<ReconnectEvent> = _reconnectEventFlow.asSharedFlow()

    val isConnected: Boolean get() = connection.isConnected
    val isReconnecting: Boolean get() = reconnectManager?.isReconnecting ?: false
    val connectedDevice: SerialDevice? get() = connection.connectedDevice
    val currentConfig: SerialConfig? get() = connection.currentConfig
    val bufferSize: Int get() = parser?.bufferSize ?: 0

    init {
        if (reconnectConfig != null && reconnectConfig.strategy != ReconnectStrategy.NEVER) {
            reconnectManager = ReconnectManager(connection, reconnectConfig).also { rm ->
                rm.startMonitoring()
                scope.launch {
                    rm.eventFlow.collect { event ->
                        _reconnectEventFlow.emit(event)
                        if (event.state == ReconnectState.CONNECTED) {
                            setupMessageParser()
                        }
                    }
                }
            }
        }
    }

    suspend fun listDevices(): List<SerialDevice> = connection.listDevices()

    suspend fun connect(device: SerialDevice, config: SerialConfig? = null) {
        if (isConnected) throw ConnectionException("Already connected to a device")

        connection.connect(device, config ?: SerialConfig.GS805)
        reconnectManager?.saveConnection(device, config)
        setupMessageParser()

        connectionMonitorJob = scope.launch {
            connection.connectionStateFlow.collect { connected ->
                _connectionStateFlow.emit(connected)
                if (!connected) cleanup()
            }
        }

        _connectionStateFlow.emit(true)
    }

    private fun setupMessageParser() {
        parser = MessageParser()
        inputJob = scope.launch {
            connection.inputFlow.collect { bytes ->
                parser?.addBytes(bytes)
            }
        }
        scope.launch {
            parser?.messageFlow?.collect { message ->
                _messageFlow.emit(message)
            }
        }
    }

    suspend fun disconnect(clearReconnection: Boolean = true) {
        cleanup()
        connection.disconnect()
        if (clearReconnection) reconnectManager?.clearConnection()
        _connectionStateFlow.emit(false)
    }

    private fun cleanup() {
        inputJob?.cancel()
        inputJob = null
        parser?.close()
        parser = null
        connectionMonitorJob?.cancel()
        connectionMonitorJob = null
    }

    /** Send a command and wait for matching response */
    suspend fun sendCommand(
        command: CommandMessage,
        timeoutMs: Long = 100,
        retries: Int = 2
    ): ResponseMessage {
        if (!isConnected) throw NotConnectedException("Cannot send command: not connected")

        for (attempt in 0..retries) {
            try {
                val bytes = command.toBytes()
                connection.write(bytes)

                val response = withTimeout(timeoutMs) {
                    messageFlow.first { it.command == command.command }
                }

                if (response.isError) {
                    throw MachineErrorException.fromStatus(
                        response.statusCode!!,
                        context = "Command 0x${command.command.toString(16)}"
                    )
                }

                return response
            } catch (e: TimeoutCancellationException) {
                if (attempt >= retries) {
                    throw TimeoutException(
                        "No response received",
                        timeoutMs = timeoutMs.toInt(),
                        retryCount = attempt
                    )
                }
            }
        }

        throw TimeoutException(
            "Command failed after $retries retries",
            timeoutMs = timeoutMs.toInt(),
            retryCount = retries
        )
    }

    suspend fun write(data: ByteArray): Int = connection.write(data)

    suspend fun reconnect() {
        reconnectManager?.reconnect()
            ?: throw GS805Exception("Reconnection is not enabled")
    }

    fun clearBuffer() {
        parser?.clearBuffer()
    }

    suspend fun dispose() {
        cleanup()
        reconnectManager?.dispose()
        connection.dispose()
        scope.cancel()
    }
}

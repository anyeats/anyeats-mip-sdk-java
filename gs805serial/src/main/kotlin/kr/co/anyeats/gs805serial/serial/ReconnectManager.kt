package kr.co.anyeats.gs805serial.serial

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Reconnection strategy */
enum class ReconnectStrategy {
    NEVER,
    IMMEDIATE,
    EXPONENTIAL_BACKOFF,
    FIXED_INTERVAL
}

/** Reconnection configuration */
data class ReconnectConfig(
    val strategy: ReconnectStrategy = ReconnectStrategy.EXPONENTIAL_BACKOFF,
    val maxAttempts: Int = 5,
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 30000,
    val backoffMultiplier: Double = 2.0,
    val reconnectOnError: Boolean = true
) {
    companion object {
        val NEVER = ReconnectConfig(strategy = ReconnectStrategy.NEVER, maxAttempts = 0)
        val IMMEDIATE = ReconnectConfig(strategy = ReconnectStrategy.IMMEDIATE, maxAttempts = 5, initialDelayMs = 0)
        val EXPONENTIAL_BACKOFF = ReconnectConfig()
        val FIXED_INTERVAL = ReconnectConfig(
            strategy = ReconnectStrategy.FIXED_INTERVAL,
            maxAttempts = 10,
            initialDelayMs = 2000
        )
    }
}

/** Reconnection state */
enum class ReconnectState {
    IDLE, WAITING, CONNECTING, CONNECTED, FAILED
}

/** Reconnection event */
data class ReconnectEvent(
    val state: ReconnectState,
    val attempt: Int,
    val maxAttempts: Int,
    val nextAttemptDelayMs: Long? = null,
    val error: Throwable? = null
) {
    override fun toString(): String {
        val parts = mutableListOf("ReconnectEvent(state: $state, attempt: $attempt")
        if (maxAttempts > 0) parts.add("/$maxAttempts")
        nextAttemptDelayMs?.let { parts.add(", nextAttempt: ${it}ms") }
        error?.let { parts.add(", error: $it") }
        return "${parts.joinToString("")})"
    }
}

/** Reconnection manager */
class ReconnectManager(
    private val connection: SerialConnection,
    private val config: ReconnectConfig = ReconnectConfig.EXPONENTIAL_BACKOFF
) {
    private var lastDevice: SerialDevice? = null
    private var lastConfig: SerialConfig? = null
    private var reconnectJob: Job? = null
    private var attemptCount = 0
    private var _state = ReconnectState.IDLE
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _eventFlow = MutableSharedFlow<ReconnectEvent>(extraBufferCapacity = 16)

    val eventFlow: Flow<ReconnectEvent> = _eventFlow.asSharedFlow()
    val state: ReconnectState get() = _state
    val isReconnecting: Boolean get() = _state == ReconnectState.WAITING || _state == ReconnectState.CONNECTING

    fun startMonitoring() {
        scope.launch {
            connection.connectionStateFlow.collect { isConnected ->
                if (!isConnected && lastDevice != null) {
                    startReconnection()
                } else if (isConnected) {
                    stopReconnection()
                }
            }
        }
    }

    suspend fun reconnect() {
        if (lastDevice == null) throw kr.co.anyeats.gs805serial.exception.GS805Exception("No previous device to reconnect to")
        stopReconnection()
        attemptCount = 0
        attemptReconnection()
    }

    private fun startReconnection() {
        if (config.strategy == ReconnectStrategy.NEVER) return
        if (_state == ReconnectState.WAITING || _state == ReconnectState.CONNECTING) return
        attemptCount = 0
        scheduleReconnection(0)
    }

    private fun stopReconnection() {
        reconnectJob?.cancel()
        reconnectJob = null
        attemptCount = 0
        _state = ReconnectState.IDLE
    }

    private fun scheduleReconnection(delayMs: Long) {
        if (config.maxAttempts > 0 && attemptCount >= config.maxAttempts) {
            _state = ReconnectState.FAILED
            _eventFlow.tryEmit(ReconnectEvent(ReconnectState.FAILED, attemptCount, config.maxAttempts))
            return
        }

        _state = ReconnectState.WAITING
        _eventFlow.tryEmit(ReconnectEvent(ReconnectState.WAITING, attemptCount, config.maxAttempts, delayMs))

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            attemptReconnection()
        }
    }

    private suspend fun attemptReconnection() {
        val device = lastDevice ?: return

        _state = ReconnectState.CONNECTING
        _eventFlow.tryEmit(ReconnectEvent(ReconnectState.CONNECTING, attemptCount, config.maxAttempts))

        try {
            connection.connect(device, lastConfig)
            _state = ReconnectState.CONNECTED
            _eventFlow.tryEmit(ReconnectEvent(ReconnectState.CONNECTED, attemptCount, config.maxAttempts))
            stopReconnection()
        } catch (e: Exception) {
            attemptCount++
            val nextDelay = calculateNextDelay()
            scheduleReconnection(nextDelay)
        }
    }

    private fun calculateNextDelay(): Long = when (config.strategy) {
        ReconnectStrategy.NEVER -> 0
        ReconnectStrategy.IMMEDIATE -> 0
        ReconnectStrategy.FIXED_INTERVAL -> config.initialDelayMs
        ReconnectStrategy.EXPONENTIAL_BACKOFF -> {
            val delay = (config.initialDelayMs * (1L shl attemptCount.coerceAtMost(10)))
            delay.coerceIn(config.initialDelayMs, config.maxDelayMs)
        }
    }

    fun saveConnection(device: SerialDevice, config: SerialConfig?) {
        lastDevice = device
        lastConfig = config
    }

    fun clearConnection() {
        lastDevice = null
        lastConfig = null
        stopReconnection()
    }

    fun dispose() {
        stopReconnection()
        scope.cancel()
    }
}

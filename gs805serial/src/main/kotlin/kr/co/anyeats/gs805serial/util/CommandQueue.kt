package kr.co.anyeats.gs805serial.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kr.co.anyeats.gs805serial.exception.CommandQueueException
import kr.co.anyeats.gs805serial.protocol.CommandMessage
import kr.co.anyeats.gs805serial.protocol.ResponseMessage
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/** Queue event types */
enum class QueueEventType {
    COMMAND_ADDED, COMMAND_STARTED, COMMAND_COMPLETED,
    COMMAND_FAILED, COMMAND_RETRYING, QUEUE_STARTED,
    QUEUE_PAUSED, QUEUE_RESUMED, QUEUE_CLEARED
}

/** Command queue status */
enum class QueueStatus {
    IDLE, PROCESSING, PAUSED, DISPOSED
}

/** Queue event */
data class QueueEvent(
    val type: QueueEventType,
    val command: QueuedCommand? = null,
    val message: String,
    val timestamp: Date = Date()
) {
    override fun toString(): String = "${type.name}: $message"
}

/** Queued command item */
class QueuedCommand(
    val id: String,
    val command: CommandMessage,
    val maxRetries: Int = 3,
    var currentAttempt: Int = 0,
    val timeoutMs: Long = 100
) {
    val deferred = CompletableDeferred<ResponseMessage>()
    val addedAt: Date = Date()
    var startedAt: Date? = null
    var completedAt: Date? = null
    var lastError: Throwable? = null

    val hasRetriesLeft: Boolean get() = currentAttempt < maxRetries

    val waitingTimeMs: Long
        get() = (startedAt ?: Date()).time - addedAt.time

    val executionTimeMs: Long?
        get() = startedAt?.let { (completedAt ?: Date()).time - it.time }

    override fun toString(): String =
        "QueuedCommand(id: $id, command: 0x${command.command.toString(16)}, " +
                "attempt: $currentAttempt/$maxRetries, waiting: ${waitingTimeMs}ms)"
}

/** Command Queue Manager */
class CommandQueue(
    private val sendFunction: suspend (CommandMessage) -> ResponseMessage
) {
    private val queue = ConcurrentLinkedDeque<QueuedCommand>()
    private val channel = Channel<QueuedCommand>(Channel.UNLIMITED)
    private var _status = QueueStatus.IDLE
    private val commandCounter = AtomicInteger(0)
    private var processingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _eventFlow = MutableSharedFlow<QueueEvent>(extraBufferCapacity = 64)

    var autoRetry = true
    var defaultMaxRetries = 3
    var defaultTimeoutMs = 100L

    val status: QueueStatus get() = _status
    val length: Int get() = queue.size
    val isEmpty: Boolean get() = queue.isEmpty()
    val isProcessing: Boolean get() = _status == QueueStatus.PROCESSING
    val eventFlow: Flow<QueueEvent> = _eventFlow.asSharedFlow()

    private var _currentCommand: QueuedCommand? = null
    val currentCommand: QueuedCommand? get() = _currentCommand

    /** Enqueue a command and return a Deferred result */
    fun enqueue(
        command: CommandMessage,
        maxRetries: Int? = null,
        timeoutMs: Long? = null
    ): Deferred<ResponseMessage> {
        if (_status == QueueStatus.DISPOSED) throw IllegalStateException("Command queue is disposed")

        val queuedCommand = QueuedCommand(
            id = "cmd_${commandCounter.getAndIncrement()}",
            command = command,
            maxRetries = maxRetries ?: defaultMaxRetries,
            timeoutMs = timeoutMs ?: defaultTimeoutMs
        )

        queue.add(queuedCommand)
        emitEvent(QueueEvent(
            type = QueueEventType.COMMAND_ADDED,
            command = queuedCommand,
            message = "Command added to queue (position: ${queue.size})"
        ))

        if (_status == QueueStatus.IDLE) {
            startProcessing()
        }

        return queuedCommand.deferred
    }

    private fun startProcessing() {
        if (_status == QueueStatus.PROCESSING || _status == QueueStatus.DISPOSED) return

        _status = QueueStatus.PROCESSING
        emitEvent(QueueEvent(type = QueueEventType.QUEUE_STARTED, message = "Queue processing started"))

        processingJob = scope.launch {
            while (queue.isNotEmpty() && _status == QueueStatus.PROCESSING) {
                val command = queue.pollFirst() ?: break
                executeCommand(command)
            }
            if (_status == QueueStatus.PROCESSING) {
                _status = QueueStatus.IDLE
            }
        }
    }

    private suspend fun executeCommand(queuedCommand: QueuedCommand) {
        _currentCommand = queuedCommand
        queuedCommand.startedAt = Date()

        emitEvent(QueueEvent(
            type = QueueEventType.COMMAND_STARTED,
            command = queuedCommand,
            message = "Executing command (attempt ${queuedCommand.currentAttempt + 1}/${queuedCommand.maxRetries})"
        ))

        try {
            val response = withTimeout(queuedCommand.timeoutMs) {
                sendFunction(queuedCommand.command)
            }

            queuedCommand.completedAt = Date()
            queuedCommand.deferred.complete(response)

            emitEvent(QueueEvent(
                type = QueueEventType.COMMAND_COMPLETED,
                command = queuedCommand,
                message = "Command completed successfully in ${queuedCommand.executionTimeMs}ms"
            ))
        } catch (e: Exception) {
            queuedCommand.lastError = e
            queuedCommand.currentAttempt++

            if (autoRetry && queuedCommand.hasRetriesLeft) {
                emitEvent(QueueEvent(
                    type = QueueEventType.COMMAND_RETRYING,
                    command = queuedCommand,
                    message = "Command failed, retrying (${queuedCommand.currentAttempt}/${queuedCommand.maxRetries})"
                ))
                queue.addFirst(queuedCommand)
            } else {
                queuedCommand.completedAt = Date()
                queuedCommand.deferred.completeExceptionally(e)
                emitEvent(QueueEvent(
                    type = QueueEventType.COMMAND_FAILED,
                    command = queuedCommand,
                    message = "Command failed after ${queuedCommand.currentAttempt} attempts: $e"
                ))
            }
        } finally {
            _currentCommand = null
        }
    }

    fun pause() {
        if (_status == QueueStatus.PROCESSING) {
            _status = QueueStatus.PAUSED
            emitEvent(QueueEvent(type = QueueEventType.QUEUE_PAUSED, message = "Queue processing paused"))
        }
    }

    fun resume() {
        if (_status == QueueStatus.PAUSED) {
            _status = QueueStatus.IDLE
            emitEvent(QueueEvent(type = QueueEventType.QUEUE_RESUMED, message = "Queue processing resumed"))
            startProcessing()
        }
    }

    fun clear() {
        val count = queue.size
        while (queue.isNotEmpty()) {
            val cmd = queue.pollFirst()
            cmd?.deferred?.completeExceptionally(CommandQueueException("Command cancelled - queue cleared"))
        }
        emitEvent(QueueEvent(type = QueueEventType.QUEUE_CLEARED, message = "Queue cleared ($count commands cancelled)"))
    }

    fun getPendingCommands(): List<QueuedCommand> = queue.toList()

    private fun emitEvent(event: QueueEvent) {
        _eventFlow.tryEmit(event)
    }

    fun dispose() {
        _status = QueueStatus.DISPOSED
        clear()
        processingJob?.cancel()
        scope.cancel()
    }
}

package kr.co.anyeats.gs805serial.exception

import kr.co.anyeats.gs805serial.protocol.StatusCodes

/** Base exception class for all GS805-related errors */
open class GS805Exception(
    override val message: String,
    val code: Int? = null,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    override fun toString(): String {
        val parts = mutableListOf("GS805Exception: $message")
        code?.let { parts.add("(code: 0x${it.toString(16)})") }
        cause?.let { parts.add("Caused by: $it") }
        return parts.joinToString(" ")
    }
}

/** Exception thrown when communication with the machine fails */
open class CommunicationException(
    message: String,
    code: Int? = null,
    cause: Throwable? = null
) : GS805Exception(message, code, cause) {
    override fun toString(): String = "CommunicationException: $message"
}

/** Exception thrown when a command times out */
class TimeoutException(
    message: String,
    val timeoutMs: Int,
    val retryCount: Int = 0
) : CommunicationException(message) {
    override fun toString(): String =
        "TimeoutException: $message (timeout: ${timeoutMs}ms, retries: $retryCount)"
}

/** Exception thrown when the machine returns an error status */
open class MachineErrorException(
    val statusCode: Int,
    message: String? = null
) : GS805Exception(
    message ?: StatusCodes.getMessage(statusCode),
    statusCode
) {
    val statusMessage: String = StatusCodes.getMessage(statusCode)

    override fun toString(): String =
        "MachineErrorException: $statusMessage (0x${statusCode.toString(16)})"

    companion object {
        fun fromStatus(statusCode: Int, context: String? = null): MachineErrorException {
            val msg = if (context != null) {
                "$context: ${StatusCodes.getMessage(statusCode)}"
            } else {
                StatusCodes.getMessage(statusCode)
            }
            return MachineErrorException(statusCode, msg)
        }
    }
}

/** Exception thrown when the machine is busy */
class MachineBusyException(
    message: String = "Machine is busy"
) : MachineErrorException(StatusCodes.BUSY, message) {
    override fun toString(): String = "MachineBusyException: $message"
}

/** Exception thrown when a parameter is invalid */
class ParameterException(
    message: String = "Invalid parameter",
    val parameterName: String? = null,
    val parameterValue: Any? = null
) : MachineErrorException(StatusCodes.PARAMETER_ERROR, message) {
    override fun toString(): String {
        val parts = mutableListOf("ParameterException: $message")
        if (parameterName != null) parts.add("($parameterName = $parameterValue)")
        return parts.joinToString(" ")
    }
}

/** Exception thrown when balance is insufficient */
class InsufficientBalanceException(
    message: String = "Insufficient balance",
    val requiredAmount: Int? = null,
    val availableBalance: Int? = null
) : MachineErrorException(StatusCodes.INSUFFICIENT_BALANCE, message) {
    override fun toString(): String {
        val parts = mutableListOf("InsufficientBalanceException: $message")
        if (requiredAmount != null && availableBalance != null) {
            parts.add("(required: $requiredAmount, available: $availableBalance)")
        }
        return parts.joinToString(" ")
    }
}

/** Exception thrown when a serial port operation fails */
class SerialPortException(
    message: String,
    val portName: String? = null,
    cause: Throwable? = null
) : CommunicationException(message, cause = cause) {
    override fun toString(): String {
        val parts = mutableListOf("SerialPortException: $message")
        portName?.let { parts.add("(port: $it)") }
        return parts.joinToString(" ")
    }
}

/** Exception thrown when a message cannot be parsed */
class MessageParseException(
    message: String,
    val rawBytes: List<Int>? = null
) : GS805Exception(message) {
    override fun toString(): String {
        val parts = mutableListOf("MessageParseException: $message")
        rawBytes?.let { bytes ->
            val hex = bytes.joinToString(" ") { it.toString(16).padStart(2, '0') }
            parts.add("(bytes: $hex)")
        }
        return parts.joinToString(" ")
    }
}

/** Exception thrown when the machine is not connected */
class NotConnectedException(
    message: String = "Not connected to machine"
) : GS805Exception(message) {
    override fun toString(): String = "NotConnectedException: $message"
}

/** Exception thrown when a connection attempt fails */
class ConnectionException(
    message: String,
    val portName: String? = null,
    cause: Throwable? = null
) : GS805Exception(message, cause = cause) {
    override fun toString(): String {
        val parts = mutableListOf("ConnectionException: $message")
        portName?.let { parts.add("(port: $it)") }
        return parts.joinToString(" ")
    }
}

/** Exception thrown when an operation is cancelled */
class OperationCancelledException(
    message: String = "Operation was cancelled"
) : GS805Exception(message) {
    override fun toString(): String = "OperationCancelledException: $message"
}

/** Exception thrown by command queue */
class CommandQueueException(
    message: String
) : GS805Exception(message)

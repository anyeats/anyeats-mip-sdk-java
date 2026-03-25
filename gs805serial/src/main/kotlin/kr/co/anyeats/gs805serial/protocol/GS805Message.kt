package kr.co.anyeats.gs805serial.protocol

/** Command message sent from host to GS805 machine.
 *
 * Format: FLAG1(2) + LEN(1) + COMND(1) + DATA(variable) + SUM(1)
 */
class CommandMessage(
    val command: Int,
    private val _data: ByteArray = ByteArray(0)
) {
    init {
        require(command in 0x01..0xFF) { "Invalid command code: 0x${command.toString(16)}" }
        val totalLength = 2 + _data.size // COMND + DATA + SUM
        require(totalLength in ProtocolFlags.MIN_MESSAGE_LENGTH..ProtocolFlags.MAX_MESSAGE_LENGTH) {
            "Message length out of range: $totalLength"
        }
    }

    val length: Int get() = 2 + _data.size

    val data: ByteArray get() = _data.copyOf()

    fun toBytes(): ByteArray {
        val messageSize = 2 + 1 + 1 + _data.size + 1 // FLAG1 + LEN + COMND + DATA + SUM
        val buffer = ByteArray(messageSize)
        var offset = 0

        // FLAG1 (2 bytes, Big Endian)
        buffer[offset++] = ((ProtocolFlags.COMMAND_HEADER shr 8) and 0xFF).toByte()
        buffer[offset++] = (ProtocolFlags.COMMAND_HEADER and 0xFF).toByte()
        // LEN
        buffer[offset++] = length.toByte()
        // COMND
        buffer[offset++] = command.toByte()
        // DATA
        for (b in _data) {
            buffer[offset++] = b
        }
        // SUM
        var checksum = 0
        checksum += (ProtocolFlags.COMMAND_HEADER shr 8) and 0xFF
        checksum += ProtocolFlags.COMMAND_HEADER and 0xFF
        checksum += length
        checksum += command
        for (b in _data) {
            checksum += b.toInt() and 0xFF
        }
        buffer[offset] = (checksum and 0xFF).toByte()

        return buffer
    }

    override fun toString(): String {
        val dataHex = _data.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "CommandMessage(command: 0x${command.toString(16)}, data: [$dataHex])"
    }

    companion object {
        fun withByte(command: Int, value: Int): CommandMessage =
            CommandMessage(command, byteArrayOf(value.toByte()))

        fun withBytes(command: Int, values: List<Int>): CommandMessage =
            CommandMessage(command, ByteArray(values.size) { values[it].toByte() })

        fun withWord(command: Int, value: Int): CommandMessage {
            require(value in 0..0xFFFF) { "Word value out of range: $value" }
            return CommandMessage(command, byteArrayOf(
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte()
            ))
        }

        fun withDWord(command: Int, value: Int): CommandMessage {
            return CommandMessage(command, byteArrayOf(
                ((value shr 24) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte()
            ))
        }
    }
}

/** Response message received from GS805 machine.
 *
 * Format: FLAG2(2) + LEN(1) + RCOMND(1) + DATA(variable) + SUM(1)
 */
class ResponseMessage(
    val command: Int,
    private val _data: ByteArray,
    val rawBytes: ByteArray? = null
) {
    val length: Int get() = 2 + _data.size

    val data: ByteArray get() = _data.copyOf()

    val isActiveReport: Boolean
        get() = _data.isNotEmpty() && (_data[0].toInt() and 0xFF) == StatusCodes.ACTIVE_REPORT

    val statusCode: Int?
        get() = if (_data.isNotEmpty()) _data[0].toInt() and 0xFF else null

    val isSuccess: Boolean
        get() = statusCode?.let { StatusCodes.isSuccess(it) } ?: false

    val isError: Boolean
        get() = statusCode?.let { StatusCodes.isError(it) } ?: false

    val statusMessage: String
        get() = statusCode?.let { StatusCodes.getMessage(it) } ?: "No status"

    /** Get data byte at index (excluding status code) */
    fun getDataByte(index: Int = 0): Int? {
        val dataIndex = index + 1 // Skip status code
        return if (dataIndex < _data.size) _data[dataIndex].toInt() and 0xFF else null
    }

    /** Get 16-bit word (Big Endian, excluding status code) */
    fun getDataWord(index: Int = 0): Int? {
        val startIndex = index + 1
        return if (startIndex + 1 < _data.size) {
            ((_data[startIndex].toInt() and 0xFF) shl 8) or (_data[startIndex + 1].toInt() and 0xFF)
        } else null
    }

    /** Get 32-bit double word (Big Endian, excluding status code) */
    fun getDataDWord(index: Int = 0): Int? {
        val startIndex = index + 1
        return if (startIndex + 3 < _data.size) {
            ((_data[startIndex].toInt() and 0xFF) shl 24) or
                    ((_data[startIndex + 1].toInt() and 0xFF) shl 16) or
                    ((_data[startIndex + 2].toInt() and 0xFF) shl 8) or
                    (_data[startIndex + 3].toInt() and 0xFF)
        } else null
    }

    fun toBytes(): ByteArray {
        val messageSize = 2 + 1 + 1 + _data.size + 1
        val buffer = ByteArray(messageSize)
        var offset = 0

        buffer[offset++] = ((ProtocolFlags.RESPONSE_HEADER shr 8) and 0xFF).toByte()
        buffer[offset++] = (ProtocolFlags.RESPONSE_HEADER and 0xFF).toByte()
        buffer[offset++] = length.toByte()
        buffer[offset++] = command.toByte()
        for (b in _data) {
            buffer[offset++] = b
        }
        var checksum = 0
        checksum += (ProtocolFlags.RESPONSE_HEADER shr 8) and 0xFF
        checksum += ProtocolFlags.RESPONSE_HEADER and 0xFF
        checksum += length
        checksum += command
        for (b in _data) {
            checksum += b.toInt() and 0xFF
        }
        buffer[offset] = (checksum and 0xFF).toByte()
        return buffer
    }

    override fun toString(): String {
        val dataHex = _data.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val statusStr = statusCode?.let { "0x${it.toString(16)}" } ?: "none"
        return "ResponseMessage(command: 0x${command.toString(16)}, status: $statusStr, data: [$dataHex])"
    }

    companion object {
        fun fromBytes(bytes: ByteArray): ResponseMessage? {
            try {
                if (bytes.size < 5) return null

                var offset = 0
                val flag = ((bytes[offset++].toInt() and 0xFF) shl 8) or (bytes[offset++].toInt() and 0xFF)
                if (flag != ProtocolFlags.RESPONSE_HEADER) return null

                val len = bytes[offset++].toInt() and 0xFF
                if (bytes.size < 3 + len) return null

                val rcomnd = bytes[offset++].toInt() and 0xFF
                val dataLength = len - 2 // len includes RCOMND(1) + SUM(1)
                val data = ByteArray(dataLength)
                for (i in 0 until dataLength) {
                    data[i] = bytes[offset++]
                }

                val receivedChecksum = bytes[offset].toInt() and 0xFF

                var expectedChecksum = 0
                expectedChecksum += (ProtocolFlags.RESPONSE_HEADER shr 8) and 0xFF
                expectedChecksum += ProtocolFlags.RESPONSE_HEADER and 0xFF
                expectedChecksum += len
                expectedChecksum += rcomnd
                for (b in data) {
                    expectedChecksum += b.toInt() and 0xFF
                }
                expectedChecksum = expectedChecksum and 0xFF

                if (receivedChecksum != expectedChecksum) return null

                return ResponseMessage(rcomnd, data, bytes)
            } catch (e: Exception) {
                return null
            }
        }
    }
}

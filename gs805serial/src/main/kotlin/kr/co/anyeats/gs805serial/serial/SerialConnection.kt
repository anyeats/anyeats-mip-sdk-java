package kr.co.anyeats.gs805serial.serial

import kotlinx.coroutines.flow.Flow

/** Serial device information */
data class SerialDevice(
    val id: String,
    val name: String,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val metadata: Map<String, Any?>? = null
) {
    override fun toString(): String {
        val parts = mutableListOf(name)
        if (vendorId != null && productId != null) {
            parts.add("VID:${vendorId.toString(16).padStart(4, '0')}")
            parts.add("PID:${productId.toString(16).padStart(4, '0')}")
        }
        return parts.joinToString(" - ")
    }

    override fun equals(other: Any?): Boolean =
        other is SerialDevice && id == other.id

    override fun hashCode(): Int = id.hashCode()
}

/** Serial connection configuration */
data class SerialConfig(
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Int = 0
) {
    override fun toString(): String {
        val parityChar = when (parity) {
            0 -> "N"; 1 -> "O"; 2 -> "E"; else -> "?"
        }
        return "${baudRate}bps, $dataBits$parityChar$stopBits"
    }

    companion object {
        /** Default GS805 configuration (9600, 8N1) */
        val GS805 = SerialConfig(baudRate = 9600, dataBits = 8, stopBits = 1, parity = 0)
    }
}

/** Abstract serial connection interface */
interface SerialConnection {
    suspend fun listDevices(): List<SerialDevice>
    suspend fun connect(device: SerialDevice, config: SerialConfig? = null)
    suspend fun disconnect()
    val isConnected: Boolean
    val connectedDevice: SerialDevice?
    val currentConfig: SerialConfig?
    suspend fun write(data: ByteArray): Int
    val inputFlow: Flow<ByteArray>
    val connectionStateFlow: Flow<Boolean>
    suspend fun dispose()
}

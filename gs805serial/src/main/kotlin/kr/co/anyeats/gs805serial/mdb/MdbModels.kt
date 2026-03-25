package kr.co.anyeats.gs805serial.mdb

import java.util.Date

/** Cashless reader state machine */
enum class CashlessState(val displayName: String) {
    INACTIVE("Inactive"),
    DISABLED("Disabled"),
    ENABLED("Enabled"),
    SESSION_IDLE("Session Idle"),
    VEND_REQUESTED("Vend Requested"),
    VENDING("Vending"),
    ERROR("Error")
}

/** Cashless event types */
enum class CashlessEventType {
    STATE_CHANGED, CARD_DETECTED, VEND_APPROVED, VEND_DENIED,
    SESSION_CANCELLED, SESSION_COMPLETED, CONFIG_RECEIVED,
    READER_ID_RECEIVED, ERROR, RAW_DATA, COMMAND_SENT, ACK_RECEIVED
}

/** Event from cashless reader */
data class CashlessEvent(
    val type: CashlessEventType,
    val state: CashlessState,
    val timestamp: Date = Date(),
    val data: Map<String, Any?> = emptyMap()
) {
    val approvedAmount: Int? get() = data["amount"] as? Int
    val availableFunds: Int? get() = data["funds"] as? Int
    val errorMessage: String? get() = data["error"] as? String
    val rawHex: String? get() = data["hex"] as? String

    override fun toString(): String {
        val parts = mutableListOf("CashlessEvent: ${type.name} (${state.displayName})")
        if (data.isNotEmpty()) parts.add("$data")
        return parts.joinToString(" ")
    }
}

/** Vend request info */
data class VendRequest(
    val price: Int,
    val itemNumber: Int,
    val timestamp: Date = Date()
) {
    fun toBytes(): ByteArray = byteArrayOf(
        0x13.toByte(), 0x00.toByte(),
        ((price shr 8) and 0xFF).toByte(), (price and 0xFF).toByte(),
        ((itemNumber shr 8) and 0xFF).toByte(), (itemNumber and 0xFF).toByte()
    )

    override fun toString(): String = "VendRequest(price: $price, item: $itemNumber)"
}

/** Reader config info */
data class ReaderConfig(
    val featureLevel: Int,
    val countryCode: Int,
    val scaleFactor: Int,
    val decimalPlaces: Int,
    val maxResponseTime: Int,
    val rawData: List<Int>
) {
    override fun toString(): String =
        "ReaderConfig(level: $featureLevel, country: $countryCode, " +
                "scale: $scaleFactor, decimals: $decimalPlaces)"

    companion object {
        fun fromBytes(bytes: List<Int>): ReaderConfig = ReaderConfig(
            featureLevel = bytes.getOrElse(0) { 0 },
            countryCode = if (bytes.size > 2) (bytes[1] shl 8) or bytes[2] else 0,
            scaleFactor = bytes.getOrElse(3) { 1 },
            decimalPlaces = bytes.getOrElse(4) { 2 },
            maxResponseTime = bytes.getOrElse(5) { 5 },
            rawData = bytes.toList()
        )
    }
}

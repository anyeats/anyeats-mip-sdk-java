package kr.co.anyeats.gs805serial.model

import kr.co.anyeats.gs805serial.protocol.PriceLimits
import kr.co.anyeats.gs805serial.protocol.StatusCodes
import java.util.Date

/** Machine status enumeration */
enum class MachineStatus(val code: Int, val message: String) {
    READY(StatusCodes.SUCCESS, "Ready"),
    BUSY(StatusCodes.BUSY, "Busy"),
    ERROR(StatusCodes.ERROR, "Error"),
    PARAMETER_ERROR(StatusCodes.PARAMETER_ERROR, "Parameter Error"),
    INSUFFICIENT_BALANCE(StatusCodes.INSUFFICIENT_BALANCE, "Insufficient Balance"),
    CONFIG_RESTRICTED(StatusCodes.CONFIG_RESTRICTED, "Configuration Restricted"),
    DATA_NOT_FOUND(StatusCodes.DATA_NOT_FOUND, "Data Not Found"),
    CONDITIONS_NOT_MET(StatusCodes.CONDITIONS_NOT_MET, "Conditions Not Met"),
    DEVICE_NOT_FOUND(StatusCodes.DEVICE_NOT_FOUND, "Device Not Found"),
    UNKNOWN(-1, "Unknown");

    val isReady: Boolean get() = this == READY
    val isError: Boolean get() = code >= StatusCodes.ERROR && code < 0x7F

    override fun toString(): String = message

    companion object {
        fun fromCode(code: Int): MachineStatus =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Machine balance information */
data class MachineBalance(
    val balance: Int,
    val timestamp: Date = Date()
) {
    init {
        require(balance in 0..PriceLimits.MAX_BALANCE) {
            "Invalid balance: $balance (must be 0-${PriceLimits.MAX_BALANCE})"
        }
    }

    fun isSufficient(price: Int): Boolean = balance >= price
    val isEmpty: Boolean get() = balance == 0
    val isFull: Boolean get() = balance >= PriceLimits.MAX_BALANCE

    override fun toString(): String = "$balance tokens"
}

/** Changer status information */
data class ChangerStatus(
    val isDispensing: Boolean,
    val hasShaftSensorFault: Boolean,
    val hasPrismSensorFault: Boolean,
    val hasInsufficientCoins: Boolean,
    val hasMotorFault: Boolean
) {
    val hasFaults: Boolean
        get() = hasShaftSensorFault || hasPrismSensorFault || hasInsufficientCoins || hasMotorFault

    val activeFaults: List<String>
        get() {
            val faults = mutableListOf<String>()
            if (hasShaftSensorFault) faults.add("Shaft sensor fault")
            if (hasPrismSensorFault) faults.add("Prism sensor fault")
            if (hasInsufficientCoins) faults.add("Insufficient coins")
            if (hasMotorFault) faults.add("Motor fault")
            return faults
        }

    override fun toString(): String = when {
        isDispensing -> "Dispensing change"
        hasFaults -> "Faults: ${activeFaults.joinToString(", ")}"
        else -> "Ready"
    }

    companion object {
        fun fromByte(statusByte: Int): ChangerStatus = ChangerStatus(
            isDispensing = (statusByte and 0x10) != 0,
            hasShaftSensorFault = (statusByte and 0x08) != 0,
            hasPrismSensorFault = (statusByte and 0x04) != 0,
            hasInsufficientCoins = (statusByte and 0x02) != 0,
            hasMotorFault = (statusByte and 0x01) != 0
        )
    }
}

/** Complete machine state information */
data class MachineState(
    val status: MachineStatus,
    val balance: MachineBalance? = null,
    val hotTempUpper: Int? = null,
    val hotTempLower: Int? = null,
    val coldTempUpper: Int? = null,
    val coldTempLower: Int? = null,
    val cupDropMode: Int? = null,
    val timestamp: Date = Date()
) {
    override fun toString(): String {
        val parts = mutableListOf("Status: $status")
        balance?.let { parts.add("Balance: $it") }
        if (hotTempUpper != null && hotTempLower != null) {
            parts.add("Hot: $hotTempLower-${hotTempUpper}\u00B0C")
        }
        if (coldTempUpper != null && coldTempLower != null) {
            parts.add("Cold: $coldTempLower-${coldTempUpper}\u00B0C")
        }
        return parts.joinToString(", ")
    }
}

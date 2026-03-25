package kr.co.anyeats.gs805serial.model

import kr.co.anyeats.gs805serial.protocol.ActiveReportCodes
import kr.co.anyeats.gs805serial.protocol.ErrorCodeBits
import java.util.Date

/** Machine error information */
data class MachineError(
    val errorCode: Int,
    val timestamp: Date = Date()
) {
    val isFirstHeating: Boolean get() = (errorCode and ErrorCodeBits.FIRST_HEATING) != 0
    val hasNoLid: Boolean get() = (errorCode and ErrorCodeBits.NO_LID) != 0
    val hasTrackFault: Boolean get() = (errorCode and ErrorCodeBits.TRACK_FAULT) != 0
    val hasSensorFault: Boolean get() = (errorCode and ErrorCodeBits.SENSOR_FAULT) != 0
    val hasNoWaterNoCup: Boolean get() = (errorCode and ErrorCodeBits.NO_WATER_NO_CUP) != 0
    val hasNoCup: Boolean get() = (errorCode and ErrorCodeBits.NO_CUP) != 0
    val hasNoWater: Boolean get() = (errorCode and ErrorCodeBits.NO_WATER) != 0

    val hasErrors: Boolean get() = (errorCode and ErrorCodeBits.FIRST_HEATING.inv()) != 0

    val activeErrors: List<String> get() = ErrorCodeBits.getActiveErrors(errorCode)

    val preventsDrinkMaking: Boolean
        get() = hasNoWater || hasNoCup || hasNoWaterNoCup || hasSensorFault || hasTrackFault

    override fun toString(): String = when {
        !hasErrors -> if (isFirstHeating) "First heating" else "No errors"
        else -> activeErrors.joinToString(", ")
    }
}

/** Machine event type */
enum class MachineEventType(val code: Int, val description: String) {
    CUP_DROP_SUCCESS(ActiveReportCodes.CUP_DROP_SUCCESS, "Cup Drop Success"),
    DRINK_COMPLETE(ActiveReportCodes.DRINK_COMPLETE, "Drink Complete"),
    ICE_DROP_COMPLETE(ActiveReportCodes.ICE_DROP_COMPLETE, "Ice Drop Complete"),
    TRACK_OBSTACLE(ActiveReportCodes.TRACK_OBSTACLE, "Track Obstacle"),
    UNKNOWN(-1, "Unknown Event");

    override fun toString(): String = description

    companion object {
        fun fromCode(code: Int): MachineEventType =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Machine event (active report from control board) */
data class MachineEvent(
    val type: MachineEventType,
    val eventCode: Int,
    val timestamp: Date = Date(),
    val additionalData: List<Int>? = null
) {
    val isSuccess: Boolean
        get() = type == MachineEventType.CUP_DROP_SUCCESS ||
                type == MachineEventType.DRINK_COMPLETE ||
                type == MachineEventType.ICE_DROP_COMPLETE

    val isError: Boolean get() = type == MachineEventType.TRACK_OBSTACLE

    override fun toString(): String {
        val parts = mutableListOf(type.description)
        additionalData?.takeIf { it.isNotEmpty() }?.let { data ->
            parts.add("data: ${data.joinToString(" ") { "0x${it.toString(16).padStart(2, '0')}" }}")
        }
        return parts.joinToString(" - ")
    }

    companion object {
        fun fromCode(eventCode: Int, additionalData: List<Int>? = null): MachineEvent =
            MachineEvent(
                type = MachineEventType.fromCode(eventCode),
                eventCode = eventCode,
                additionalData = additionalData
            )
    }
}

/** Error severity level */
enum class ErrorSeverity(val label: String) {
    INFO("Info"),
    WARNING("Warning"),
    ERROR("Error"),
    CRITICAL("Critical");

    override fun toString(): String = label
}

/** Detailed error information with recovery suggestions */
data class ErrorInfo(
    val error: MachineError,
    val requiresUserIntervention: Boolean,
    val recoveryActions: List<String>,
    val severity: ErrorSeverity
) {
    override fun toString(): String =
        "Error: $error\nSeverity: $severity\nActions: ${recoveryActions.joinToString(", ")}"

    companion object {
        fun fromError(error: MachineError): ErrorInfo {
            val actions = mutableListOf<String>()
            var requiresIntervention = false
            var severity = ErrorSeverity.INFO

            if (error.hasNoWater) {
                actions.add("Refill water tank")
                requiresIntervention = true
                severity = ErrorSeverity.ERROR
            }
            if (error.hasNoCup) {
                actions.add("Refill cup dispenser")
                requiresIntervention = true
                severity = ErrorSeverity.ERROR
            }
            if (error.hasNoLid) {
                actions.add("Close machine lid")
                requiresIntervention = true
                severity = ErrorSeverity.WARNING
            }
            if (error.hasSensorFault) {
                actions.add("Check NTC sensor")
                requiresIntervention = true
                severity = ErrorSeverity.ERROR
            }
            if (error.hasTrackFault) {
                actions.add("Check cup track mechanism")
                requiresIntervention = true
                severity = ErrorSeverity.ERROR
            }
            if (error.isFirstHeating && !error.hasErrors) {
                actions.add("Wait for initial heating to complete")
                severity = ErrorSeverity.INFO
            }
            if (actions.isEmpty()) {
                actions.add("No action required")
            }

            return ErrorInfo(error, requiresIntervention, actions, severity)
        }
    }
}

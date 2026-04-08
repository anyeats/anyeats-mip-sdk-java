package kr.co.anyeats.gs805serial.protocol

/** Protocol header flags */
object ProtocolFlags {
    /** Command message header (from host to machine): 0xAA55 */
    const val COMMAND_HEADER = 0xAA55
    /** Response message header (from machine to host): 0xA55A */
    const val RESPONSE_HEADER = 0xA55A
    /** Minimum message length (LEN field minimum) */
    const val MIN_MESSAGE_LENGTH = 2
    /** Maximum message length (LEN field maximum) */
    const val MAX_MESSAGE_LENGTH = 255
}

/** Protocol command codes (COMND field) */
object CommandCodes {
    const val MAKE_DRINK = 0x01
    const val SET_HOT_TEMPERATURE = 0x04
    const val SET_COLD_TEMPERATURE = 0x05
    const val GET_SALES_COUNT = 0x06
    const val SET_CUP_DROP_MODE = 0x07
    const val TEST_CUP_DROP = 0x08
    const val AUTO_INSPECTION = 0x09
    const val CLEAN_ALL_PIPES = 0x0A
    const val GET_MACHINE_STATUS = 0x0B
    const val GET_ERROR_CODE = 0x0C
    const val SET_DRINK_PRICE = 0x0E
    const val GET_BALANCE = 0x0F
    const val RETURN_CHANGE = 0x10
    const val CLEAN_SPECIFIC_PIPE = 0x12
    /** 0x15: Set drink recipe water/material time (Series 2,3,R) */
    const val SET_DRINK_RECIPE_TIME = 0x15
    /** 0x1A: Unit function test (3,R series) */
    const val UNIT_FUNCTION_TEST = 0x1A
    /** 0x1B: Electronic lock control (3,R series) */
    const val ELECTRONIC_LOCK = 0x1B
    /** 0x1C: Water refill (R series) */
    const val WATER_REFILL = 0x1C
    /** 0x1D: Set drink recipe process configuration (R series) */
    const val SET_DRINK_RECIPE_PROCESS = 0x1D
    /** 0x1E: Main controller status query (R series) */
    const val GET_CONTROLLER_STATUS = 0x1E
    /** 0x1F: Drink preparation status query (R series) */
    const val GET_DRINK_STATUS = 0x1F
    /** 0x22: Object exception info query (R series) */
    const val GET_OBJECT_EXCEPTION = 0x22
    /** 0x23: Force stop operation (R series) */
    const val FORCE_STOP = 0x23
    /** 0x24: Cup delivery command (R series) */
    const val CUP_DELIVERY = 0x24
    /** 0x25: Immediate single channel execution (R series) */
    const val EXECUTE_CHANNEL = 0x25
}

/** Response status codes (STA field) */
object StatusCodes {
    const val SUCCESS = 0x00
    const val BUSY = 0x01
    const val ERROR = 0x02
    const val PARAMETER_ERROR = 0x03
    const val INSUFFICIENT_BALANCE = 0x04
    const val CONFIG_RESTRICTED = 0x05
    const val DATA_NOT_FOUND = 0x06
    const val CONDITIONS_NOT_MET = 0x07
    const val DEVICE_NOT_FOUND = 0x08
    const val ACTIVE_REPORT = 0x7F

    fun getMessage(code: Int): String = when (code) {
        SUCCESS -> "Success"
        BUSY -> "Device busy"
        ERROR -> "Device error or execution failed"
        PARAMETER_ERROR -> "Parameter error"
        INSUFFICIENT_BALANCE -> "Insufficient balance"
        CONFIG_RESTRICTED -> "Configuration restricted"
        DATA_NOT_FOUND -> "Data or recipe not found"
        CONDITIONS_NOT_MET -> "Execution conditions not met"
        DEVICE_NOT_FOUND -> "Device or function not found"
        ACTIVE_REPORT -> "Active report from control board"
        else -> "Unknown status code: 0x${code.toString(16)}"
    }

    fun isSuccess(code: Int): Boolean = code == SUCCESS
    fun isError(code: Int): Boolean = code != SUCCESS && code != ACTIVE_REPORT && code < 0x80
}

/** Drink number definitions */
object DrinkNumbers {
    const val HOT_DRINK_1 = 0x01
    const val HOT_DRINK_2 = 0x02
    const val HOT_DRINK_3 = 0x03
    const val HOT_DRINK_4 = 0x04
    const val HOT_DRINK_5 = 0x05
    const val HOT_DRINK_6 = 0x06
    const val HOT_DRINK_7 = 0x07
    const val COLD_DRINK_1 = 0x11
    const val COLD_DRINK_2 = 0x12
    const val COLD_DRINK_3 = 0x13
    const val COLD_DRINK_4 = 0x14
    const val COLD_DRINK_5 = 0x15
    const val COLD_DRINK_6 = 0x16
    const val COLD_DRINK_7 = 0x17

    fun isValid(drinkNo: Int): Boolean =
        (drinkNo in HOT_DRINK_1..HOT_DRINK_7) || (drinkNo in COLD_DRINK_1..COLD_DRINK_7)

    fun isHot(drinkNo: Int): Boolean = drinkNo in HOT_DRINK_1..HOT_DRINK_7
    fun isCold(drinkNo: Int): Boolean = drinkNo in COLD_DRINK_1..COLD_DRINK_7
}

/** Make drink command parameters */
object MakeDrinkParams {
    const val USE_LOCAL_BALANCE = 0x01
    const val DIRECT_COMMAND = 0x02
}

/** Cup drop mode parameters */
object CupDropMode {
    const val AUTOMATIC = 0x00
    const val MANUAL = 0x01
}

/** Error code bit flags (from 0x0C command response) */
object ErrorCodeBits {
    const val FIRST_HEATING = 0x80
    const val NO_LID = 0x40
    const val RESERVED_5 = 0x20
    const val TRACK_FAULT = 0x10
    const val SENSOR_FAULT = 0x08
    const val NO_WATER_NO_CUP = 0x04
    const val NO_CUP = 0x02
    const val NO_WATER = 0x01

    fun getActiveErrors(errorCode: Int): List<String> {
        val errors = mutableListOf<String>()
        if (errorCode and FIRST_HEATING != 0) errors.add("First heating after power on")
        if (errorCode and NO_LID != 0) errors.add("No lid")
        if (errorCode and TRACK_FAULT != 0) errors.add("Track fault")
        if (errorCode and SENSOR_FAULT != 0) errors.add("Sensor fault")
        if (errorCode and NO_WATER_NO_CUP != 0) errors.add("No water and no cup")
        if (errorCode and NO_CUP != 0) errors.add("No cup")
        if (errorCode and NO_WATER != 0) errors.add("No water")
        return errors
    }
}

/** Active report event codes */
object ActiveReportCodes {
    const val CUP_DROP_SUCCESS = 0x05
    const val DRINK_COMPLETE = 0x10
    const val ICE_DROP_COMPLETE = 0x06
    const val TRACK_OBSTACLE = 0x20
}

/** Communication protocol configuration */
object ProtocolConfig {
    const val BAUD_RATE = 9600
    const val DATA_BITS = 8
    const val STOP_BITS = 1
    const val PARITY = 0
    const val RESPONSE_TIMEOUT = 100
    const val MAX_RETRIES = 2
    const val BIG_ENDIAN = true
}

/** Temperature limits */
object TemperatureLimits {
    const val HOT_TEMP_MAX = 99
    const val HOT_TEMP_MIN = 60
    const val COLD_TEMP_MAX = 40
    const val COLD_TEMP_MIN = 2
    const val HOT_TEMP_DIFF = 5

    fun isValidHotTemp(high: Int, low: Int): Boolean =
        high in HOT_TEMP_MIN..HOT_TEMP_MAX &&
                low in HOT_TEMP_MIN..HOT_TEMP_MAX &&
                high >= low + HOT_TEMP_DIFF

    fun isValidColdTemp(high: Int, low: Int): Boolean =
        high in COLD_TEMP_MIN..COLD_TEMP_MAX &&
                low in COLD_TEMP_MIN..COLD_TEMP_MAX &&
                high >= low
}

/** Price limits (in token value) */
object PriceLimits {
    const val MIN = 0
    const val MAX = 99
    const val MAX_BALANCE = 99

    fun isValid(price: Int): Boolean = price in MIN..MAX
}

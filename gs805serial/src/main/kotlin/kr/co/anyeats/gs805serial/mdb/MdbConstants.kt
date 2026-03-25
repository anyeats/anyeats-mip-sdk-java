package kr.co.anyeats.gs805serial.mdb

/** MDB Device IDs (first byte of received data) */
object MdbDeviceId {
    const val COIN_ACCEPTOR = 0x08
    const val BILL_VALIDATOR = 0x30
    const val CASHLESS_1 = 0x10

    fun isCashless(id: Int): Boolean = id in 0x10..0x17
    fun isCoin(id: Int): Boolean = id == COIN_ACCEPTOR
    fun isBill(id: Int): Boolean = id == BILL_VALIDATOR
}

/** Cashless command group bytes */
object CashlessCommands {
    const val CONFIG = 0x11
    const val CONFIG_SUB_SETUP = 0x00
    const val CONFIG_SUB_MAX_MIN = 0x01

    const val VEND = 0x13
    const val VEND_SUB_REQUEST = 0x00
    const val VEND_SUB_CANCEL = 0x01
    const val VEND_SUB_SUCCESS = 0x02
    const val VEND_SUB_SESSION_COMPLETE = 0x04
    const val VEND_SUB_CASH_SALE = 0x05

    const val READER_CONTROL = 0x14
    const val READER_SUB_DISABLE = 0x00
    const val READER_SUB_ENABLE = 0x01
    const val READER_SUB_CANCEL = 0x02

    const val REVALUE = 0x15
    const val REVALUE_SUB_REQUEST = 0x00

    const val EXPANSION = 0x17
    const val EXPANSION_SUB_REQUEST_ID = 0x00
}

/** Cashless reader response codes */
object CashlessResponse {
    const val ACK = 0x00
    const val JUST_RESET = 0x00
    const val CONFIG_DATA = 0x01
    const val BEGIN_SESSION = 0x03
    const val SESSION_CANCEL_REQUEST = 0x04
    const val VEND_APPROVED = 0x05
    const val VEND_DENIED = 0x06
    const val END_SESSION = 0x07
    const val CANCELLED = 0x08
    const val PERIPHERAL_ID = 0x09
    const val OUT_OF_SEQUENCE = 0x0B
    const val REVALUE_APPROVED = 0x0D
    const val REVALUE_DENIED = 0x0E
    const val DIAGNOSTICS = 0x0F
}

/** MDB-RS232 communication config */
object MdbConfig {
    const val BAUD_RATE = 9600
    const val DATA_BITS = 8
    const val STOP_BITS = 1
    const val PARITY = 0
    const val COMMAND_TIMEOUT = 200
    const val SESSION_TIMEOUT = 30000
}

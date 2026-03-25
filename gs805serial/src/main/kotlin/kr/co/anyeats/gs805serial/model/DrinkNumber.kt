package kr.co.anyeats.gs805serial.model

import kr.co.anyeats.gs805serial.protocol.PriceLimits
import kr.co.anyeats.gs805serial.protocol.TemperatureLimits
import kr.co.anyeats.gs805serial.protocol.CupDropMode

/** Drink number enumeration */
enum class DrinkNumber(val code: Int, val displayName: String, val isHot: Boolean) {
    HOT_DRINK_1(0x01, "Hot Drink 1", true),
    HOT_DRINK_2(0x02, "Hot Drink 2", true),
    HOT_DRINK_3(0x03, "Hot Drink 3", true),
    HOT_DRINK_4(0x04, "Hot Drink 4", true),
    HOT_DRINK_5(0x05, "Hot Drink 5", true),
    HOT_DRINK_6(0x06, "Hot Drink 6", true),
    HOT_DRINK_7(0x07, "Hot Drink 7", true),
    COLD_DRINK_1(0x11, "Cold Drink 1", false),
    COLD_DRINK_2(0x12, "Cold Drink 2", false),
    COLD_DRINK_3(0x13, "Cold Drink 3", false),
    COLD_DRINK_4(0x14, "Cold Drink 4", false),
    COLD_DRINK_5(0x15, "Cold Drink 5", false),
    COLD_DRINK_6(0x16, "Cold Drink 6", false),
    COLD_DRINK_7(0x17, "Cold Drink 7", false);

    val isCold: Boolean get() = !isHot

    override fun toString(): String = displayName

    companion object {
        fun fromCode(code: Int): DrinkNumber? = entries.firstOrNull { it.code == code }

        val hotDrinks: List<DrinkNumber> get() = entries.filter { it.isHot }
        val coldDrinks: List<DrinkNumber> get() = entries.filter { it.isCold }
    }
}

/** Drink price information */
data class DrinkPrice(
    val drink: DrinkNumber,
    val price: Int
) {
    init {
        require(PriceLimits.isValid(price)) {
            "Invalid price: $price (must be ${PriceLimits.MIN}-${PriceLimits.MAX})"
        }
    }

    override fun toString(): String = "$drink: $price tokens"
}

/** Drink sales statistics */
data class DrinkSalesCount(
    val drink: DrinkNumber,
    val localSalesCount: Int,
    val commandSalesCount: Int
) {
    init {
        require(localSalesCount >= 0) { "Invalid local sales count: $localSalesCount" }
        require(commandSalesCount >= 0) { "Invalid command sales count: $commandSalesCount" }
    }

    val totalCount: Int get() = localSalesCount + commandSalesCount

    override fun toString(): String =
        "$drink: total=$totalCount (local=$localSalesCount, cmd=$commandSalesCount)"
}

/** Temperature settings for drinks */
data class TemperatureSettings(
    val upperLimit: Int,
    val lowerLimit: Int,
    val isHot: Boolean
) {
    init {
        if (isHot) {
            require(TemperatureLimits.isValidHotTemp(upperLimit, lowerLimit)) {
                "Invalid hot temperature range: upper=$upperLimit, lower=$lowerLimit"
            }
        } else {
            require(TemperatureLimits.isValidColdTemp(upperLimit, lowerLimit)) {
                "Invalid cold temperature range: upper=$upperLimit, lower=$lowerLimit"
            }
        }
    }

    val temperatureDifference: Int get() = upperLimit - lowerLimit

    override fun toString(): String =
        "${if (isHot) "Hot" else "Cold"} temperature: $lowerLimit\u00B0C - $upperLimit\u00B0C"

    companion object {
        fun hot(upperLimit: Int, lowerLimit: Int): TemperatureSettings =
            TemperatureSettings(upperLimit, lowerLimit, true)

        fun cold(upperLimit: Int, lowerLimit: Int): TemperatureSettings =
            TemperatureSettings(upperLimit, lowerLimit, false)
    }
}

/** Cup drop mode */
enum class CupDropModeEnum(val code: Int, val displayName: String) {
    AUTOMATIC(CupDropMode.AUTOMATIC, "Automatic"),
    MANUAL(CupDropMode.MANUAL, "Manual");

    override fun toString(): String = displayName

    companion object {
        fun fromCode(code: Int): CupDropModeEnum? = entries.firstOrNull { it.code == code }
    }
}

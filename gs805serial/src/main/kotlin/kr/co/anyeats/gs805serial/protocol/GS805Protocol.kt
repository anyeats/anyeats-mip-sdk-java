package kr.co.anyeats.gs805serial.protocol

import kr.co.anyeats.gs805serial.model.RecipeStep

/** Protocol utility class for GS805 serial communication */
object GS805Protocol {

    fun calculateChecksum(bytes: ByteArray): Int {
        var sum = 0
        for (b in bytes) {
            sum += b.toInt() and 0xFF
        }
        return sum and 0xFF
    }

    fun verifyChecksum(message: ByteArray): Boolean {
        if (message.size < 5) return false
        val checksumBytes = message.copyOfRange(0, message.size - 1)
        val calculated = calculateChecksum(checksumBytes)
        val received = message[message.size - 1].toInt() and 0xFF
        return calculated == received
    }

    fun int16ToBigEndian(value: Int): ByteArray {
        require(value in 0..0xFFFF) { "Value out of range for 16-bit integer: $value" }
        return byteArrayOf(
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    fun bigEndianToInt16(bytes: ByteArray, offset: Int = 0): Int {
        require(offset + 1 < bytes.size) { "Not enough bytes for 16-bit integer" }
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    fun int32ToBigEndian(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    fun bigEndianToInt32(bytes: ByteArray, offset: Int = 0): Int {
        require(offset + 3 < bytes.size) { "Not enough bytes for 32-bit integer" }
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }

    /** Find message start position in byte stream */
    fun findMessageStart(bytes: ByteArray, isResponse: Boolean = false): Int {
        if (bytes.size < 2) return -1
        val targetFlag = if (isResponse) ProtocolFlags.RESPONSE_HEADER else ProtocolFlags.COMMAND_HEADER
        for (i in 0 until bytes.size - 1) {
            val flag = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            if (flag == targetFlag) return i
        }
        return -1
    }

    data class ExtractResult(val message: ByteArray?, val consumed: Int)

    /** Extract complete message from byte stream */
    fun extractMessage(bytes: ByteArray, isResponse: Boolean = false): ExtractResult {
        val startOffset = findMessageStart(bytes, isResponse)
        if (startOffset == -1) return ExtractResult(null, bytes.size)
        if (startOffset + 3 > bytes.size) return ExtractResult(null, 0)

        val len = bytes[startOffset + 2].toInt() and 0xFF
        val totalLength = 3 + len

        if (startOffset + totalLength > bytes.size) return ExtractResult(null, 0)

        val message = bytes.copyOfRange(startOffset, startOffset + totalLength)
        if (!verifyChecksum(message)) return ExtractResult(null, startOffset + totalLength)

        return ExtractResult(message, startOffset + totalLength)
    }

    // ========== Command Factory Methods ==========

    fun makeDrinkCommand(drinkNumber: Int, useLocalBalance: Boolean = false): CommandMessage {
        require(DrinkNumbers.isValid(drinkNumber)) { "Invalid drink number: 0x${drinkNumber.toString(16)}" }
        return CommandMessage(
            CommandCodes.MAKE_DRINK,
            byteArrayOf(
                drinkNumber.toByte(),
                (if (useLocalBalance) MakeDrinkParams.USE_LOCAL_BALANCE else MakeDrinkParams.DIRECT_COMMAND).toByte()
            )
        )
    }

    fun setHotTemperatureCommand(high: Int, low: Int): CommandMessage {
        require(TemperatureLimits.isValidHotTemp(high, low)) {
            "Invalid hot temperature range: high=$high, low=$low"
        }
        return CommandMessage(CommandCodes.SET_HOT_TEMPERATURE, byteArrayOf(high.toByte(), low.toByte()))
    }

    fun setColdTemperatureCommand(high: Int, low: Int): CommandMessage {
        require(TemperatureLimits.isValidColdTemp(high, low)) {
            "Invalid cold temperature range: high=$high, low=$low"
        }
        return CommandMessage(CommandCodes.SET_COLD_TEMPERATURE, byteArrayOf(high.toByte(), low.toByte()))
    }

    fun getSalesCountCommand(drinkNumber: Int): CommandMessage {
        require(DrinkNumbers.isValid(drinkNumber)) { "Invalid drink number: 0x${drinkNumber.toString(16)}" }
        return CommandMessage.withByte(CommandCodes.GET_SALES_COUNT, drinkNumber)
    }

    fun setCupDropModeCommand(mode: Int): CommandMessage {
        require(mode == CupDropMode.AUTOMATIC || mode == CupDropMode.MANUAL) { "Invalid cup drop mode: $mode" }
        return CommandMessage.withByte(CommandCodes.SET_CUP_DROP_MODE, mode)
    }

    fun testCupDropCommand(): CommandMessage = CommandMessage(CommandCodes.TEST_CUP_DROP)

    fun autoInspectionCommand(): CommandMessage = CommandMessage(CommandCodes.AUTO_INSPECTION)

    fun cleanAllPipesCommand(): CommandMessage = CommandMessage(CommandCodes.CLEAN_ALL_PIPES)

    fun getMachineStatusCommand(): CommandMessage = CommandMessage(CommandCodes.GET_MACHINE_STATUS)

    fun getErrorCodeCommand(): CommandMessage = CommandMessage(CommandCodes.GET_ERROR_CODE)

    fun setDrinkPriceCommand(drinkNumber: Int, price: Int): CommandMessage {
        require(DrinkNumbers.isValid(drinkNumber)) { "Invalid drink number: 0x${drinkNumber.toString(16)}" }
        require(PriceLimits.isValid(price)) { "Invalid price: $price (must be ${PriceLimits.MIN}-${PriceLimits.MAX})" }
        return CommandMessage(CommandCodes.SET_DRINK_PRICE, byteArrayOf(drinkNumber.toByte(), price.toByte()))
    }

    fun getBalanceCommand(): CommandMessage = CommandMessage(CommandCodes.GET_BALANCE)

    fun returnChangeCommand(): CommandMessage = CommandMessage(CommandCodes.RETURN_CHANGE)

    fun cleanSpecificPipeCommand(pipeNumber: Int): CommandMessage {
        require(pipeNumber in 0..255) { "Invalid pipe number: $pipeNumber" }
        return CommandMessage.withByte(CommandCodes.CLEAN_SPECIFIC_PIPE, pipeNumber)
    }

    /**
     * Create a command to set drink recipe time (0x15, Series 2,3,R).
     * Sets material duration and water amount for each channel (1-8).
     * Sequential order (no reordering).
     *
     * @param drinkNumber Drink number (0x01-0x07 hot, 0x11-0x17 cold)
     * @param channelTimes List of 8 pairs: (materialDuration, waterAmount) in 0.1s units (0-999).
     *                     (0,0) = channel disabled.
     */
    fun setDrinkRecipeTimeCommand(
        drinkNumber: Int,
        channelTimes: List<Pair<Int, Int>>
    ): CommandMessage {
        require(DrinkNumbers.isValid(drinkNumber)) { "Invalid drink number: 0x${drinkNumber.toString(16)}" }
        require(channelTimes.size == 8) { "Must provide exactly 8 channel times, got ${channelTimes.size}" }

        // Sequential order (no reordering)
        val dataList = mutableListOf<Byte>(drinkNumber.toByte())
        for ((mat, wat) in channelTimes) {
            dataList.add(((mat shr 8) and 0xFF).toByte())
            dataList.add((mat and 0xFF).toByte())
            dataList.add(((wat shr 8) and 0xFF).toByte())
            dataList.add((wat and 0xFF).toByte())
        }
        return CommandMessage(CommandCodes.SET_DRINK_RECIPE_TIME, dataList.toByteArray())
    }

    /** Create a command to set drink recipe process (0x1D) */
    fun setDrinkRecipeProcessCommand(drinkNumber: Int, steps: List<RecipeStep>): CommandMessage {
        require(DrinkNumbers.isValid(drinkNumber)) { "Invalid drink number: 0x${drinkNumber.toString(16)}" }
        require(steps.isNotEmpty() && steps.size <= 32) { "Steps must be 1-32, got ${steps.size}" }

        val dataList = mutableListOf<Byte>(drinkNumber.toByte())
        for (step in steps) {
            for (b in step.toBytes()) {
                dataList.add(b)
            }
        }
        return CommandMessage(CommandCodes.SET_DRINK_RECIPE_PROCESS, dataList.toByteArray())
    }

    /** Create a unit function test command (0x1A) */
    fun unitFunctionTestCommand(testCmd: Int, data1: Int, data2: Int, data3: Int): CommandMessage {
        require(testCmd in 0x01..0x06) { "Invalid test command: 0x${testCmd.toString(16)}" }
        require(data1 in 0..255) { "Invalid data1: $data1" }
        require(data2 in 0..255) { "Invalid data2: $data2" }
        require(data3 in 0..255) { "Invalid data3: $data3" }
        return CommandMessage(
            CommandCodes.UNIT_FUNCTION_TEST,
            byteArrayOf(testCmd.toByte(), data1.toByte(), data2.toByte(), data3.toByte())
        )
    }

    /** Create an electronic lock command (0x1B) */
    fun electronicLockCommand(lockNumber: Int = 0x01, operation: Int): CommandMessage {
        require(lockNumber in 0x01..0xFF) { "Invalid lock number: $lockNumber" }
        require(operation in 0x00..0x02) { "Invalid operation: $operation (0=unlock, 1=lock, 2=query)" }
        return CommandMessage(
            CommandCodes.ELECTRONIC_LOCK,
            byteArrayOf(lockNumber.toByte(), operation.toByte())
        )
    }

    /** Create a water refill command (0x1C) */
    fun waterRefillCommand(): CommandMessage {
        return CommandMessage(CommandCodes.WATER_REFILL, byteArrayOf(0x1D.toByte()))
    }

    /** Create a main controller status query command (0x1E) */
    fun getControllerStatusCommand(): CommandMessage {
        return CommandMessage(CommandCodes.GET_CONTROLLER_STATUS, byteArrayOf(0x1F.toByte()))
    }

    /** Create a drink preparation status query command (0x1F) */
    fun getDrinkStatusCommand(): CommandMessage {
        return CommandMessage(CommandCodes.GET_DRINK_STATUS, byteArrayOf(0x20.toByte()))
    }

    /** Create an object exception info query command (0x22) */
    fun getObjectExceptionCommand(objectNumber: Int): CommandMessage {
        require(objectNumber in 0x0000..0xFFFF) { "Invalid object number: $objectNumber" }
        val objBytes = int16ToBigEndian(objectNumber)
        return CommandMessage(
            CommandCodes.GET_OBJECT_EXCEPTION,
            byteArrayOf(0x00.toByte(), objBytes[0], objBytes[1])
        )
    }

    /** Create a force stop command (0x23) */
    fun forceStopCommand(targetCommand: Int): CommandMessage {
        require(targetCommand == 0x01 || targetCommand == 0x24) {
            "Invalid target command: 0x${targetCommand.toString(16)} (must be 0x01 or 0x24)"
        }
        return CommandMessage(CommandCodes.FORCE_STOP, byteArrayOf(targetCommand.toByte()))
    }

    /** Create a cup delivery command (0x24) */
    fun cupDeliveryCommand(waitTime: Int): CommandMessage {
        require(waitTime in 0x01..0xFF) { "Invalid wait time: $waitTime (must be 1-255 seconds)" }
        return CommandMessage(
            CommandCodes.CUP_DELIVERY,
            byteArrayOf(0x00.toByte(), waitTime.toByte())
        )
    }

    /** Create a command for immediate single channel execution (0x25) */
    fun executeChannelCommand(
        channel: Int,
        waterType: Int = 0,
        materialDuration: Int = 0,
        waterAmount: Int = 0,
        materialSpeed: Int = 50,
        mixSpeed: Int = 0,
        subChannel: Int = -1,
        subMaterialDuration: Int = 0,
        subMaterialSpeed: Int = 0,
        endWaitTime: Int = 0,
        removeParamLimits: Boolean = false
    ): CommandMessage {
        return CommandMessage(
            CommandCodes.EXECUTE_CHANNEL,
            byteArrayOf(
                (channel and 0xFF).toByte(),
                (waterType and 0xFF).toByte(),
                ((materialDuration shr 8) and 0xFF).toByte(), (materialDuration and 0xFF).toByte(),
                ((waterAmount shr 8) and 0xFF).toByte(), (waterAmount and 0xFF).toByte(),
                (materialSpeed and 0xFF).toByte(),
                (mixSpeed and 0xFF).toByte(),
                (subChannel and 0xFF).toByte(),
                ((subMaterialDuration shr 8) and 0xFF).toByte(), (subMaterialDuration and 0xFF).toByte(),
                (subMaterialSpeed and 0xFF).toByte(),
                (endWaitTime and 0xFF).toByte(),
            )
        )
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    fun hexToBytes(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").uppercase()
        require(cleaned.length % 2 == 0) { "Invalid hex string length" }
        return ByteArray(cleaned.length / 2) { i ->
            val byteStr = cleaned.substring(i * 2, i * 2 + 2)
            byteStr.toInt(16).toByte()
        }
    }
}

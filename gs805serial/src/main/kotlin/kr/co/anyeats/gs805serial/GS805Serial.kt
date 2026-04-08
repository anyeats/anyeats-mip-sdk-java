package kr.co.anyeats.gs805serial

import android.content.Context
import kotlinx.coroutines.flow.*
import kr.co.anyeats.gs805serial.exception.*
import kr.co.anyeats.gs805serial.model.*
import kr.co.anyeats.gs805serial.protocol.*
import kr.co.anyeats.gs805serial.serial.*
import kr.co.anyeats.gs805serial.util.*

/**
 * High-level GS805 coffee machine controller.
 *
 * ```kotlin
 * val gs805 = GS805Serial(context)
 * val devices = gs805.listDevices()
 * gs805.connect(devices.first())
 * gs805.makeDrink(DrinkNumber.HOT_DRINK_1)
 * gs805.disconnect()
 * ```
 */
class GS805Serial(
    context: Context,
    connection: SerialConnection? = null,
    reconnectConfig: ReconnectConfig? = null,
    val enableLogging: Boolean = false,
    val enableCommandQueue: Boolean = false
) {
    private val _connection: SerialConnection = connection ?: UsbSerialConnection(context)
    private val _manager: SerialManager = SerialManager(_connection, reconnectConfig)
    private var _commandQueue: CommandQueue? = null
    private val _logger = GS805Logger

    init {
        if (enableLogging) {
            _logger.info("GS805Serial", "Logger initialized")
        }
        if (enableCommandQueue) {
            _commandQueue = CommandQueue { cmd -> _manager.sendCommand(cmd) }
            if (enableLogging) {
                _logger.info("GS805Serial", "Command queue initialized")
            }
        }
    }

    // ========== Connection Management ==========

    suspend fun listDevices(): List<SerialDevice> = _connection.listDevices()

    suspend fun connect(device: SerialDevice, config: SerialConfig? = null) {
        if (enableLogging) _logger.info("Connection", "Connecting to ${device.name}...")
        try {
            _manager.connect(device, config ?: SerialConfig.GS805)
            if (enableLogging) _logger.info("Connection", "Connected successfully to ${device.name}")
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Connection", "Failed to connect to ${device.name}", e)
            throw e
        }
    }

    suspend fun connectToFirstDevice(config: SerialConfig? = null) {
        val devices = listDevices()
        if (devices.isEmpty()) throw ConnectionException("No devices found")
        connect(devices.first(), config)
    }

    suspend fun connectByVidPid(vendorId: Int, productId: Int, config: SerialConfig? = null) {
        val devices = listDevices()
        val device = devices.firstOrNull { it.vendorId == vendorId && it.productId == productId }
            ?: throw ConnectionException(
                "Device not found: VID=${vendorId.toString(16)}, PID=${productId.toString(16)}"
            )
        connect(device, config)
    }

    suspend fun disconnect(clearReconnection: Boolean = true) {
        if (enableLogging) _logger.info("Connection", "Disconnecting...")
        _manager.disconnect(clearReconnection)
        if (enableLogging) _logger.info("Connection", "Disconnected")
    }

    val isConnected: Boolean get() = _manager.isConnected
    val connectedDevice: SerialDevice? get() = _manager.connectedDevice
    val isReconnecting: Boolean get() = _manager.isReconnecting

    // ========== Drink Making ==========

    suspend fun makeDrink(drink: DrinkNumber, useLocalBalance: Boolean = false, timeoutMs: Long = 100) {
        ensureConnected()
        if (enableLogging) _logger.info("Drink", "Making ${drink.displayName}...")

        val command = GS805Protocol.makeDrinkCommand(drink.code, useLocalBalance)
        try {
            if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command, timeoutMs = timeoutMs).await()
            } else {
                _manager.sendCommand(command, timeoutMs = timeoutMs)
            }
            if (enableLogging) _logger.info("Drink", "${drink.displayName} command sent successfully")
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Drink", "Failed to make ${drink.displayName}", e)
            throw e
        }
    }

    /**
     * Set drink recipe process configuration (R series only).
     * Defines a custom multi-step recipe for a drink number.
     * After setting, call [makeDrink] with the same drink number to execute.
     */
    suspend fun setDrinkRecipeProcess(drink: DrinkNumber, steps: List<RecipeStep>) {
        ensureConnected()
        if (enableLogging) _logger.info("Recipe", "Setting recipe for ${drink.displayName} with ${steps.size} steps...")

        val command = GS805Protocol.setDrinkRecipeProcessCommand(drink.code, steps)
        try {
            if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            if (enableLogging) _logger.info("Recipe", "Recipe set successfully for ${drink.displayName}")
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Recipe", "Failed to set recipe for ${drink.displayName}", e)
            throw e
        }
    }

    /**
     * Execute a single channel immediately (R series only).
     * Directly controls a single dispensing channel without saving a recipe.
     *
     * @param channel Channel number (0-based, 0 = 1st channel)
     * @param waterType Water type (HOT or COLD)
     * @param materialDuration Material dispensing time in 0.1s units (0-999)
     * @param waterAmount Water amount in 0.1mL units (0-999)
     * @param materialSpeed Material speed 0-100%
     * @param mixSpeed Mixing speed 0-100%
     * @param subChannel Sub channel (-1 to 127, -1 = none)
     * @param subMaterialDuration Sub material time in 0.1s units (0-999)
     * @param subMaterialSpeed Sub material speed 0-100%
     * @param endWaitTime Wait time after completion in seconds (0-255)
     * @param removeParamLimits If true, removes default parameter range limits
     */
    suspend fun executeChannel(
        channel: Int,
        waterType: WaterType = WaterType.HOT,
        materialDuration: Int = 0,
        waterAmount: Int = 0,
        materialSpeed: Int = 50,
        mixSpeed: Int = 0,
        subChannel: Int = -1,
        subMaterialDuration: Int = 0,
        subMaterialSpeed: Int = 0,
        endWaitTime: Int = 0,
        removeParamLimits: Boolean = false
    ) {
        ensureConnected()
        if (enableLogging) _logger.info("Channel", "Executing channel $channel...")

        val command = GS805Protocol.executeChannelCommand(
            channel = channel,
            waterType = waterType.code,
            materialDuration = materialDuration,
            waterAmount = waterAmount,
            materialSpeed = materialSpeed,
            mixSpeed = mixSpeed,
            subChannel = subChannel,
            subMaterialDuration = subMaterialDuration,
            subMaterialSpeed = subMaterialSpeed,
            endWaitTime = endWaitTime,
            removeParamLimits = removeParamLimits
        )

        try {
            if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            if (enableLogging) _logger.info("Channel", "Channel $channel executed successfully")
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Channel", "Failed to execute channel $channel", e)
            throw e
        }
    }

    // ========== Recipe Time (0x15) ==========

    /**
     * Set drink recipe time (0x15, Series 2,3,R).
     * Sets material duration and water amount for each channel (1-8).
     *
     * @param drink Drink number
     * @param channelTimes 8 pairs of (materialDuration, waterAmount) in 0.1s units.
     *                     (0,0) = channel disabled.
     */
    suspend fun setDrinkRecipeTime(drink: DrinkNumber, channelTimes: List<Pair<Int, Int>>) {
        ensureConnected()
        if (enableLogging) _logger.info("Recipe", "Setting recipe time for ${drink.displayName}...")

        val command = GS805Protocol.setDrinkRecipeTimeCommand(drink.code, channelTimes)
        try {
            if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            if (enableLogging) _logger.info("Recipe", "Recipe time set successfully for ${drink.displayName}")
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Recipe", "Failed to set recipe time for ${drink.displayName}", e)
            throw e
        }
    }

    // ========== Temperature Control ==========

    suspend fun setHotTemperature(upperLimit: Int, lowerLimit: Int) {
        ensureConnected()
        _manager.sendCommand(GS805Protocol.setHotTemperatureCommand(upperLimit, lowerLimit))
    }

    suspend fun setColdTemperature(upperLimit: Int, lowerLimit: Int) {
        ensureConnected()
        _manager.sendCommand(GS805Protocol.setColdTemperatureCommand(upperLimit, lowerLimit))
    }

    // ========== Information Queries ==========

    suspend fun getSalesCount(drink: DrinkNumber): DrinkSalesCount {
        ensureConnected()
        val response = _manager.sendCommand(GS805Protocol.getSalesCountCommand(drink.code))
        val drinkNo = response.getDataByte(0) ?: 0
        val localNum = response.getDataDWord(1) ?: 0
        val cmdNum = response.getDataDWord(5) ?: 0
        return DrinkSalesCount(
            drink = DrinkNumber.fromCode(drinkNo) ?: DrinkNumber.HOT_DRINK_1,
            localSalesCount = localNum,
            commandSalesCount = cmdNum
        )
    }

    suspend fun getMachineStatus(): MachineStatus {
        ensureConnected()
        val response = _manager.sendCommand(GS805Protocol.getMachineStatusCommand())
        return MachineStatus.fromCode(response.statusCode ?: 0)
    }

    suspend fun getErrorCode(): MachineError {
        ensureConnected()
        val response = _manager.sendCommand(GS805Protocol.getErrorCodeCommand())
        val errorCode = response.getDataByte(0) ?: 0
        return MachineError(errorCode = errorCode)
    }

    suspend fun getErrorInfo(): ErrorInfo {
        val error = getErrorCode()
        return ErrorInfo.fromError(error)
    }

    suspend fun getBalance(): MachineBalance {
        ensureConnected()
        val response = _manager.sendCommand(GS805Protocol.getBalanceCommand())
        val balance = response.getDataByte(0) ?: 0
        return MachineBalance(balance = balance)
    }

    // ========== Machine Control ==========

    suspend fun setCupDropMode(mode: CupDropModeEnum) {
        ensureConnected()
        _manager.sendCommand(GS805Protocol.setCupDropModeCommand(mode.code))
    }

    suspend fun testCupDrop() {
        ensureConnected()
        _manager.sendCommand(GS805Protocol.testCupDropCommand())
    }

    suspend fun autoInspection() {
        ensureConnected()
        _manager.sendCommand(GS805Protocol.autoInspectionCommand())
    }

    suspend fun cleanAllPipes() {
        ensureConnected()
        _manager.sendCommand(GS805Protocol.cleanAllPipesCommand())
    }

    suspend fun cleanSpecificPipe(pipeNumber: Int) {
        ensureConnected()
        _manager.sendCommand(GS805Protocol.cleanSpecificPipeCommand(pipeNumber))
    }

    suspend fun returnChange(): ChangerStatus {
        ensureConnected()
        val response = _manager.sendCommand(GS805Protocol.returnChangeCommand())
        val statusByte = response.getDataByte(0) ?: 0
        return ChangerStatus.fromByte(statusByte)
    }

    suspend fun setDrinkPrice(drink: DrinkNumber, price: Int) {
        ensureConnected()
        _manager.sendCommand(GS805Protocol.setDrinkPriceCommand(drink.code, price))
    }

    // ========== Unit Function Test (R series) ==========

    /**
     * Execute a unit function test (0x1A, 3/R series).
     *
     * @param testCmd Test command type (1=dispensing, 2=coordinate, 3=front door, 4=ice, 5=IO, 6=combined)
     * @param data1 Test-specific parameter 1
     * @param data2 Test-specific parameter 2
     * @param data3 Test-specific parameter 3
     */
    suspend fun unitFunctionTest(testCmd: Int, data1: Int, data2: Int, data3: Int) {
        ensureConnected()
        if (enableLogging) _logger.info("Test", "Unit function test: cmd=$testCmd, data=[$data1, $data2, $data3]")

        val command = GS805Protocol.unitFunctionTestCommand(testCmd, data1, data2, data3)
        try {
            if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            if (enableLogging) _logger.info("Test", "Unit function test command sent successfully")
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Test", "Failed to execute unit function test", e)
            throw e
        }
    }

    // ========== Electronic Lock (3,R series) ==========

    /**
     * Lock the electronic lock (0x1B).
     * @param lockNumber Lock device number (default 1)
     * @return LockStatus with execution result and lock state
     */
    suspend fun lockDoor(lockNumber: Int = 1): LockStatus {
        ensureConnected()
        if (enableLogging) _logger.info("Lock", "Locking door (lock #$lockNumber)...")

        val command = GS805Protocol.electronicLockCommand(lockNumber, LockOperation.LOCK.code)
        try {
            val response = if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            val lockS = response.getDataByte(0) ?: 0
            val status = LockStatus.fromByte(lockS)
            if (enableLogging) _logger.info("Lock", "Lock result: $status")
            return status
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Lock", "Failed to lock door", e)
            throw e
        }
    }

    /**
     * Unlock the electronic lock (0x1B).
     * @param lockNumber Lock device number (default 1)
     * @return LockStatus with execution result and lock state
     */
    suspend fun unlockDoor(lockNumber: Int = 1): LockStatus {
        ensureConnected()
        if (enableLogging) _logger.info("Lock", "Unlocking door (lock #$lockNumber)...")

        val command = GS805Protocol.electronicLockCommand(lockNumber, LockOperation.UNLOCK.code)
        try {
            val response = if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            val lockS = response.getDataByte(0) ?: 0
            val status = LockStatus.fromByte(lockS)
            if (enableLogging) _logger.info("Lock", "Unlock result: $status")
            return status
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Lock", "Failed to unlock door", e)
            throw e
        }
    }

    /**
     * Query electronic lock status (0x1B).
     * @param lockNumber Lock device number (default 1)
     * @return LockStatus with execution result and lock state
     */
    suspend fun getLockStatus(lockNumber: Int = 1): LockStatus {
        ensureConnected()
        if (enableLogging) _logger.info("Lock", "Querying lock status (lock #$lockNumber)...")

        val command = GS805Protocol.electronicLockCommand(lockNumber, LockOperation.QUERY.code)
        try {
            val response = if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            val lockS = response.getDataByte(0) ?: 0
            val status = LockStatus.fromByte(lockS)
            if (enableLogging) _logger.info("Lock", "Lock status: $status")
            return status
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Lock", "Failed to query lock status", e)
            throw e
        }
    }

    // ========== Water Refill (R series) ==========

    /**
     * Trigger water refill (0x1C, R series).
     */
    suspend fun waterRefill() {
        ensureConnected()
        if (enableLogging) _logger.info("Water", "Triggering water refill...")

        val command = GS805Protocol.waterRefillCommand()
        try {
            if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            if (enableLogging) _logger.info("Water", "Water refill command sent successfully")
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Water", "Failed to trigger water refill", e)
            throw e
        }
    }

    // ========== Controller & Drink Status (R series) ==========

    /**
     * Query main controller status (0x1E, R series).
     * Returns detailed bit-field status of all machine subsystems.
     */
    suspend fun getControllerStatus(): ControllerStatus {
        ensureConnected()
        if (enableLogging) _logger.info("Status", "Querying controller status...")

        val command = GS805Protocol.getControllerStatusCommand()
        try {
            val response = if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            // 0x1E response has no STA byte; data is ST_INFO(4) + optional drink_NO(1)
            val data = response.data
            val stInfo = if (data.size >= 4) GS805Protocol.bigEndianToInt32(data, 0) else 0
            val drinkNo = if (data.size >= 5) data[4].toInt() and 0xFF else 0
            val status = ControllerStatus.fromRawData(stInfo, drinkNo)
            if (enableLogging) _logger.info("Status", "Controller status: $status")
            return status
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Status", "Failed to query controller status", e)
            throw e
        }
    }

    /**
     * Query drink preparation status (0x1F, R series).
     * Returns detailed progress of current drink being prepared.
     */
    suspend fun getDrinkStatus(): DrinkPreparationStatus {
        ensureConnected()
        if (enableLogging) _logger.info("Status", "Querying drink preparation status...")

        val command = GS805Protocol.getDrinkStatusCommand()
        try {
            val response = if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            // 0x1F response has no STA byte; data is DK_INFO(4bytes)
            val data = response.data
            val dkInfo = if (data.size >= 4) GS805Protocol.bigEndianToInt32(data, 0) else 0
            val status = DrinkPreparationStatus.fromRawData(dkInfo)
            if (enableLogging) _logger.info("Status", "Drink status: $status")
            return status
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Status", "Failed to query drink preparation status", e)
            throw e
        }
    }

    // ========== Object Exception Query (R series) ==========

    /**
     * Query object exception info (0x22, R series).
     * @param objectType The object type to query
     * @return ObjectExceptionInfo with detailed exception data
     */
    suspend fun getObjectException(objectType: ObjectType): ObjectExceptionInfo {
        ensureConnected()
        if (enableLogging) _logger.info("Exception", "Querying exception for ${objectType.description}...")

        val command = GS805Protocol.getObjectExceptionCommand(objectType.code)
        try {
            val response = if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            val sta = response.statusCode ?: 0
            val stInfo = response.getDataDWord(0) ?: 0
            val opInfo = response.getDataWord(4) ?: 0
            val objNo = response.getDataWord(6) ?: 0
            val infoCode = response.getDataWord(8) ?: 0
            val autInfo = response.getDataByte(10) ?: 0
            val info = ObjectExceptionInfo(
                statusCode = sta,
                stInfo = stInfo,
                opInfo = opInfo,
                objectNumber = objNo,
                infoCode = infoCode,
                autInfo = autInfo
            )
            if (enableLogging) _logger.info("Exception", "Exception info: $info")
            return info
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Exception", "Failed to query object exception", e)
            throw e
        }
    }

    // ========== Force Stop (R series) ==========

    /**
     * Force stop drink process (0x23, R series).
     */
    suspend fun forceStopDrinkProcess() {
        ensureConnected()
        if (enableLogging) _logger.info("Control", "Force stopping drink process...")

        val command = GS805Protocol.forceStopCommand(ForceStopTarget.DRINK_PROCESS.code)
        try {
            if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            if (enableLogging) _logger.info("Control", "Force stop drink process command sent successfully")
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Control", "Failed to force stop drink process", e)
            throw e
        }
    }

    /**
     * Force stop cup delivery (0x23, R series).
     */
    suspend fun forceStopCupDelivery() {
        ensureConnected()
        if (enableLogging) _logger.info("Control", "Force stopping cup delivery...")

        val command = GS805Protocol.forceStopCommand(ForceStopTarget.CUP_DELIVERY.code)
        try {
            if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            if (enableLogging) _logger.info("Control", "Force stop cup delivery command sent successfully")
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Control", "Failed to force stop cup delivery", e)
            throw e
        }
    }

    // ========== Cup Delivery (R series) ==========

    /**
     * Send cup delivery command (0x24, R series).
     * @param waitTimeSeconds Wait time after door opens, in seconds (1-255)
     */
    suspend fun cupDelivery(waitTimeSeconds: Int) {
        ensureConnected()
        if (enableLogging) _logger.info("Control", "Cup delivery with wait time ${waitTimeSeconds}s...")

        val command = GS805Protocol.cupDeliveryCommand(waitTimeSeconds)
        try {
            if (enableCommandQueue && _commandQueue != null) {
                _commandQueue!!.enqueue(command).await()
            } else {
                _manager.sendCommand(command)
            }
            if (enableLogging) _logger.info("Control", "Cup delivery command sent successfully")
        } catch (e: Exception) {
            if (enableLogging) _logger.error("Control", "Failed to send cup delivery command", e)
            throw e
        }
    }

    // ========== Raw Command ==========

    /**
     * Send a raw CommandMessage and return the response.
     * Use for commands not yet wrapped in dedicated methods.
     */
    suspend fun sendRawCommand(command: CommandMessage): ResponseMessage {
        ensureConnected()
        return _manager.sendCommand(command)
    }

    // ========== Event Flows ==========

    val messageFlow: SharedFlow<ResponseMessage> get() = _manager.messageFlow

    val eventFlow: Flow<MachineEvent>
        get() = _manager.messageFlow
            .filter { it.isActiveReport }
            .map { MachineEvent.fromCode(it.getDataByte(0) ?: 0) }

    val connectionStateFlow: StateFlow<Boolean> get() = _manager.connectionStateFlow

    val reconnectEventFlow: SharedFlow<ReconnectEvent> get() = _manager.reconnectEventFlow

    // ========== Utility ==========

    private fun ensureConnected() {
        if (!isConnected) throw NotConnectedException("Not connected to device")
    }

    val bufferSize: Int get() = _manager.bufferSize

    fun clearBuffer() { _manager.clearBuffer() }

    suspend fun reconnect() { _manager.reconnect() }

    // ========== Logging & Queue ==========

    val logger: GS805Logger get() = _logger

    val logStream: Flow<LogEntry> get() = _logger.stream

    val commandQueue: CommandQueue? get() = _commandQueue

    val queueEventFlow: Flow<QueueEvent>
        get() = _commandQueue?.eventFlow ?: emptyFlow()

    fun setLogLevel(level: LogLevel) { _logger.setLevel(level) }

    fun clearLogs() { _logger.clearHistory() }

    fun exportLogs(
        minLevel: LogLevel? = null,
        source: String? = null,
        since: java.util.Date? = null
    ): String = _logger.exportLogs(minLevel, source, since)

    fun pauseQueue() { _commandQueue?.pause() }

    fun resumeQueue() { _commandQueue?.resume() }

    fun clearQueue() { _commandQueue?.clear() }

    fun getPendingCommands(): List<QueuedCommand> =
        _commandQueue?.getPendingCommands() ?: emptyList()

    suspend fun dispose() {
        if (enableLogging) _logger.info("GS805Serial", "Disposing resources...")
        _commandQueue?.dispose()
        _manager.dispose()
        if (enableLogging) _logger.info("GS805Serial", "Resources disposed")
    }
}

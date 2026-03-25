package kr.co.anyeats.gs805serial.model

/** Unit test command types for 0x1A */
enum class UnitTestCommand(val code: Int, val description: String) {
    DISPENSING(0x01, "Dispensing test"),
    COORDINATE(0x02, "Coordinate test"),
    FRONT_DOOR(0x03, "Front door test"),
    ICE_MODULE(0x04, "Ice module test"),
    IO_CONTROL(0x05, "IO control test"),
    COMBINED(0x06, "Combined test");

    companion object {
        fun fromCode(code: Int): UnitTestCommand? = entries.firstOrNull { it.code == code }
    }
}

/** Electronic lock operation */
enum class LockOperation(val code: Int, val description: String) {
    UNLOCK(0x00, "Unlock"),
    LOCK(0x01, "Lock"),
    QUERY(0x02, "Query status");

    companion object {
        fun fromCode(code: Int): LockOperation? = entries.firstOrNull { it.code == code }
    }
}

/** Electronic lock execution result */
enum class LockExecutionResult(val code: Int, val description: String) {
    NOT_EXECUTED(0x00, "Not executed"),
    IN_PROGRESS(0x01, "In progress"),
    SUCCESS(0x02, "Success"),
    FAILED(0x03, "Failed");

    companion object {
        fun fromCode(code: Int): LockExecutionResult = entries.firstOrNull { it.code == code } ?: NOT_EXECUTED
    }
}

/** Electronic lock status parsed from 0x1B LOCK_S byte */
data class LockStatus(
    val executionResult: LockExecutionResult,
    val isLocked: Boolean
) {
    override fun toString(): String = "Lock ${if (isLocked) "locked" else "unlocked"} ($executionResult)"

    companion object {
        fun fromByte(lockS: Int): LockStatus {
            val execResult = (lockS shr 6) and 0x03
            val lockState = lockS and 0x01
            return LockStatus(
                executionResult = LockExecutionResult.fromCode(execResult),
                isLocked = lockState == 1
            )
        }
    }
}

/** Overall machine status from controller status bits 0-1 */
enum class OverallStatus(val code: Int, val description: String) {
    NORMAL_IDLE(0, "Normal idle"),
    NORMAL_BUSY(1, "Normal busy"),
    EXCEPTION(2, "Exception"),
    FAILURE(3, "Failure");

    companion object {
        fun fromCode(code: Int): OverallStatus = entries.firstOrNull { it.code == code } ?: NORMAL_IDLE
    }
}

/** Main controller status parsed from 0x1E ST_INFO 32-bit field */
data class ControllerStatus(
    val rawValue: Int,
    val overallStatus: OverallStatus,
    val frontDoorModuleOffline: Boolean,
    val iceModuleOffline: Boolean,
    val grindingModuleOffline: Boolean,
    val frontDoorModuleFault: Boolean,
    val iceModuleFault: Boolean,
    val grindingModuleFault: Boolean,
    val noCup: Boolean,
    val noLid: Boolean,
    val waterTankLow: Boolean,
    val wasteTankWarning: Boolean,
    val firstHeatingOnPowerOn: Boolean,
    val cupPresentOnHolder: Boolean,
    val beanHopperEmpty: Boolean,
    val mainControllerSensorAbnormal: Boolean,
    val waterTank1NoWater: Boolean,
    val waterTank2NoWater: Boolean,
    val waterTank3NoWater: Boolean,
    val waterTank4NoWater: Boolean,
    val pump1Fault: Boolean,
    val pump2Fault: Boolean,
    val pump3Fault: Boolean,
    val pump4Fault: Boolean,
    val boosterPumpFault: Boolean,
    val hotTankFault: Boolean,
    val coldTankFault: Boolean,
    val drainSolenoidFault: Boolean,
    val cupDropper1Fault: Boolean,
    val cupDropper2Fault: Boolean,
    val trackError: Boolean,
    val electronicLockFault: Boolean,
    val drinkNumber: Int
) {
    val hasErrors: Boolean
        get() = overallStatus == OverallStatus.EXCEPTION || overallStatus == OverallStatus.FAILURE

    val activeAlerts: List<String>
        get() {
            val alerts = mutableListOf<String>()
            if (frontDoorModuleOffline) alerts.add("Front door module offline")
            if (iceModuleOffline) alerts.add("Ice module offline")
            if (grindingModuleOffline) alerts.add("Grinding module offline")
            if (frontDoorModuleFault) alerts.add("Front door module fault")
            if (iceModuleFault) alerts.add("Ice module fault")
            if (grindingModuleFault) alerts.add("Grinding module fault")
            if (noCup) alerts.add("No cup (E02)")
            if (noLid) alerts.add("No lid (A01)")
            if (waterTankLow) alerts.add("Water tank low (E01)")
            if (wasteTankWarning) alerts.add("Waste tank warning")
            if (firstHeatingOnPowerOn) alerts.add("First heating on power on")
            if (cupPresentOnHolder) alerts.add("Cup present on holder (A09)")
            if (beanHopperEmpty) alerts.add("Bean hopper empty (A165)")
            if (mainControllerSensorAbnormal) alerts.add("Main controller sensor abnormal (E04)")
            if (waterTank1NoWater) alerts.add("Water tank 1 no water (A10)")
            if (waterTank2NoWater) alerts.add("Water tank 2 no water (A11)")
            if (waterTank3NoWater) alerts.add("Water tank 3 no water (A12)")
            if (waterTank4NoWater) alerts.add("Water tank 4 no water (A13)")
            if (pump1Fault) alerts.add("Pump 1 fault (A14)")
            if (pump2Fault) alerts.add("Pump 2 fault (A15)")
            if (pump3Fault) alerts.add("Pump 3 fault (A16)")
            if (pump4Fault) alerts.add("Pump 4 fault (A17)")
            if (boosterPumpFault) alerts.add("Booster pump fault (E19)")
            if (hotTankFault) alerts.add("Hot tank fault (A05)")
            if (coldTankFault) alerts.add("Cold tank fault (A06)")
            if (drainSolenoidFault) alerts.add("Drain solenoid fault (E20)")
            if (cupDropper1Fault) alerts.add("Cup dropper 1 fault (E22)")
            if (cupDropper2Fault) alerts.add("Cup dropper 2 fault (E23)")
            if (trackError) alerts.add("Track error (E12)")
            if (electronicLockFault) alerts.add("Electronic lock fault (A116)")
            return alerts
        }

    override fun toString(): String {
        val parts = mutableListOf("Status: ${overallStatus.description}")
        if (drinkNumber > 0) parts.add("Drink: 0x${drinkNumber.toString(16)}")
        val alerts = activeAlerts
        if (alerts.isNotEmpty()) parts.add("Alerts: ${alerts.joinToString(", ")}")
        return parts.joinToString(" | ")
    }

    companion object {
        fun fromRawData(stInfo: Int, drinkNo: Int): ControllerStatus {
            return ControllerStatus(
                rawValue = stInfo,
                overallStatus = OverallStatus.fromCode(stInfo and 0x03),
                frontDoorModuleOffline = (stInfo and (1 shl 2)) != 0,
                iceModuleOffline = (stInfo and (1 shl 3)) != 0,
                grindingModuleOffline = (stInfo and (1 shl 4)) != 0,
                frontDoorModuleFault = (stInfo and (1 shl 5)) != 0,
                iceModuleFault = (stInfo and (1 shl 6)) != 0,
                grindingModuleFault = (stInfo and (1 shl 7)) != 0,
                noCup = (stInfo and (1 shl 8)) != 0,
                noLid = (stInfo and (1 shl 9)) != 0,
                waterTankLow = (stInfo and (1 shl 10)) != 0,
                wasteTankWarning = (stInfo and (1 shl 11)) != 0,
                firstHeatingOnPowerOn = (stInfo and (1 shl 12)) != 0,
                cupPresentOnHolder = (stInfo and (1 shl 13)) != 0,
                beanHopperEmpty = (stInfo and (1 shl 14)) != 0,
                mainControllerSensorAbnormal = (stInfo and (1 shl 15)) != 0,
                waterTank1NoWater = (stInfo and (1 shl 16)) != 0,
                waterTank2NoWater = (stInfo and (1 shl 17)) != 0,
                waterTank3NoWater = (stInfo and (1 shl 18)) != 0,
                waterTank4NoWater = (stInfo and (1 shl 19)) != 0,
                pump1Fault = (stInfo and (1 shl 20)) != 0,
                pump2Fault = (stInfo and (1 shl 21)) != 0,
                pump3Fault = (stInfo and (1 shl 22)) != 0,
                pump4Fault = (stInfo and (1 shl 23)) != 0,
                boosterPumpFault = (stInfo and (1 shl 24)) != 0,
                hotTankFault = (stInfo and (1 shl 25)) != 0,
                coldTankFault = (stInfo and (1 shl 26)) != 0,
                drainSolenoidFault = (stInfo and (1 shl 27)) != 0,
                cupDropper1Fault = (stInfo and (1 shl 28)) != 0,
                cupDropper2Fault = (stInfo and (1 shl 29)) != 0,
                trackError = (stInfo and (1 shl 30)) != 0,
                electronicLockFault = (stInfo and (1 shl 31)) != 0,
                drinkNumber = drinkNo
            )
        }
    }
}

/** Drink execution result from DK_INFO bits 30-31 */
enum class DrinkExecutionResult(val code: Int, val description: String) {
    NOT_EXECUTED(0, "Not executed"),
    EXECUTING(1, "Executing"),
    SUCCESS(2, "Success"),
    FAILURE(3, "Failure");

    companion object {
        fun fromCode(code: Int): DrinkExecutionResult = entries.firstOrNull { it.code == code } ?: NOT_EXECUTED
    }
}

/** Drink preparation status parsed from 0x1F DK_INFO 32-bit field */
data class DrinkPreparationStatus(
    val rawValue: Int,
    val drinkNumber: Int,
    val drinkMakingType: Int,
    val cupPlaced: Boolean,
    val waitingForCupRetrieval: Boolean,
    val synchronizationStatus: Boolean,
    val processType: Int,
    val currentStep: Int,
    val totalSteps: Int,
    val failureCauseCode: Int,
    val executionResult: DrinkExecutionResult
) {
    val isLocalBalance: Boolean get() = drinkMakingType == 0
    val isProtocolCommand: Boolean get() = drinkMakingType == 1
    val isNormalProcess: Boolean get() = processType == 0
    val isAbnormalProcess: Boolean get() = processType == 1
    val hasFailure: Boolean get() = failureCauseCode > 0
    val failureCode: String? get() = if (failureCauseCode > 0) "EM${failureCauseCode.toString().padStart(2, '0')}" else null
    val progressPercent: Int get() = if (totalSteps > 0) (currentStep * 100) / totalSteps else 0

    override fun toString(): String {
        val parts = mutableListOf<String>()
        parts.add("Drink: 0x${drinkNumber.toString(16)}")
        parts.add("Result: ${executionResult.description}")
        parts.add("Progress: $currentStep/$totalSteps")
        if (cupPlaced) parts.add("Cup placed")
        if (waitingForCupRetrieval) parts.add("Waiting for cup retrieval")
        failureCode?.let { parts.add("Failure: $it") }
        return parts.joinToString(" | ")
    }

    companion object {
        fun fromRawData(dkInfo: Int): DrinkPreparationStatus {
            return DrinkPreparationStatus(
                rawValue = dkInfo,
                drinkNumber = dkInfo and 0x1F,
                drinkMakingType = (dkInfo shr 5) and 0x01,
                cupPlaced = (dkInfo and (1 shl 6)) != 0,
                waitingForCupRetrieval = (dkInfo and (1 shl 7)) != 0,
                synchronizationStatus = (dkInfo and (1 shl 8)) != 0,
                processType = (dkInfo shr 9) and 0x01,
                currentStep = (dkInfo shr 10) and 0x7F,
                totalSteps = (dkInfo shr 17) and 0x7F,
                failureCauseCode = (dkInfo shr 24) and 0x3F,
                executionResult = DrinkExecutionResult.fromCode((dkInfo shr 30) and 0x03)
            )
        }
    }
}

/** Object type for exception info query (0x22) */
enum class ObjectType(val code: Int, val description: String) {
    WATER_PUMP(0x0000, "Water pump"),
    WATER_TANK(0x0001, "Water tank"),
    FLOW_METER_BOOSTER_PUMP(0x0002, "Flow meter / booster pump"),
    SOLENOID_VALVE(0x0003, "Solenoid valve"),
    WASTE_BUCKET(0x0004, "Waste bucket"),
    HOT_TANK(0x0005, "Hot tank"),
    COLD_TANK(0x0006, "Cold tank"),
    ICE_MODULE(0x0007, "Ice module"),
    TRACK_CUP_HOLDER(0x0008, "Track / cup holder"),
    FRONT_DOOR_MODULE(0x0009, "Front door module"),
    GRINDING_MODULE(0x000A, "Grinding module"),
    BOARD_OTHER(0x000B, "Board other functions");

    companion object {
        fun fromCode(code: Int): ObjectType? = entries.firstOrNull { it.code == code }
    }
}

/** Object exception info parsed from 0x22 response */
data class ObjectExceptionInfo(
    val statusCode: Int,
    val stInfo: Int,
    val opInfo: Int,
    val objectNumber: Int,
    val infoCode: Int,
    val autInfo: Int
) {
    val objectType: ObjectType? get() = ObjectType.fromCode(objectNumber)

    override fun toString(): String {
        val objName = objectType?.description ?: "Unknown(0x${objectNumber.toString(16)})"
        return "Object: $objName, InfoCode: 0x${infoCode.toString(16)}, Status: 0x${stInfo.toString(16)}"
    }
}

/** Force stop target */
enum class ForceStopTarget(val code: Int, val description: String) {
    DRINK_PROCESS(0x01, "Force stop drink process"),
    CUP_DELIVERY(0x24, "Force stop cup delivery");

    companion object {
        fun fromCode(code: Int): ForceStopTarget? = entries.firstOrNull { it.code == code }
    }
}

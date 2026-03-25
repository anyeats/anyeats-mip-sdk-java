package kr.co.anyeats.gs805serial.model

/** Water type for drink operations */
enum class WaterType(val code: Int) {
    HOT(0x00),
    COLD(0x01);
}

/** Recipe step operation types (OPT-N values for 0x1D command) */
enum class RecipeOperationType(val code: Int) {
    NONE(0x00),
    INSTANT_CHANNEL(0x01),
    GRINDING(0x02),
    CUP_DISPENSE(0x03),
    ICE_MAKING(0x04),
    LID_PLACEMENT(0x05),
    LID_PRESSING(0x06),
    INDEPENDENT_MIXING(0x07);
}

/**
 * A single step in a drink recipe process.
 * Used with command 0x1D (setDrinkRecipeProcess).
 */
class RecipeStep private constructor(
    val operationType: RecipeOperationType,
    val parameters: ByteArray
) {
    /** Encode this step as bytes for the 0x1D command: [OPT, OP-DL, ...OPI] */
    fun toBytes(): ByteArray {
        val result = ByteArray(2 + parameters.size)
        result[0] = operationType.code.toByte()
        result[1] = parameters.size.toByte()
        parameters.copyInto(result, 2)
        return result
    }

    companion object {
        /**
         * Create an instant channel step (OPT=0x01).
         * This is the main step for dispensing powder + water.
         *
         * @param channel Channel number (0-based, 0 = 1st channel)
         * @param waterType Hot or cold water
         * @param materialDuration Powder dispensing time in 0.1s units (0-999)
         * @param waterAmount Water amount in 0.1mL units (0-999), must be >= materialDuration when using time unit
         * @param materialSpeed Powder dispensing speed 0-100%
         * @param mixSpeed Stirring speed 0-100%
         * @param subChannel Sub-material channel (-1 to 127, -1 = none)
         * @param subMaterialDuration Sub-material dispensing time in 0.1s units (0-999)
         * @param subMaterialSpeed Sub-material speed 0-100%
         * @param endWaitTime Wait time after step completes, in seconds (0-255)
         */
        fun instantChannel(
            channel: Int,
            waterType: WaterType = WaterType.HOT,
            materialDuration: Int = 0,
            waterAmount: Int = 0,
            materialSpeed: Int = 50,
            mixSpeed: Int = 0,
            subChannel: Int = -1,
            subMaterialDuration: Int = 0,
            subMaterialSpeed: Int = 0,
            endWaitTime: Int = 0
        ): RecipeStep {
            val params = byteArrayOf(
                (channel and 0xFF).toByte(),
                waterType.code.toByte(),
                ((materialDuration shr 8) and 0xFF).toByte(), (materialDuration and 0xFF).toByte(),
                ((waterAmount shr 8) and 0xFF).toByte(), (waterAmount and 0xFF).toByte(),
                (materialSpeed and 0xFF).toByte(),
                (mixSpeed and 0xFF).toByte(),
                (subChannel and 0xFF).toByte(),
                ((subMaterialDuration shr 8) and 0xFF).toByte(), (subMaterialDuration and 0xFF).toByte(),
                (subMaterialSpeed and 0xFF).toByte(),
                (endWaitTime and 0xFF).toByte()
            )
            return RecipeStep(RecipeOperationType.INSTANT_CHANNEL, params)
        }

        /**
         * Create a cup dispense step (OPT=0x03).
         * @param dispenser 0=manual wait, 1=#1 dispenser, 2=#2 dispenser
         */
        fun cupDispense(dispenser: Int = 1): RecipeStep {
            return RecipeStep(
                RecipeOperationType.CUP_DISPENSE,
                byteArrayOf((dispenser and 0xFF).toByte())
            )
        }

        /**
         * Create a grinding step (OPT=0x02, fresh ground coffee).
         * @param channel Grinder channel (0-based)
         * @param waterTemp Grinding water temperature 70-90 C
         * @param grindDuration Grind time in 0.1s units (20-200)
         * @param waterAmount Water amount in g/mL (20-200)
         * @param makeType 0=concurrent with instant, 1=sequential
         */
        fun grinding(
            channel: Int = 0,
            waterTemp: Int = 85,
            grindDuration: Int = 50,
            waterAmount: Int = 50,
            makeType: Int = 1
        ): RecipeStep {
            return RecipeStep(
                RecipeOperationType.GRINDING,
                byteArrayOf(
                    (channel and 0xFF).toByte(),
                    (waterTemp and 0xFF).toByte(),
                    (grindDuration and 0xFF).toByte(),
                    (waterAmount and 0xFF).toByte(),
                    (makeType and 0xFF).toByte()
                )
            )
        }

        /**
         * Create an ice making step (OPT=0x04).
         * @param channel Ice channel (0-based)
         * @param weight Ice weight in grams (0-200)
         */
        fun iceMaking(channel: Int = 0, weight: Int = 100): RecipeStep {
            return RecipeStep(
                RecipeOperationType.ICE_MAKING,
                byteArrayOf((channel and 0xFF).toByte(), (weight and 0xFF).toByte(), 0x00)
            )
        }

        /**
         * Create a lid placement step (OPT=0x05).
         * @param channel Lid dispenser channel (0-based)
         */
        fun lidPlacement(channel: Int = 0): RecipeStep {
            return RecipeStep(
                RecipeOperationType.LID_PLACEMENT,
                byteArrayOf((channel and 0xFF).toByte())
            )
        }

        /**
         * Create a lid pressing step (OPT=0x06).
         * @param channel Press channel (0-based)
         */
        fun lidPressing(channel: Int = 0): RecipeStep {
            return RecipeStep(
                RecipeOperationType.LID_PRESSING,
                byteArrayOf((channel and 0xFF).toByte())
            )
        }

        /**
         * Create an independent mixing step (OPT=0x07, GS801 only).
         * @param channel Mixer channel (0-based)
         * @param mixType 0=fixed, 1=fixed stir-stop, 2=centrifugal, 3=centrifugal stir-stop, 4=concentric
         * @param maxSpeed Maximum mixing speed 1-100%
         */
        fun independentMixing(channel: Int = 0, mixType: Int = 0, maxSpeed: Int = 50): RecipeStep {
            return RecipeStep(
                RecipeOperationType.INDEPENDENT_MIXING,
                byteArrayOf(
                    (channel and 0xFF).toByte(),
                    (mixType and 0xFF).toByte(),
                    (maxSpeed and 0xFF).toByte()
                )
            )
        }
    }
}

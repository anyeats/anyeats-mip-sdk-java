package kr.co.anyeats.gs805serial.serial

import kr.co.anyeats.gs805serial.protocol.GS805Protocol
import kr.co.anyeats.gs805serial.protocol.ResponseMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.ByteArrayOutputStream

/** Message parser that assembles complete messages from byte streams */
class MessageParser {

    private val buffer = ByteArrayOutputStream()
    private val _messageFlow = MutableSharedFlow<ResponseMessage>(extraBufferCapacity = 64)
    private var isActive = true

    /** Flow of parsed response messages */
    val messageFlow: Flow<ResponseMessage> = _messageFlow.asSharedFlow()

    /** Current buffer size */
    val bufferSize: Int get() = buffer.size()

    /** Add incoming bytes to the parser */
    fun addBytes(bytes: ByteArray) {
        if (!isActive) return
        synchronized(buffer) {
            buffer.write(bytes)
            extractMessages()
        }
    }

    private fun extractMessages() {
        while (true) {
            val bufferBytes = buffer.toByteArray()
            if (bufferBytes.size < 5) break

            val result = GS805Protocol.extractMessage(bufferBytes, isResponse = true)

            if (result.message != null) {
                val response = ResponseMessage.fromBytes(result.message)
                if (response != null) {
                    _messageFlow.tryEmit(response)
                }
                if (result.consumed > 0) {
                    removeFromBuffer(result.consumed)
                }
            } else if (result.consumed > 0) {
                removeFromBuffer(result.consumed)
            } else {
                break
            }
        }
    }

    private fun removeFromBuffer(count: Int) {
        val bufferBytes = buffer.toByteArray()
        buffer.reset()
        if (count < bufferBytes.size) {
            buffer.write(bufferBytes, count, bufferBytes.size - count)
        }
    }

    /** Clear the internal buffer */
    fun clearBuffer() {
        synchronized(buffer) {
            buffer.reset()
        }
    }

    /** Close the parser */
    fun close() {
        isActive = false
        synchronized(buffer) {
            buffer.reset()
        }
    }
}

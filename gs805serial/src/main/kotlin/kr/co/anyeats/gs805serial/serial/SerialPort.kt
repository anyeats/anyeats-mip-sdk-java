package kr.co.anyeats.gs805serial.serial

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * JNI wrapper for native UART serial port access.
 *
 * Opens hardware UART ports (e.g., /dev/ttyS*, /dev/ttyHS*) on embedded
 * Android devices using termios via JNI.
 */
class SerialPort(val devicePath: String) {

    @JvmField
    var fileDescriptor: FileDescriptor? = null
    var inputStream: FileInputStream? = null
        private set
    var outputStream: FileOutputStream? = null
        private set

    val isOpen: Boolean get() = fileDescriptor != null

    /**
     * Opens the serial port with the specified configuration.
     *
     * @param baudRate Baud rate (9600, 19200, 38400, 57600, 115200)
     * @param dataBits Number of data bits (5, 6, 7, or 8)
     * @param stopBits Number of stop bits (1 or 2)
     * @param parity Parity mode (0=None, 1=Odd, 2=Even)
     * @throws IOException if the port cannot be opened or configured
     */
    @Throws(IOException::class)
    fun open(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int) {
        if (isOpen) {
            throw IOException("Serial port is already open: $devicePath")
        }

        val fd = nativeOpen(devicePath, baudRate, dataBits, stopBits, parity)
        fileDescriptor = fd
        inputStream = FileInputStream(fd)
        outputStream = FileOutputStream(fd)
    }

    /**
     * Closes the serial port and releases all resources.
     */
    fun close() {
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            nativeClose()
        } catch (_: Exception) {}

        inputStream = null
        outputStream = null
        fileDescriptor = null
    }

    @Throws(IOException::class)
    private external fun nativeOpen(
        path: String,
        baudRate: Int,
        dataBits: Int,
        stopBits: Int,
        parity: Int
    ): FileDescriptor

    private external fun nativeClose()

    companion object {
        init {
            System.loadLibrary("serial_port")
        }
    }
}

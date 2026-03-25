package kr.co.anyeats.gs805serial.util

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

/** Log level enumeration */
enum class LogLevel(val level: Int, val tag: String) {
    DEBUG(0, "DEBUG"),
    INFO(1, "INFO"),
    WARNING(2, "WARN"),
    ERROR(3, "ERROR"),
    NONE(99, "NONE")
}

/** Log entry */
data class LogEntry(
    val timestamp: Date,
    val level: LogLevel,
    val source: String,
    val message: String,
    val error: Throwable? = null
) {
    override fun toString(): String {
        val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val parts = mutableListOf("[${timeFormat.format(timestamp)}] [${level.tag}] [$source] $message")
        error?.let { parts.add("Error: $it") }
        return parts.joinToString("\n")
    }
}

/** GS805 Serial Logger (Singleton) */
object GS805Logger {

    private var _level = LogLevel.INFO
    private var _maxHistorySize = 100
    private val _history = ConcurrentLinkedDeque<LogEntry>()
    private val _flow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 64)

    /** Whether to print logs to Android logcat */
    var printToLogcat = true

    val level: LogLevel get() = _level
    val history: List<LogEntry> get() = _history.toList()
    val stream: Flow<LogEntry> = _flow.asSharedFlow()

    fun setLevel(level: LogLevel) { _level = level }

    fun setMaxHistorySize(size: Int) {
        _maxHistorySize = size
        trimHistory()
    }

    fun debug(source: String, message: String) = log(LogLevel.DEBUG, source, message)

    fun info(source: String, message: String) = log(LogLevel.INFO, source, message)

    fun warning(source: String, message: String, error: Throwable? = null) =
        log(LogLevel.WARNING, source, message, error)

    fun error(source: String, message: String, error: Throwable? = null) =
        log(LogLevel.ERROR, source, message, error)

    private fun log(level: LogLevel, source: String, message: String, error: Throwable? = null) {
        if (level.level < _level.level) return

        val entry = LogEntry(
            timestamp = Date(),
            level = level,
            source = source,
            message = message,
            error = error
        )

        _history.add(entry)
        trimHistory()

        if (printToLogcat) {
            val tag = "GS805-$source"
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, message, error)
                LogLevel.INFO -> Log.i(tag, message, error)
                LogLevel.WARNING -> Log.w(tag, message, error)
                LogLevel.ERROR -> Log.e(tag, message, error)
                LogLevel.NONE -> {}
            }
        }

        _flow.tryEmit(entry)
    }

    private fun trimHistory() {
        while (_history.size > _maxHistorySize) {
            _history.pollFirst()
        }
    }

    fun clearHistory() { _history.clear() }

    fun getByLevel(level: LogLevel): List<LogEntry> = _history.filter { it.level == level }

    fun getBySource(source: String): List<LogEntry> = _history.filter { it.source == source }

    fun getInRange(start: Date, end: Date): List<LogEntry> =
        _history.filter { it.timestamp.after(start) && it.timestamp.before(end) }

    fun exportLogs(minLevel: LogLevel? = null, source: String? = null, since: Date? = null): String {
        var logs = _history.toList()
        minLevel?.let { ml -> logs = logs.filter { it.level.level >= ml.level } }
        source?.let { s -> logs = logs.filter { it.source == s } }
        since?.let { s -> logs = logs.filter { it.timestamp.after(s) } }
        return logs.joinToString("\n\n") { it.toString() }
    }
}
